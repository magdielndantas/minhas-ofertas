import re
import json
import os
import sys
import logging
import argparse
import sqlite3
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler
from pathlib import Path
from logging.handlers import RotatingFileHandler
from datetime import datetime
from dotenv import load_dotenv
load_dotenv()

# Adiciona src ao path para imports
sys.path.insert(0, str(Path(__file__).parent))

from telethon import TelegramClient, events
from telethon.errors import FloodWaitError, SessionPasswordNeededError
import asyncio
import sched
import time as time_module

try:
    from tqdm import tqdm
    PROGRESSBAR = True
except ImportError:
    PROGRESSBAR = False

try:
    from colorama import init, Fore, Style
    init(autoreset=True)
    CORES = True
except ImportError:
    class Fore:
        GREEN = RED = YELLOW = CYAN = MAGENTA = ''
    class Style:
        BRIGHT = RESET_ALL = ''
    CORES = False

# Imports dos módulos locais
from database import init_db, save_oferta, get_ofertas, get_estatisticas
from config import carregar_config, salvar_config, CONFIG_DEFAULT
from price_parser import extrair_preco, texto_contem_interesse, texto_contem_cupom, extrair_codigo_cupom, extrair_desconto, extrair_nome_produto
from olx_monitor import run_olx_monitor

# ==================== TRIGGER SERVER ====================
_event_loop = None
_history_running = False
_history_last_run = None
_history_result = None

_auth_state = {
    'status': 'unauthenticated',  # unauthenticated | code_sent | authenticated
    'phone': None,
    'phone_code_hash': None,
    'error': None
}
_auth_client = None

class TriggerHandler(BaseHTTPRequestHandler):
    def _json_response(self, code, data):
        body = json.dumps(data).encode()
        self.send_response(code)
        self.send_header('Content-Type', 'application/json')
        self.end_headers()
        self.wfile.write(body)

    def _read_body(self):
        length = int(self.headers.get('Content-Length', 0))
        return json.loads(self.rfile.read(length)) if length else {}

    def do_GET(self):
        if self.path == '/status':
            self._json_response(200, {
                'running': _history_running,
                'last_run': _history_last_run,
                'result': _history_result
            })
        elif self.path == '/auth/status':
            self._json_response(200, {
                'status': _auth_state['status'],
                'error': _auth_state['error']
            })
        elif self.path == '/olx/status':
            self._json_response(200, {
                'last_run': _olx_last_run,
                'scheduler': _olx_scheduler is not None
            })
        else:
            self.send_response(404)
            self.end_headers()

    def do_POST(self):
        global _history_running, _auth_state
        if self.path == '/buscar-historico':
            if _history_running:
                self._json_response(409, {'status': 'running'})
                return
            if _event_loop:
                asyncio.run_coroutine_threadsafe(_trigger_history(), _event_loop)
                self._json_response(202, {'status': 'started'})
            else:
                self._json_response(503, {'status': 'error', 'message': 'Loop não iniciado'})
        elif self.path == '/auth/phone':
            data = self._read_body()
            phone = data.get('phone', '').strip()
            if not phone:
                self._json_response(400, {'error': 'Telefone obrigatório'})
                return
            if _event_loop and _auth_client:
                future = asyncio.run_coroutine_threadsafe(_send_code(phone), _event_loop)
                try:
                    future.result(timeout=15)
                    self._json_response(200, {'status': 'code_sent'})
                except Exception as e:
                    self._json_response(500, {'error': str(e)})
            else:
                self._json_response(503, {'error': 'Cliente não iniciado'})
        elif self.path == '/auth/code':
            data = self._read_body()
            code = data.get('code', '').strip()
            password = data.get('password', '').strip()
            if not code:
                self._json_response(400, {'error': 'Código obrigatório'})
                return
            if _event_loop and _auth_client:
                future = asyncio.run_coroutine_threadsafe(_verify_code(code, password), _event_loop)
                try:
                    result = future.result(timeout=15)
                    self._json_response(200, result)
                except Exception as e:
                    self._json_response(500, {'error': str(e)})
            else:
                self._json_response(503, {'error': 'Cliente não iniciado'})
        elif self.path == '/olx/run':
            if _event_loop and _trigger_client:
                config = carregar_config()
                try:
                    run_olx_scheduled(_trigger_client, config)
                    self._json_response(200, {'status': 'completed', 'last_run': _olx_last_run})
                except Exception as e:
                    self._json_response(500, {'error': str(e)})
            else:
                self._json_response(503, {'error': 'Cliente não iniciado'})
        else:
            self.send_response(404)
            self.end_headers()

    def log_message(self, format, *args):
        pass


