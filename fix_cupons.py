import sqlite3
import os

DB_PATH = os.path.join(os.path.dirname(__file__), 'data', 'ofertas.db')
conn = sqlite3.connect(DB_PATH)
c = conn.cursor()

# Atualizar: ofertas com código mas sem preco = cupom
c.execute("UPDATE ofertas SET tipo = 'cupom' WHERE codigo IS NOT NULL AND codigo != '' AND preco IS NULL")
atualizados = c.rowcount
conn.commit()

c.execute('SELECT tipo, COUNT(*) FROM ofertas GROUP BY tipo')
for r in c.fetchall():
    print(f'{r[0]}: {r[1]}')

print(f'Atualizados: {atualizados}')
conn.close()