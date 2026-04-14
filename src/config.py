import json
import os

# Caminho do arquivo de config
DATA_DIR = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data')
CONFIG_PATH = os.path.join(DATA_DIR, 'config.json')

CONFIG_DEFAULT = {
    "canais": [
        "ofertasdodiaon", "promocasinha", "vipsuperofertas", "promopraeles",
        "promobrinquedo", "EconomizandocomJP", "promocaozinha",
        "meusmartphoneofertas", "showofertas2", "nerdofertas",
        "cludeofertas", "comoecomofaz", "topofertasbabykids", "melhoresprecosBR"
    ],
    "palavras_chave": [
        "mouse", "teclado", "monitor", "fone", "headset",
        "webcam", "microfone", "cadeira gamer", "mousepad",
        "gpu", "placa de vídeo", "rtx", "geforce"
    ],
    "termos_ignorar": ["usado", "seminovo", "defeito", "segunda mão", "reembalado"],
    "preco_maximo": None,
    "rate_limit": {"max_por_minuto": 20, "delay_segundos": 2},
    "imagens_pasta": "data/imagens"
}

def carregar_config(config_path=None):
    """Carrega configuração do arquivo JSON ou usa padrão"""
    path = config_path or CONFIG_PATH
    
    if os.path.exists(path):
        with open(path, 'r', encoding='utf-8') as f:
            return json.load(f)
    
    # Se não existir, criar diretório data e salvar config padrão
    os.makedirs(DATA_DIR, exist_ok=True)
    salvar_config(CONFIG_DEFAULT)
    return CONFIG_DEFAULT.copy()

def salvar_config(config, config_path=None):
    """Salva configuração em arquivo JSON"""
    path = config_path or CONFIG_PATH
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        json.dump(config, f, ensure_ascii=False, indent=2)