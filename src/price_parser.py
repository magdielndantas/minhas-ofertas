import re
import os
import sys

PALAVRAS_RELACIONADAS = {
    'mouse': ['mouse', 'mouse sem fio', 'mouse gamer', 'mouse rgb', 'mouse wireless', 'mouse 7botoes', 'mouse 8botoes', 'mouse 16000dpi', 'mouse logitech', 'mouse razer', 'mouse hyperx', 'mousepad', 'mouse blade'],
    'teclado': ['teclado', 'teclado gamer', 'teclado mecanico', 'teclado sem fio', 'teclado rgb', 'teclado wireless', 'teclado 60%', 'teclado 65%', 'teclado full size', 'teclado logitech', 'teclado razer'],
    'monitor': ['monitor', 'monitor gamer', 'monitor 144hz', 'monitor 240hz', 'monitor 27', 'monitor 24', 'monitor 32', 'monitor ips', 'monitor va', 'monitor oled', 'monitor 4k', 'monitor 2k', 'monitor lg', 'monitor samsung', 'monitor aoc', 'monitor dell'],
    'fone': ['fone', 'headset', 'fone bluetooth', 'fone sem fio', 'earbud', 'airpod', 'fone gamer', 'headset gamer', 'fone jbl', 'fone sony', 'fone bluetooth', 'auricular'],
    'headset': ['headset', 'headset gamer', 'headset usb', 'headset bluetooth', 'headset sem fio', 'headset 7.1'],
    'webcam': ['webcam', 'webcam 4k', 'webcam 1080p', 'webcam 720p', 'camera stream', 'webcam logitech', 'webcam c920'],
    'microfone': ['microfone', 'microfone usb', 'microfone condensador', 'microfone gamer', 'microfone streaming', 'microfone fifine', 'microfone blue'],
    'cadeira': ['cadeira gamer', 'cadeira office', 'cadeira ergonomica', 'cadeira presidente'],
    'mousepad': ['mousepad', 'mousepad gamer', 'mousepad 90x40', 'mousepad xl', 'mousepad rgb', 'mousepad hydro'],
    'gpu': ['gpu', 'placa de video', 'rtx', 'geforce', 'rtx 4090', 'rtx 4080', 'rtx 4070', 'rtx 4060', 'rtx 3060', 'rtx 3050', 'gtx', 'gtx 1660'],
    'notebook': ['notebook', 'laptop', 'notebook gamer', 'macbook', 'notebook lenovo', 'notebook dell', 'notebook samsung'],
    'ssd': ['ssd', 'ssd nvme', 'ssd 1tb', 'ssd 500gb', 'ssd 2tb', 'ssd samsung', 'ssd kingston', 'ssd western'],
    'ram': ['ram', 'memoria ram', 'ddr4', 'ddr5', '16gb', '32gb', '8gb', 'ram corsair', 'ram kingston'],
    'processador': ['processador', 'cpu', 'ryzen', 'intel', 'i5', 'i7', 'i9', 'ryzen 5', 'ryzen 7', 'ryzen 9'],
    'tablet': ['tablet', 'ipad', 'tab s9', 'tab s8'],
    'smartwatch': ['smartwatch', 'relogio', 'apple watch', 'watch', 'pulseira'],
    'celular': ['celular', 'smartphone', 'iphone', 'samsung', 'xiaomi', 'motorola'],
    'fonte': ['fonte', 'fonte gamer', 'fonte 500w', 'fonte 650w', 'fonte 750w', 'fonte corsair', 'fonteEVGA'],
    'coolers': ['cooler', 'fan', 'ventoinha', 'rgb fan'],
}


def get_palavras_relacionadas(palavra_chave):
    """Retorna lista de palavras relacionadas para uma palavra-chave"""
    if not palavra_chave:
        return []
    palavra_lower = palavra_chave.lower()
    for categoria, palavras in PALAVRAS_RELACIONADAS.items():
        if palavra_lower in [p.lower() for p in palavras]:
            return palavras
    return [palavra_chave]

def normalizar_texto(texto):
    """Normaliza texto para comparação"""
    if not texto:
        return ""
    texto = texto.lower()
    texto = texto.replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u')
    texto = texto.replace('ã', 'a').replace('õ', 'o').replace('â', 'a').replace('ê', 'e').replace('ô', 'o')
    texto = re.sub(r'[^\w\s]', ' ', texto)
    texto = re.sub(r'\s+', ' ', texto).strip()
    return texto


def calcular_similaridade(prod1, prod2):
    """Calcula similaridade entre dois produtos (0-100%)"""
    if not prod1 or not prod2:
        return 0
    
    p1 = normalizar_texto(prod1)
    p2 = normalizar_texto(prod2)
    
    if p1 == p2:
        return 100
    
    palavras1 = set(p1.split())
    palavras2 = set(p2.split())
    
    if not palavras1 or not palavras2:
        return 0
    
    interseccao = palavras1 & palavras2
    uniao = palavras1 | palavras2
    
    return int(len(interseccao) / len(uniao) * 100)


