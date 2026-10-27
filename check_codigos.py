import sqlite3
conn = sqlite3.connect('data/ofertas.db')
c = conn.cursor()
c.execute("SELECT id, preco, codigo FROM ofertas WHERE codigo IS NOT NULL AND codigo != ''")
for r in c.fetchall():
    print(f'ID:{r[0]} Preco:{r[1]} Codigo:{r[2]}')