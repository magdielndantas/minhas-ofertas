import re
import os
import sys

def extrair_preco(texto):
    """Extrai o menor preço do texto com suporte a múltiplos formatos brasileiros."""
    if not texto:
        return None
    
    precos_encontrados = []
    texto = texto.strip()
    
    # R$ 1.299,90
    matches = re.findall(r'R\$\s*(\d{1,3}(?:\.\d{3})*),(\d{2})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m[0].replace('.', '') + '.' + m[1])
            precos_encontrados.append(valor)
        except:
            pass
    
    # R$ 1299,90
    matches = re.findall(r'R\$\s*(\d+),(\d{2})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m[0] + '.' + m[1])
            precos_encontrados.append(valor)
        except:
            pass
    
    # R$ 1299.90 (americano)
    matches = re.findall(r'R\$\s*(\d+)\.(\d{2})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m[0] + '.' + m[1])
            precos_encontrados.append(valor)
        except:
            pass
    
    # R$1299 (sem decimais)
    matches = re.findall(r'R\$\s*(\d{3,})(?:\s|$|[.,])', texto)
    for m in matches:
        try:
            valor = float(m)
            precos_encontrados.append(valor)
        except:
            pass
    
    # R$99 (forma curta)
    matches = re.findall(r'R\$(\d{1,3})(?:\s|,|\.|;|$)', texto)
    for m in matches:
        try:
            valor = float(m)
            if valor > 9:
                precos_encontrados.append(valor)
        except:
            pass
    
    # "por R$ 99,90"
    matches = re.findall(r'(?:por|apenas|s[óò]|somente)\s*R\$\s*(\d+(?:\.\d{3})?(?:,\d{2})?)', texto, re.IGNORECASE)
    for m in matches:
        try:
            m_normalizado = m.replace('.', '').replace(',', '.')
            valor = float(m_normalizado)
            precos_encontrados.append(valor)
        except:
            pass
    
    # 1579 REAIS
    matches = re.findall(r'(\d{3,})\s*(?:REAIS?|reais?|R\$|R\s?)?(?:\s|$|[.,])', texto, re.IGNORECASE)
    for m in matches:
        try:
            valor = float(m)
            if 10 <= valor <= 100000:
                precos_encontrados.append(valor)
        except:
            pass
    
    return min(precos_encontrados) if precos_encontrados else None


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
    return any(cupom in texto_lower for cupom in cupons)