async def _send_code(phone):
    global _auth_state
    try:
        sent = await _auth_client.send_code_request(phone)
        _auth_state['phone'] = phone
        _auth_state['phone_code_hash'] = sent.phone_code_hash
        _auth_state['status'] = 'code_sent'
        _auth_state['error'] = None
    except Exception as e:
        _auth_state['error'] = str(e)
        raise


async def _verify_code(code, password=''):
    global _auth_state
    try:
        await _auth_client.sign_in(
            _auth_state['phone'],
            code,
            phone_code_hash=_auth_state['phone_code_hash']
        )
        _auth_state['status'] = 'authenticated'
        _auth_state['error'] = None
        return {'status': 'authenticated'}
    except SessionPasswordNeededError:
        if password:
            await _auth_client.sign_in(password=password)
            _auth_state['status'] = 'authenticated'
            _auth_state['error'] = None
            return {'status': 'authenticated'}
        return {'status': '2fa_required'}
    except Exception as e:
        _auth_state['error'] = str(e)
        raise

def start_trigger_server():
    server = HTTPServer(('0.0.0.0', 8000), TriggerHandler)
    server.serve_forever()

# Cores para Windows
if sys.platform == 'win32':
    def c_green(msg): return f"[OK] {msg}"
    def c_red(msg): return f"[ERR] {msg}"
    def c_yellow(msg): return f"[WARN] {msg}"
    def c_cyan(msg): return f"[INFO] {msg}"
    def c_magenta(msg): return f"[STEP] {msg}"
    def c_bright(msg): return msg
else:
    def c_green(msg): return f"{Fore.GREEN}{msg}{Style.RESET_ALL}" if CORES else msg
    def c_red(msg): return f"{Fore.RED}{msg}{Style.RESET_ALL}" if CORES else msg
    def c_yellow(msg): return f"{Fore.YELLOW}{msg}{Style.RESET_ALL}" if CORES else msg
    def c_cyan(msg): return f"{Fore.CYAN}{msg}{Style.RESET_ALL}" if CORES else msg
    def c_magenta(msg): return f"{Fore.MAGENTA}{msg}{Style.RESET_ALL}" if CORES else msg
    def c_bright(msg): return f"{Style.BRIGHT}{msg}{Style.RESET_ALL}" if CORES else msg

# ==================== LOGS ====================
def setup_logging(verbose=False):
    log_dir = Path(__file__).parent.parent / 'logs'
    log_dir.mkdir(exist_ok=True)
    
    logger = logging.getLogger('minhas_ofertas')
    logger.setLevel(logging.DEBUG if verbose else logging.INFO)
    
    formatter = logging.Formatter(
        '%(asctime)s | %(levelname)-8s | %(message)s',
        datefmt='%Y-%m-%d %H:%M:%S'
    )
    
    file_handler = RotatingFileHandler(
        log_dir / 'monitor.log', 
        maxBytes=5*1024*1024, 
        backupCount=3,
        encoding='utf-8'
    )
    file_handler.setLevel(logging.DEBUG)
    file_handler.setFormatter(formatter)
    
    console_handler = logging.StreamHandler(sys.stdout)
    console_handler.setLevel(logging.DEBUG if verbose else logging.INFO)
    console_handler.setFormatter(formatter)
    
    logger.addHandler(file_handler)
    logger.addHandler(console_handler)
    
    return logger

logger = None

# ==================== CREDENCIAIS ====================
def get_env_credentials():
    api_id = os.environ.get('TELEGRAM_API_ID')
    api_hash = os.environ.get('TELEGRAM_API_HASH')
    
    if not api_id or not api_hash:
        print(c_red("[ERR] Credenciais nao encontradas no .env"))
        print(c_yellow("[*] Crie um arquivo .env com:"))
        print("   TELEGRAM_API_ID=seu_api_id")
        print("   TELEGRAM_API_HASH=seu_api_hash")
        sys.exit(1)
    
    try:
        api_id = int(api_id)
    except ValueError:
        print(c_red("[ERR] TELEGRAM_API_ID deve ser um numero inteiro"))
        sys.exit(1)
    
    return api_id, api_hash

