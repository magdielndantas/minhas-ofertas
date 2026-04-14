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
            created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
        )
    """)
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_canal ON ofertas(canal)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_preco ON ofertas(preco)")
    cursor.execute("CREATE INDEX IF NOT EXISTS idx_data ON ofertas(data)")
    conn.commit()
    conn.close()

def save_oferta(oferta):
    """Salva uma oferta no banco"""
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    cursor.execute("""
        INSERT INTO ofertas (canal, preco, link, data, mensagem, imagem)
        VALUES (?, ?, ?, ?, ?, ?)
    """, (
        oferta.get('canal'),
        oferta.get('preco'),
        oferta.get('link'),
        oferta.get('data'),
        oferta.get('mensagem'),
        oferta.get('imagem')
    ))
    conn.commit()
    conn.close()

def get_ofertas(filtros=None, limite=100):
    """Busca ofertas com filtros opcionais"""
    conn = sqlite3.connect(DB_PATH)
    conn.row_factory = sqlite3.Row
    cursor = conn.cursor()
    
    query = "SELECT * FROM ofertas WHERE 1=1"
    params = []
    
    if filtros:
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
    
    query += " ORDER BY created_at DESC LIMIT ?"
    params.append(limite)
    
    cursor.execute(query, params)
    rows = cursor.fetchall()
    conn.close()
    
    return [dict(row) for row in rows]

def get_estatisticas():
    """Retorna estatísticas das ofertas"""
    if not os.path.exists(DB_PATH):
        return {'total': 0, 'top_canais': [], 'preco_min': None, 'preco_max': None, 'preco_avg': None}
    
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    
    cursor.execute("SELECT COUNT(*) FROM ofertas")
    total = cursor.fetchone()[0] or 0
    
    cursor.execute("SELECT canal, COUNT(*) as cnt FROM ofertas GROUP BY canal ORDER BY cnt DESC LIMIT 10")
    top_canais = cursor.fetchall()
    
    cursor.execute("SELECT MIN(preco), MAX(preco), AVG(preco) FROM ofertas WHERE preco IS NOT NULL")
    precos = cursor.fetchone()
    
    conn.close()
    
    return {
        'total': total,
        'top_canais': top_canais,
        'preco_min': precos[0],
        'preco_max': precos[1],
        'preco_avg': precos[2]
    }