def buscar_produtos_similares(produto, todas_ofertas, limite=10, threshold=30):
    """Busca produtos similares usando similaridade"""
    similaridades = []
    
    for oferta in todas_ofertas:
        if oferta.get('produto') and oferta.get('preco'):
            similaridade = calcular_similaridade(produto, oferta.get('produto'))
            if similaridade >= threshold:
                similaridades.append((oferta, similaridade))
    
    similaridades.sort(key=lambda x: x[1], reverse=True)
    return [s[0] for s in similaridades[:limite]]


def extrair_nome_produto(texto):
    """Extrai o nome do produto da mensagem removendo preços e códigos"""
    if not texto:
        return None
    
    texto_original = texto
    
    # Remover emojis iniciais (🔥, 🎮, etc)
    texto = re.sub(r'^[\U00010000-\U0010ffff]+\s*', '', texto)
    
    texto = re.sub(r'R\$\s*\d+(?:[.,]\d{2})?', '', texto, flags=re.IGNORECASE)
    texto = re.sub(r'\d{1,3}(?:\.\d{3})*(?:,\d{2})?(?:REAIS?|reais?)', '', texto, flags=re.IGNORECASE)
    texto = re.sub(r'(?:por|apenas|somente)[:\s]*\d+', '', texto, flags=re.IGNORECASE)
    texto = re.sub(r'https?://\S+', '', texto)
    texto = re.sub(r'@\w+', '', texto)
    texto = re.sub(r'#\w+', '', texto)
    texto = re.sub(r'\b\d{3,}\b', '', texto)
    texto = re.sub(r'(?:cupom|codigo|promocode|frete|desconto|off)[^a-zA-Z]*[A-Z0-9]+', '', texto, flags=re.IGNORECASE)
    texto = re.sub(r'🎟|🏷|✅|🔥|😱|💵|🔗|➡️|🛒', ' ', texto)  # Remover emojis comuns
    texto = re.sub(r'[^\w\s]', ' ', texto)
    texto = re.sub(r'\s+', ' ', texto).strip()
    
    palavras_excluir = {
        'oferta', 'promocao', 'desconto', 'preco', 'valor', 'por', 'apenas', 'somente',
        'à vista', 'vista', 'parcelado', 'com', 'frete', 'gratis', 'gratuito',
        'link', 'clique', 'acessar', 'comprar', 'bug', 'lamp', 'mega', 'super',
        'cupom', 'codigo', 'promocode', 'desconto', 'off', 'porcento', 'porcento',
        'kg', 'un', 'und', 'und', 'cada', 'brinde', 'presente', 'gratis',
        'use', 'resgate', 'após', 'entrar', 'clique', 'convide', 'ganhe',
        'vá', 'venha', 'para', 'nossos', 'grupos', 'ofertas', 'assine',
    }
    
    palavras = [p for p in texto.split() if p not in palavras_excluir and len(p) > 2]
    
    if len(palavras) < 2:
        return None
    
    return ' '.join(palavras[:12])