# ==================== RATE LIMITER ====================
class RateLimiter:
    def __init__(self, max_per_minute=20, min_delay=2.0):
        self.max_per_minute = max_per_minute
        self.min_delay = min_delay
        self.last_send_time = {}
        self.message_count = {}
        self.lock = asyncio.Lock()
    
    async def wait_if_needed(self, channel_id):
        async with self.lock:
            now = datetime.now()
            minute_key = now.strftime('%Y%m%d%H%M')
            
            if minute_key not in self.message_count:
                self.message_count = {minute_key: 0}
            
            if self.message_count.get(minute_key, 0) >= self.max_per_minute:
                wait_time = 60 - now.second
                logger.warning(f"Aguardando {wait_time}s (limite atingido)...")
                await asyncio.sleep(wait_time)
            
            last_time = self.last_send_time.get(channel_id)
            if last_time:
                elapsed = (now - last_time).total_seconds()
                if elapsed < self.min_delay:
                    await asyncio.sleep(self.min_delay - elapsed)
            
            self.last_send_time[channel_id] = now
            self.message_count[minute_key] = self.message_count.get(minute_key, 0) + 1

# ==================== DOWNLOAD IMAGENS ====================
async def baixar_midia(message, pasta):
    if message.photo:
        try:
            Path(pasta).mkdir(exist_ok=True)
            filename = f"{pasta}/msg_{message.id}_{datetime.now().strftime('%Y%m%d_%H%M%S_%f')}.jpg"
            await message.download_media(file=filename)
            return filename
        except Exception as e:
            logger.warning(f"Erro ao baixar imagem: {e}")
            return None
    return None

async def enviar_notificacao(client, mensagem, caminho_imagem, canal_id, rate_limiter, produto=None, preco=None, desconto=None):
    await rate_limiter.wait_if_needed(canal_id or 'default')
    
    linhas = [mensagem]
    
    if produto and produto != 'N/A':
        linhas.append(f"📦 {produto[:50]}")
    
    if desconto:
        linhas.append(f"🔥 {desconto}% OFF")
    
    msg_final = "\n".join(linhas)
    
    try:
        if caminho_imagem:
            await client.send_message('me', msg_final, file=caminho_imagem)
        else:
            await client.send_message('me', msg_final)
        logger.info("Notificacao enviada")
    except FloodWaitError as e:
        logger.warning(f"Flood detected. Aguardando {e.seconds}s...")
        await asyncio.sleep(e.seconds)
        if caminho_imagem:
            await client.send_message('me', msg_final, file=caminho_imagem)
        else:
            await client.send_message('me', msg_final)
    except Exception as e:
        logger.error(f"Erro ao enviar: {e}")

async def enviar_notificacao_olx(client, oferta, rate_limiter):
    await rate_limiter.wait_if_needed('olx')
    
    title = oferta.get('produto', 'Sem título')
    price = oferta.get('preco')
    url = oferta.get('link', '')
    price_text = f"R$ {price:.2f}" if price else "Preço não informado"
    
    linhas = [
        f"[ALERT] OFERTA OLX",
        f"📦 {title[:50]}",
        f"💰 {price_text}",
        f"🔗 {url}"
    ]
    msg = "\n".join(linhas)
    
    try:
        await client.send_message('me', msg)
        logger.info(f"OLX: Notificação enviada para {title[:30]}")
    except FloodWaitError as e:
        logger.warning(f"Flood OLX. Aguardando {e.seconds}s...")
        await asyncio.sleep(e.seconds)
        await client.send_message('me', msg)
    except Exception as e:
        logger.error(f"Erro OLX ao notificar: {e}")

# ==================== OLX SCHEDULER ====================
_olx_scheduler = None
_olx_last_run = None

