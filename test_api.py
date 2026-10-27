import sys
sys.path.insert(0, 'D:/Projetos/minhas-ofertas')
from web import app

with app.test_client() as client:
    response = client.get('/api/ofertas')
    print('Status:', response.status_code)
    data = response.get_json()
    print('Count:', len(data))
    if data:
        print('First:', data[0].get('canal', 'N/A')[:30])