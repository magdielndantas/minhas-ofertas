import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(os.path.dirname(__file__)), 'data', 'ofertas.db')

def init_db():
    """Inicializa o banco SQLite"""
    os.makedirs(os.path.dirname(DB_PATH), exist_ok=True)
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS ofertas (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            canal TEXT NOT NULL,
            preco REAL,
            link TEXT,
            data TEXT,
            mensagem TEXT,
            imagem TEXT,
            tipo TEXT DEFAULT 'oferta',
            codigo TEXT,
            desconto INTEGER,
            produto TEXT,
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_canal ON ofertas(canal)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_preco ON ofertas(preco)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_data ON ofertas(data)")
    
    try:
        cursor.execute("SELECT tipo FROM ofertas LIMIT 1")
    except sqlite3.OperationalError:
        cursor.execute("ALTER TABLE ofertas ADD COLUMN tipo TEXT DEFAULT 'oferta'")
    
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_tipo ON ofertas(tipo)")
    conn.commit()
    conn.close()

def save_oferta(oferta):
    """Salva uma oferta no banco, evitando duplicatas"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    link = oferta.get('link')
    canal = oferta.get('canal')
    mensagem = oferta.get('mensagem', '')[:100]
    produto = oferta.get('produto')
    preco = oferta.get('preco')
    
    if link and link != '#':
        cursor.execute("SELECT id FROM ofertas WHERE link = ? LIMIT 1", (link,))
        if cursor.fetchone():
            conn.close()
            return False
    
    if canal and mensagem:
        cursor.execute("SELECT id FROM ofertas WHERE canal = ? AND SUBSTR(mensagem, 1, 100) = ? LIMIT 1", (canal, mensagem))
        if cursor.fetchone():
            conn.close()
            return False
    
    cursor.execute("""
        INSERT INTO ofertas (canal, preco, link, data, mensagem, imagem, tipo, codigo, desconto, produto)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    """, (
        oferta.get('canal'),
        oferta.get('preco'),
        oferta.get('link'),
        oferta.get('data'),
        oferta.get('mensagem'),
        oferta.get('imagem'),
        oferta.get('tipo', 'oferta'),
        oferta.get('codigo'),
        oferta.get('desconto'),
        oferta.get('produto')
    ))
    conn.commit()
    conn.close()
    return True

def get_ofertas(filtros=None, limite=100, offset=0):
    """Busca ofertas com filtros opcionais"""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    query = "SELECT * FROM ofertas WHERE 1=1"
    params = []
    
    if filtros:
        if filtros.get('id'):
            query += " AND id = ?"
            params.append(filtros['id'])
        if filtros.get('canal'):
            query += " AND canal LIKE ?"
            params.append(f"%{filtros['canal']}%")
        if filtros.get('preco_min'):
            query += " AND preco >= ?"
            params.append(filtros['preco_min'])
        if filtros.get('preco_max'):
            query += " AND preco <= ?"
            params.append(filtros['preco_max'])
        if filtros.get('palavra'):
            query += " AND mensagem LIKE ?"
            params.append(f"%{filtros['palavra']}%")
        if filtros.get('tipo'):
            query += " AND tipo = ?"
            params.append(filtros['tipo'])
        if filtros.get('search'):
            query += " AND (mensagem LIKE ? OR canal LIKE ? OR codigo LIKE ?)"
            params.append(f"%{filtros['search']}%")
            params.append(f"%{filtros['search']}%")
            params.append(f"%{filtros['search']}%")
        if filtros.get('codigo'):
            query += " AND codigo LIKE ?"
            params.append(f"%{filtros['codigo']}%")
        if filtros.get('periodo'):
            periodo = filtros['periodo']
            if periodo == 'hoje':
                query += " AND date(created_at) = date('now')"
            elif periodo == 'semana':
                query += " AND date(created_at) >= date('now', '-7 days')"
            elif periodo == 'mes':
                query += " AND date(created_at) >= date('now', '-30 days')"
        
        ordenar = filtros.get('ordenar', 'created_at')
        ordem = 'DESC' if filtros.get('ordem', 'desc') == 'desc' else 'ASC'
        query += f" ORDER BY {ordenar} {ordem}"
    else:
        query += " ORDER BY created_at DESC"
    
    query += " LIMIT ? OFFSET ?"
    params.extend([limite, offset])
    
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]


def get_todas_ofertas_com_produto():
    """Retorna todas as ofertas que têm produto e preço"""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    cursor.execute("SELECT * FROM ofertas WHERE produto IS NOT NULL AND preco IS NOT NULL ORDER BY created_at DESC LIMIT 500")
    rows = cursor.fetchall()
    conn.close()
    return [dict(row) for row in rows]


def get_canais():
    """Retorna lista de canais únicos"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("SELECT DISTINCT canal FROM ofertas ORDER BY canal")
    canais = [row[0] for row in cursor.fetchall()]
    conn.close()
    return canais

def get_estatisticas():
    """Retorna estatísticas das ofertas"""
    if not os.path.exists(DB_PATH):
        return {'total': 0, 'cupons': 0, 'top_canais': [], 'preco_min': None, 'preco_max': None, 'preco_avg': None}
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("SELECT COUNT(*) FROM ofertas")
    total = cursor.fetchone()[0] or 0
    
    cursor.execute("SELECT COUNT(*) FROM ofertas WHERE tipo = 'cupom'")
    cupons = cursor.fetchone()[0] or 0
    
    cursor.execute("SELECT canal, COUNT(*) as cnt FROM ofertas GROUP BY canal ORDER BY cnt DESC LIMIT 10")
    top_canais = cursor.fetchall()
    
    cursor.execute("SELECT MIN(preco), MAX(preco), AVG(preco) FROM ofertas WHERE preco IS NOT NULL")
    precos = cursor.fetchone()
    
    conn.close()
    
    return {
        'total': total,
        'cupons': cupons,
        'top_canais': top_canais,
        'preco_min': precos[0],
        'preco_max': precos[1],
        'preco_avg': precos[2]
    }


def get_ofertas_similares(produto, exclude_id=None, limite=10):
    """Busca ofertas com produto similar (mesmo produto exato - algoritmo Jaccard)"""
    if not produto:
        return []
    
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    palavras = produto.split()[:5]
    if not palavras:
        conn.close()
        return []
    
    conditions = []
    params = []
    for palavra in palavras:
        if len(palavra) > 2:
            conditions.append("produto LIKE ?")
            params.append(f"%{palavra}%")
    
    if not conditions:
        conn.close()
        return []
    
    query = "SELECT * FROM ofertas WHERE " + " OR ".join(conditions) + " AND preco IS NOT NULL"
    if exclude_id:
        query += " AND id != ?"
        params.append(exclude_id)
    query += " ORDER BY preco ASC LIMIT ?"
    params.append(limite)
    
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]


def get_ofertas_por_palavras(palavras, exclude_id=None, limite=10):
    """Busca ofertas por palavras relacionadas (keywords diferentes mas da mesma categoria)"""
    if not palavras or len(palavras) == 0:
        return []
    
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    conditions = []
    params = []
    for palavra in palavras:
        if palavra and len(palavra) > 2:
            conditions.append("produto LIKE ?")
            params.append(f"%{palavra}%")
    
    if not conditions:
        conn.close()
        return []
    
    query = "SELECT * FROM ofertas WHERE (" + " OR ".join(conditions) + ") AND preco IS NOT NULL"
    if exclude_id:
        query += " AND id != ?"
        params.append(exclude_id)
    query += " ORDER BY preco ASC LIMIT ?"
    params.append(limite)
    
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]