def run_olx_scheduled(client, config):
    global _olx_last_run
    
    logger.info("[OLX] Iniciando verificação scheduled...")
    print(c_cyan("[OLX] Verificando novas ofertas..."))
    
    offers = run_olx_monitor(config, save_oferta)
    
    if offers:
        rate_limiter = RateLimiter(
            config.get('rate_limit', {}).get('max_por_minuto', 20),
            config.get('rate_limit', {}).get('delay_segundos', 2)
        )
        
        async def notify_offers():
            for offer in offers:
                await enviar_notificacao_olx(client, offer, rate_limiter)
        
        try:
            asyncio.run(notify_offers())
        except Exception as e:
            logger.error(f"Erro ao enviar notificações OLX: {e}")
        
        print(c_green(f"[OLX] {len(offers)} novas ofertas encontradas e notificadas"))
    else:
        print(c_yellow("[OLX] Nenhuma nova oferta encontrada"))
    
    _olx_last_run = datetime.now().strftime('%d/%m/%Y %H:%M')
    logger.info(f"[OLX] Última execução: {_olx_last_run}")

def start_olx_scheduler(client, config):
    def schedule_check():
        if _event_loop and _trigger_client:
            try:
                run_olx_scheduled(_trigger_client, config)
            except Exception as e:
                logger.error(f"Erro no scheduler OLX: {e}")
        
        schedule_next()

    def schedule_next():
        now = datetime.now()
        target_hours = [9, 21]
        next_hour = None
        
        for h in target_hours:
            if now.hour < h:
                next_hour = h
                break
        
        if next_hour is None:
            next_hour = target_hours[0]
        
        next_run = now.replace(hour=next_hour, minute=0, second=0, microsecond=0)
        if now >= next_run:
            next_run = next_run.replace(day=now.day + 1)
        
        delay = (next_run - now).total_seconds()
        logger.info(f"[OLX] Próxima verificação em {delay/3600:.1f}h")
        _olx_scheduler.enter(delay, 1, schedule_check)
    
    _olx_scheduler = sched.scheduler(time_module.time, time_module.sleep)
    _olx_scheduler.enter(60, 1, schedule_check)
    
    scheduler_thread = threading.Thread(target=_olx_scheduler.run, daemon=True)
    scheduler_thread.start()
    logger.info("[OLX] Scheduler iniciado (manhã 9h, noite 21h)")

# ==================== COMANDOS ====================
def cmd_listar_canais(config):
    print(c_cyan("\n[*] Canais monitorados:"))
    for i, canal in enumerate(config['canais'], 1):
        print(f"  {i}. {c_bright(canal)}")
    print(c_cyan(f"\nTotal: {len(config['canais'])} canais"))
    print(c_cyan("\n[*] Palavras-chave:"))
    for i, palavra in enumerate(config['palavras_chave'], 1):
        print(f"  {i}. {palavra}")

def cmd_testar_regex(texto_teste):
    print(c_cyan(f"\n[*] Testando regex no texto:"))
    print(f'"{texto_teste}"')
    print()
    
    preco = extrair_preco(texto_teste)
    
    if preco:
        print(c_green(f"[OK] Preco encontrado: R$ {preco:.2f}"))
    else:
        print(c_red("[ERR] Nenhum preco encontrado"))
    
    precos = re.findall(r'R\$\s*[\d.,]+', texto_teste)
    if precos:
        print(c_yellow(f"[*] Matches encontrados: {precos}"))

async def _trigger_history():
    global _history_running, _history_last_run, _history_result
    _history_running = True
    try:
        config = carregar_config()
        await cmd_buscar_historico(_trigger_client, config)
        _history_result = 'ok'
    except Exception as e:
        _history_result = f'erro: {e}'
        if logger:
            logger.error(f'Erro no histórico via trigger: {e}')
    finally:
        _history_running = False
        _history_last_run = datetime.now().strftime('%d/%m/%Y %H:%M:%S')

_trigger_client = None

