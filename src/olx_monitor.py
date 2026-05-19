import requests
import re
import os
import json
import logging
from datetime import datetime
from urllib.parse import quote_plus

logger = logging.getLogger(__name__)

HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'Accept-Language': 'pt-BR,pt;q=0.9,en-US;q=0.8,en;q=0.7',
    'Accept-Encoding': 'gzip, deflate, br',
    'Connection': 'keep-alive',
}

SESSION_FILE = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data', 'olx_state.json')

class OLXMonitor:
    def __init__(self, keywords, termos_ignorar=None, preco_maximo=None, db_save_func=None, notify_func=None):
        self.keywords = keywords
        self.termos_ignorar = termos_ignorar or []
        self.preco_maximo = preco_maximo
        self.db_save_func = db_save_func
        self.notify_func = notify_func
        self.session = requests.Session()
        self.session.headers.update(HEADERS)
        self._load_state()

    def _load_state(self):
        if os.path.exists(SESSION_FILE):
            try:
                with open(SESSION_FILE, 'r') as f:
                    self.state = json.load(f)
            except Exception:
                self.state = {}
        else:
            self.state = {}
        self.seen_ids = set(self.state.get('seen_ids', []))

    def _save_state(self):
        self.state['seen_ids'] = list(self.seen_ids)
        self.state['last_run'] = datetime.now().isoformat()
        os.makedirs(os.path.dirname(SESSION_FILE), exist_ok=True)
        with open(SESSION_FILE, 'w') as f:
            json.dump(self.state, f)

    def _extract_price(self, text):
        match = re.search(r'R?\$?\s*(\d{1,3}(?:\.\d{3})*,\d{2}|\d{2})', text.replace('.', ''))
        if match:
            price_str = match.group(1).replace(',', '.')
            try:
                return float(price_str)
            except ValueError:
                pass
        return None

    def _matches_keywords(self, title, description=''):
        text = (title + ' ' + description).lower()
        for kw in self.keywords:
            if kw.lower() in text:
                for termo in self.termos_ignorar:
                    if termo.lower() in text:
                        return False, f"Ignorado por conter '{termo}'"
                return True, None
        return False, None

    def _search_olx(self, keyword, max_price=None, page=1):
        base_url = f"https://www.olx.com.br/brasil?q={quote_plus(keyword)}"
        if max_price:
            base_url += f"&ps={max_price}"
        if page > 1:
            base_url += f"&o={page}"
        
        try:
            response = self.session.get(base_url, timeout=15)
            response.raise_for_status()
            return response.text
        except requests.RequestException as e:
            logger.error(f"Erro ao buscar OLX para '{keyword}': {e}")
            return None

    def _parse_listings(self, html):
        listings = []
        listing_pattern = re.compile(
            r'href="(https://www\.olx\.com\.br/[^"]*item[^"]*)"[^>]*>.*?<h2[^>]*>(.*?)</h2>.*?data-testid="ad-price"[^>]*>(.*?)</span>',
            re.DOTALL
        )
        
        for match in re.finditer(r'<a[^>]*href="(https://www\.olx\.com\.br/[^"]*item[^"]*)"[^>]*>.*?<h2[^>]*>(.*?)</h2>', html, re.DOTALL):
            url = match.group(1)
            title = re.sub(r'<[^>]+>', '', match.group(2)).strip()
            
            price_match = re.search(r'<span[^>]*data-testid="ad-price"[^>]*>(.*?)</span>', html[match.start():match.start()+2000], re.DOTALL)
            price_text = re.sub(r'<[^>]+>', '', price_match.group(1)) if price_match else ''
            price = self._extract_price(price_text)
            
            item_id = re.search(r'/item/(\d+)', url)
            item_id = item_id.group(1) if item_id else url
            
            listings.append({
                'id': item_id,
                'title': title,
                'url': url,
                'price': price,
                'price_text': price_text.strip()
            })
        
        return listings

    def _get_ad_details(self, url):
        try:
            response = self.session.get(url, timeout=15)
            response.raise_for_status()
            html = response.text
            
            description = ''
            desc_match = re.search(r'data-testid="ad-description"[^>]*>(.*?)</div>', html, re.DOTALL)
            if desc_match:
                description = re.sub(r'<[^>]+>', '', desc_match.group(1)).strip()
            
            location = ''
            loc_match = re.search(r'<span[^>]*data-testid="location[^>]*>(.*?)</span>', html, re.DOTALL)
            if loc_match:
                location = re.sub(r'<[^>]+>', '', loc_match.group(1)).strip()
            
            return {'description': description, 'location': location}
        except Exception as e:
            logger.error(f"Erro ao buscar detalhes: {e}")
            return {'description': '', 'location': ''}

    def run_search(self):
        new_offers = []
        
        for keyword in self.keywords:
            logger.info(f"Buscando OLX: {keyword}")
            
            for page in range(1, 3):
                html = self._search_olx(keyword, max_price=self.preco_maximo, page=page)
                if not html:
                    break
                
                listings = self._parse_listings(html)
                
                for listing in listings:
                    if listing['id'] in self.seen_ids:
                        continue
                    
                    self.seen_ids.add(listing['id'])
                    
                    if listing['price'] and self.preco_maximo and listing['price'] > self.preco_maximo:
                        continue
                    
                    details = self._get_ad_details(listing['url'])
                    matches, reason = self._matches_keywords(listing['title'], details['description'])
                    
                    if matches:
                        offer = {
                            'canal': f'OLX - {keyword}',
                            'preco': listing['price'],
                            'link': listing['url'],
                            'data': datetime.now().strftime('%d/%m/%Y %H:%M'),
                            'mensagem': f"{listing['title']}\n\n{listing['price_text']}\n\n{details['description'][:300]}",
                            'imagem': None,
                            'tipo': 'oferta',
                            'produto': listing['title']
                        }
                        new_offers.append(offer)
                        logger.info(f"Nova oferta OLX: {listing['title']} - {listing['price_text']}")
        
        self._save_state()
        return new_offers

def run_olx_monitor(config, db_save_func, notify_func=None):
    keywords = config.get('palavras_chave', [])
    termos_ignorar = config.get('termos_ignorar', [])
    preco_maximo = config.get('preco_maximo')
    
    monitor = OLXMonitor(keywords, termos_ignorar, preco_maximo, db_save_func, notify_func)
    
    offers = monitor.run_search()
    
    saved_count = 0
    for offer in offers:
        if db_save_func and db_save_func(offer):
            saved_count += 1
    
    logger.info(f"OLX: {saved_count} novas ofertas salvas de {len(offers)} encontradas")
    
    return offers