def extrair_preco(texto):
    """Extrai o primeiro preço válido do texto com suporte a múltiplos formatos brasileiros."""
    if not texto:
        return None
    
    texto = texto.strip()
    
    # Prioridade 1: ✅ R$ 87 (emoji check + preço)
    matches = re.findall(r'✅\s*R?\$\s*(\d+(?:\.\d{3})?(?:,\d{2})?)', texto, re.IGNORECASE)
    for m in matches:
        try:
            m_normalizado = m.replace('.', '').replace(',', '.')
            valor = float(m_normalizado)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 2: "por R$ 99,90" ou "POR:" + valor
    matches = re.findall(r'(?:por|apenas|s[óò]|somente)[:\s]*R?\$?\s*(\d+(?:\.\d{3})?(?:,\d{2})?)', texto, re.IGNORECASE)
    for m in matches:
        try:
            m_normalizado = m.replace('.', '').replace(',', '.')
            valor = float(m_normalizado)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 2: "POR: 599", "POR 169", "POR: 175 REAIS"
    matches = re.findall(r'\bPOR[:\s]*(\d+(?:\.\d{3})?(?:,\d{2})?)\s*(?:REAIS?)?', texto, re.IGNORECASE)
    for m in matches:
        try:
            m_normalizado = m.replace('.', '').replace(',', '.')
            valor = float(m_normalizado)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 3: "A partir de R$ 599"
    matches = re.findall(r'a partir de[:\s]*R?\$?\s*(\d+(?:\.\d{3})?(?:,\d{2})?)', texto, re.IGNORECASE)
    for m in matches:
        try:
            m_normalizado = m.replace('.', '').replace(',', '.')
            valor = float(m_normalizado)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 4: 💵 R$ 559,00 (emoji de dinheiro)
    matches = re.findall(r'💵\s*R?\$\s*(\d+(?:\.\d{3})?(?:,\d{2})?)', texto, re.IGNORECASE)
    for m in matches:
        try:
            m_normalizado = m.replace('.', '').replace(',', '.')
            valor = float(m_normalizado)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 5: "R$ 1.299,90" (com ponto como separador de milhar)
    matches = re.findall(r'R\$\s*(\d{1,3}(?:\.\d{3})*),(\d{2})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m[0].replace('.', '') + '.' + m[1])
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 6: "R$ 1299,90" (sem ponto no milhar)
    matches = re.findall(r'R\$\s*(\d+),(\d{2})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m[0] + '.' + m[1])
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 7: "R$ 1299.90" (formato americano)
    matches = re.findall(r'R\$\s*(\d+)\.(\d{2})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m[0] + '.' + m[1])
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 8: "R$1299" (sem decimais)
    matches = re.findall(r'R\$\s*(\d{3,})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 9: "R$99" (forma curta)
    matches = re.findall(r'R\$(\d{1,3})(?:\s|,|\.|;|$)', texto)
    for m in matches:
        try:
            valor = float(m)
            if valor >= 10:
                return valor
        except:
            pass
    
    # Prioridade 10: "1579 REAIS"
    matches = re.findall(r'(\d{3,})\s*(?:REAIS?|reais?)(?:\s|$|[.,])', texto, re.IGNORECASE)
    for m in matches:
        try:
            valor = float(m)
            if 10 <= valor <= 100000:
                return valor
        except:
            pass
    
    return None


def texto_contem_interesse(texto, config):
    """Verifica se o texto contém palavras-chave e não contém termos ignorados"""
    if not texto:
        return False
    
    texto_lower = texto.lower()
    
    termos_ignorar = config.get('termos_ignorar', [])
    if any(termo in texto_lower for termo in termos_ignorar):
        return False
    
    palavras_chave = config.get('palavras_chave', [])
    if not any(palavra in texto_lower for palavra in palavras_chave):
        return False
    
    preco_max = config.get('preco_maximo')
    if preco_max:
        preco = extrair_preco(texto)
        if preco and preco > preco_max:
            return False
    
    return True


def texto_contem_cupom(texto, config):
    """Verifica se o texto contém cupons de desconto"""
    if not texto:
        return False
    
    texto_lower = texto.lower().replace('á', 'a').replace('é', 'e').replace('í', 'i').replace('ó', 'o').replace('ú', 'u').replace('ã', 'a').replace('õ', 'o')
    
    cupons = config.get('cupons', [])
    if any(cupom in texto_lower for cupom in cupons):
        return True
    
    import re
    
    padroes_cupom = [
        r'cupom[:\s/]+[A-Z0-9]+(?:\s*/\s*[A-Z0-9]+)?',
        r'codigo[:\s]*[A-Z0-9]+',
        r'promocode[:\s]*[A-Z0-9]+',
        r'desconto[:\s]*[0-9]+%',
        r'frete\s*gr(is|at)is',
        r'[A-Z]{2,4}[0-9]{0,6}',
        r'R\$\s*\d+\s*off\b',
        r'use\s*o\s*cupom[:\s]*([A-Z0-9]+)',  # Use o cupom XYZ
        r'🎟\s*([A-Z0-9]+)',  # 🎟 CUPOM123
        r'%.*off\b',  # 12% OFF
    ]
    for padrao in padroes_cupom:
        if re.search(padrao, texto, re.IGNORECASE):
            return True
    
    codigos_encontrados = re.findall(r'[A-Z0-9]{4,12}', texto.upper())
    return bool(codigos_encontrados)


def extrair_codigo_cupom(texto):
    """Extrai o código do cupom do texto"""
    if not texto:
        return None
    
    import re
    padroes = [
        r'cupom[:\s/]+([A-Z0-9]+(?:\s*/\s*[A-Z0-9]+)?)',  # CUPOM: AREA01 / BRAE1
        r'codigo[:\s]*([A-Z0-9]+)',
        r'promocode[:\s]*([A-Z0-9]+)',
        r'(?<!\w)([A-Z]{2,4}[0-9]{0,6})(?!\w)',
    ]
    
    for padrao in padroes:
        match = re.search(padrao, texto, re.IGNORECASE)
        if match:
            return match.group(1).upper()
    
    return None


def extrair_desconto(texto):
    """Extrai percentual de desconto do texto"""
    if not texto:
        return None
    
    import re
    padroes = [
        r'(\d+)%\s*(?:off|desconto|descount)',  # 50% OFF, 30% desconto
        r'desconto[:\s]*(\d+)%',  # DESCONTO: 20%
        r'(-?\d+)%\s*off\b',  # -50% OFF
    ]
    
    for padrao in padroes:
        match = re.search(padrao, texto, re.IGNORECASE)
        if match:
            return int(match.group(1))
    
    return None