async def cmd_buscar_historico(client, config, dry_run=False, enviar_telegram=False):
    print(c_cyan("\n[*] Buscando historico..."))
    
    if dry_run:
        print(c_yellow("[-] Modo DRY-RUN: nenhuma notificacao sera enviada"))
    
    ofertas = []
    canais = config.get('canais', CONFIG_DEFAULT['canais'])
    
    if PROGRESSBAR:
        pbar = tqdm(canais, desc="Processando canais", unit="canal")
    else:
        pbar = canais
    
    pasta = config.get('imagens_pasta', 'data/imagens')
    
    for canal in pbar:
        try:
            if not PROGRESSBAR:
                print(f"\n[CH] {canal}")
            
            canal_entity = await client.get_entity(canal)
            canal_username = getattr(canal_entity, 'username', None)
            canal_id = canal_entity.id if hasattr(canal_entity, 'id') else None
            canal_nome = getattr(canal_entity, 'title', canal)
            
            if canal_username:
                base_link = f"https://t.me/{canal_username}/"
            elif canal_id:
                base_link = f"https://t.me/c/{abs(canal_id)}/"
            else:
                base_link = "#"
            
            async for message in client.iter_messages(canal_entity, limit=50):
                if message.message and texto_contem_interesse(message.message, config):
                    preco = extrair_preco(message.message)
                    
                    link = f"{base_link}{message.id}" if base_link != "#" else "#"
                    
                    caminho_imagem = None
                    if message.photo:
                        caminho_imagem = await baixar_midia(message, pasta)
                    
                    oferta = {
                        'canal': canal_nome,
                        'preco': preco,
                        'link': link,
                        'data': message.date.strftime('%d/%m/%Y %H:%M'),
                        'mensagem': message.message[:200],
                        'imagem': caminho_imagem
                    }
                    
                    ofertas.append(oferta)
                    
                    if not dry_run and enviar_telegram:
                        preco_info = f"R$ {preco:.2f}" if preco else ""
                        msg = f"[ALERT] OFERTA HISTORICA\n[CANAL] {canal_nome}\n[$] {preco_info}\n[LINK] {link}"
                        await client.send_message('me', msg)
                        await asyncio.sleep(1)
            
            if PROGRESSBAR:
                pbar.set_postfix_str(f"{len(ofertas)} ofertas")
                
        except Exception as e:
            logger.warning(f"Erro em {canal}: {e}")
    
    # Salvar no banco
    salvas = 0
    duplicadas = 0
    for oferta in ofertas:
        if save_oferta(oferta):
            salvas += 1
        else:
            duplicadas += 1
    
    print(c_green(f"[OK] Busca concluida! {salvas} ofertas salvas, {duplicadas} duplicadas"))
    print(c_green(f"[OK] Total no banco: {get_estatisticas()['total']} ofertas"))

# ==================== GERAR HTML ====================
def gerar_html(ofertas, output_path):
    html = """<!DOCTYPE html>
<html class="light" lang="pt-BR"><head>
<meta charset="utf-8"/>
<meta content="width=device-width, initial-scale=1.0" name="viewport"/>
<title>Minhas Ofertas</title>
<script src="https://cdn.tailwindcss.com?plugins=forms,container-queries"></script>
<link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@200;300;400;500;600;700;800&display=swap" rel="stylesheet"/>
<link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&display=swap" rel="stylesheet"/>
<script id="tailwind-config">
    tailwind.config = {
        darkMode: "class",
        theme: {
            extend: {
                "colors": {
                    "primary": "#af2700",
                    "surface": "#fff4f3",
                    "surface-container-low": "#ffedec",
                    "surface-container-lowest": "#ffffff",
                    "on-surface": "#4e2122",
                    "on-surface-variant": "#834c4c",
                    "tertiary-container": "#fdc003",
                    "on-tertiary-container": "#553e00"
                },
                "fontFamily": {
                    "headline": ["Plus Jakarta Sans"],
                    "body": ["Plus Jakarta Sans"],
                    "label": ["Plus Jakarta Sans"]
                }
            },
        },
    }
</script>
<style>
    body { font-family: 'Plus Jakarta Sans', sans-serif; }
    .material-symbols-outlined { font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24; }
</style>
</head>
<body class="bg-surface text-on-surface min-h-screen">
<nav class="fixed top-0 w-full z-50 bg-[#fff4f3]/80 backdrop-blur-xl h-20">
<div class="flex justify-between items-center px-8 h-full max-w-[1920px] mx-auto">
<div class="text-2xl font-black italic tracking-tighter text-[#af2700]">DEAL.EDIT</div>
</div>
</nav>
<main class="pt-32 pb-20 px-4 md:px-8 max-w-[1440px] mx-auto">
<section>
<h1 class="text-5xl font-black text-on-surface tracking-tighter mb-2">Minhas Ofertas</h1>
<p class="text-on-surface-variant max-w-lg">Total: """ + str(len(ofertas)) + """ ofertas</p>
</section>
<section class="mt-12">
<div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
"""
    
    for oferta in ofertas:
        preco = f"R$ {oferta['preco']:.2f}" if oferta.get('preco') else "Sem preco"
        mensagem = oferta.get('mensagem', '')[:150] + '...' if len(oferta.get('mensagem', '')) > 150 else oferta.get('mensagem', '')
        link = oferta.get('link', '#')
        canal = oferta.get('canal', 'N/A')
        
        imagem = oferta.get('imagem')
        if imagem:
            img_html = f'<img class="w-full h-full object-cover" src="{imagem}"/>'
        else:
            img_html = '<span class="material-symbols-outlined text-[#834c4c] text-4xl">shopping_cart</span>'
        
        html += f"""<div class="bg-surface-container-low rounded-lg p-1 overflow-hidden group">
<div class="relative h-48 overflow-hidden rounded-lg bg-surface-container flex items-center justify-center">
{img_html}
</div>
<div class="p-6">
<h4 class="font-bold text-xl mb-1">{canal}</h4>
<p class="text-sm text-on-surface-variant mb-4">{mensagem}</p>
<div class="flex items-center justify-between">
<span class="text-2xl font-black text-on-background">{preco}</span>
<a href="{link}" target="_blank" class="text-primary font-bold text-sm">Ver Oferta</a>
</div>
</div>
</div>
"""
    
    html += """
</div>
</section>
</main>
</body></html>"""
    
    with open(output_path, 'w', encoding='utf-8') as f:
        f.write(html)

# ==================== MAIN ====================
def main():
    parser = argparse.ArgumentParser(
        description="Monitor de Ofertas Telegram",
        formatter_class=argparse.RawDescriptionHelpFormatter
    )
    
    parser.add_argument('--list', '-l', action='store_true', help='Listar canais')
    parser.add_argument('--history', action='store_true', help='Buscar historico')
    parser.add_argument('--telegram', action='store_true', help='Enviar para Telegram')
    parser.add_argument('--test', '-t', metavar='TEXTO', help='Testar regex')
    parser.add_argument('--config', action='store_true', help='Menu interativo')
    parser.add_argument('--dry-run', action='store_true', help='Testar sem enviar')
    parser.add_argument('--verbose', '-v', action='store_true', help='Modo verboso')
    parser.add_argument('--view', action='store_true', help='Gerar HTML do banco')
    parser.add_argument('--server', action='store_true', help='Iniciar servidor web')
    parser.add_argument('--port', type=int, default=5000, help='Porta do servidor (default: 5000)')
    parser.add_argument('--estats', action='store_true', help='Estatisticas do banco')
    parser.add_argument('--save-config', metavar='ARQUIVO', help='Salvar config')
    
    args = parser.parse_args()
    
    global logger
    logger = setup_logging(args.verbose)
    
    # Inicializa banco
    init_db()
    config = carregar_config()
    
    # Comandos
    if args.estats:
        estats = get_estatisticas()
        print(c_cyan("\n[*] Estatisticas:"))
        print(f"   Total: {estats['total']} ofertas")
        for canal, cnt in estats['top_canais']:
            print(f"   {canal}: {cnt}")
        return
    
    if args.view:
        ofertas = get_ofertas(limite=200)
        output = Path(__file__).parent.parent / 'output' / 'ofertas.html'
        output.parent.mkdir(exist_ok=True)
        gerar_html(ofertas, output)
        print(c_green(f"[OK] HTML gerado: {output}"))
        return
    
    if args.server:
        from web import app
        print(c_cyan(f"[*] Servidor iniciado em http://localhost:{args.port}"))
        app.run(host='0.0.0.0', port=args.port)
        return
    
    if args.list:
        cmd_listar_canais(config)
        return
    
    if args.test:
        cmd_testar_regex(args.test)
        return
    
    if args.save_config:
        salvar_config(CONFIG_DEFAULT, args.save_config)
        print(c_green(f"[OK] Config salva em {args.save_config}"))
        return
    
    # Telegram
    API_ID, API_HASH = get_env_credentials()
    client = TelegramClient('data/monitor_ofertas', API_ID, API_HASH)

    async def run_app():
        global _event_loop, _trigger_client, _auth_client, _auth_state
        _event_loop = asyncio.get_event_loop()
        _trigger_client = client
        _auth_client = client

        trigger_thread = threading.Thread(target=start_trigger_server, daemon=True)
        trigger_thread.start()

        await client.connect()

        if not await client.is_user_authorized():
            print(c_yellow("[*] Autenticação necessária. Acesse /auth no frontend."))
            _auth_state['status'] = 'unauthenticated'
            while _auth_state['status'] != 'authenticated':
                await asyncio.sleep(1)
            print(c_green("[OK] Autenticado com sucesso!"))
        else:
            _auth_state['status'] = 'authenticated'

        start_olx_scheduler(client, config)

        if args.history:
            await cmd_buscar_historico(client, config, args.dry_run, args.telegram)
        else:
            canais = config.get('canais', CONFIG_DEFAULT['canais'])
            rate_limiter = RateLimiter(
                config.get('rate_limit', {}).get('max_por_minuto', 20),
                config.get('rate_limit', {}).get('delay_segundos', 2)
            )
            
            print(c_cyan(f"\n[START] Monitorando {len(canais)} canais..."))
            print(c_cyan("[*] Aguardando ofertas (Ctrl+C para parar)"))
            
            pasta = config.get('imagens_pasta', 'data/imagens')
            
            @client.on(events.NewMessage(chats=canais))
            async def handler(event):
                mensagem = event.message.message
                if not mensagem:
                    return
                
                canal = await event.get_chat()
                canal_nome = getattr(canal, 'title', canal.username)
                canal_username = getattr(canal, 'username', None)
                canal_id = canal.id if hasattr(canal, 'id') else None
                
                if canal_username:
                    link = f"https://t.me/{canal_username}/{event.message.id}"
                elif canal_id:
                    link = f"https://t.me/c/{abs(canal_id)}/{event.message.id}"
                else:
                    link = "#"
                
                caminho_imagem = await baixar_midia(event.message, pasta)
                
                if texto_contem_interesse(mensagem, config):
                    preco = extrair_preco(mensagem)
                    preco_info = f"R$ {preco:.2f}" if preco else "Sem preco"
                    print(c_green(f"[OK] Oferta em {canal_nome} - {preco_info}"))
                    
                    codigo_cupom = extrair_codigo_cupom(mensagem) if texto_contem_cupom(mensagem, config) else None
                    desconto = extrair_desconto(mensagem)
                    nome_produto = extrair_nome_produto(mensagem)
                    
                    # Se tem cupom/desconto mas não tem preço, é cupom
                    tipo_oferta = 'cupom' if (codigo_cupom or desconto) and not preco else 'oferta'
                    
                    oferta = {
                        'canal': canal_nome,
                        'preco': preco,
                        'link': link,
                        'data': datetime.now().strftime('%d/%m/%Y %H:%M'),
                        'mensagem': mensagem[:200],
                        'imagem': caminho_imagem,
                        'tipo': tipo_oferta,
                        'codigo': codigo_cupom,
                        'desconto': desconto,
                        'produto': nome_produto
                    }
                    if save_oferta(oferta):
                        if not args.dry_run:
                            msg = f"[ALERT] OFERTA\n{canal_nome}\n{preco_info}\n{link}"
                            await enviar_notificacao(client, msg, caminho_imagem, canal_username, rate_limiter, produto=nome_produto, preco=preco, desconto=desconto)
                
                elif texto_contem_cupom(mensagem, config):
                    print(c_cyan(f"[CUPOM] {canal_nome}"))
                    
                    codigo_cupom = extrair_codigo_cupom(mensagem)
                    
                    oferta = {
                        'canal': canal_nome,
                        'preco': None,
                        'link': link,
                        'data': datetime.now().strftime('%d/%m/%Y %H:%M'),
                        'mensagem': mensagem[:200],
                        'imagem': caminho_imagem,
                        'tipo': 'cupom',
                        'codigo': codigo_cupom
                    }
                    if save_oferta(oferta):
                        if not args.dry_run:
                            msg = f"[CUPOM] {canal_nome}" + (f"\nCódigo: {codigo_cupom}" if codigo_cupom else "") + f"\n{link}"
                            await enviar_notificacao(client, msg, caminho_imagem, canal_username, rate_limiter)
            
            try:
                await client.run_until_disconnected()
            except KeyboardInterrupt:
                print(c_yellow("\n[*] Encerrando..."))
            finally:
                await client.disconnect()
    
    asyncio.run(run_app())

if __name__ == '__main__':
    main()