from datetime import datetime
import os
from flask import Flask, render_template_string, jsonify, send_from_directory, request
from src.database import get_ofertas

app = Flask(__name__)


@app.route('/data/imagens/<filename>')
def serve_image(filename):
    img_dir = os.path.join(os.path.dirname(__file__), 'data', 'imagens')
    return send_from_directory(img_dir, filename)


def oferta_to_dict(oferta):
    """Converte oferta para dict com valores padrão"""
    imagem = oferta.get('imagem')
    if imagem:
        if imagem.startswith('http'):
            pass
        else:
            imagem = '/data/imagens/' + os.path.basename(imagem)
    
    return {
        'id': oferta.get('id'),
        'canal': oferta.get('canal', 'N/A'),
        'preco': oferta.get('preco'),
        'link': oferta.get('link', '#'),
        'mensagem': oferta.get('mensagem', ''),
        'imagem': imagem,
        'data': oferta.get('data', ''),
        'tipo': oferta.get('tipo', 'oferta'),
        'created_at': oferta.get('created_at', '')
    }


HTML_TEMPLATE = '''<!DOCTYPE html>
<html class="light" lang="pt-BR">

<head>
    <meta charset="utf-8" />
    <meta content="width=device-width, initial-scale=1.0" name="viewport" />
    <title>Minhas Ofertas</title>
    <script src="https://cdn.tailwindcss.com?plugins=forms,container-queries"></script>
    <link href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@200..800&display=swap" rel="stylesheet" />
    <link href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:opsz,wght,FILL@24,400,0" rel="stylesheet" />
    <script>
        tailwind.config = {
            darkMode: "class",
            theme: {
                extend: {
                    colors: {
                        primary: "#af2700", "on-primary": "#ffefec", "primary-container": "#ff7856",
                        surface: "#fff4f3", "on-surface": "#4e2122", "on-surface-variant": "#834c4c",
                        "surface-container-low": "#ffedec", "tertiary-container": "#fdc003",
                        "on-tertiary-container": "#553e00"
                    },
                    fontFamily: { "headline": ["Plus Jakarta Sans"], "body": ["Plus Jakarta Sans"] }
                }
            }
        }
    </script>
    <style>
        body { font-family: 'Plus Jakarta Sans', sans-serif; }
        .material-symbols-outlined { font-variation-settings: 'FILL' 0, 'wght' 400; }
        .hide-scrollbar::-webkit-scrollbar { display: none; }
    </style>
</head>

<body class="bg-surface text-on-surface min-h-screen">
    <main class="pt-24 pb-12 px-4 md:px-8 max-w-[1600px] mx-auto">
        <header class="mb-8">
            <h1 class="text-4xl font-black text-on-surface tracking-tight">Minhas Ofertas</h1>
            <p class="text-on-surface-variant">High-velocity tracking</p>
        </header>

        <div class="bg-white rounded-xl p-4 mb-6 shadow-sm">
            <form id="filters" class="flex flex-wrap gap-4 items-end">
                <div class="flex-1 min-w-[200px]">
                    <label class="block text-sm font-medium text-on-surface-variant mb-1">Buscar</label>
                    <input type="text" id="search" placeholder="Palavra-chave..." 
                        class="w-full px-4 py-2 rounded-lg border border-[#df9c9b] focus:border-primary focus:ring-1 focus:ring-primary outline-none">
                </div>
                <div class="w-32">
                    <label class="block text-sm font-medium text-on-surface-variant mb-1">Preço Mín</label>
                    <input type="number" id="preco_min" placeholder="0" 
                        class="w-full px-4 py-2 rounded-lg border border-[#df9c9b] focus:border-primary outline-none">
                </div>
                <div class="w-32">
                    <label class="block text-sm font-medium text-on-surface-variant mb-1">Preço Máx</label>
                    <input type="number" id="preco_max" placeholder="9999" 
                        class="w-full px-4 py-2 rounded-lg border border-[#df9c9b] focus:border-primary outline-none">
                </div>
                <div class="w-40">
                    <label class="block text-sm font-medium text-on-surface-variant mb-1">Tipo</label>
                    <select id="tipo" class="w-full px-4 py-2 rounded-lg border border-[#df9c9b] focus:border-primary outline-none">
                        <option value="">Todos</option>
                        <option value="oferta">Ofertas</option>
                        <option value="cupom">Cupons</option>
                    </select>
                </div>
                <div class="w-40">
                    <label class="block text-sm font-medium text-on-surface-variant mb-1">Ordenar</label>
                    <select id="ordenar" class="w-full px-4 py-2 rounded-lg border border-[#df9c9b] focus:border-primary outline-none">
                        <option value="created_at">Data</option>
                        <option value="preco">Preço</option>
                        <option value="canal">Canal</option>
                    </select>
                </div>
                <div class="w-32">
                    <label class="block text-sm font-medium text-on-surface-variant mb-1">Ordem</label>
                    <select id="ordem" class="w-full px-4 py-2 rounded-lg border border-[#df9c9b] focus:border-primary outline-none">
                        <option value="desc">Mais recentes</option>
                        <option value="asc">Mais antigos</option>
                    </select>
                </div>
                <button type="submit" class="px-6 py-2 bg-primary text-white font-bold rounded-lg hover:bg-[#9a2100] transition">
                    Filtrar
                </button>
            </form>
        </div>

        <div class="flex gap-4 mb-6">
            <div class="bg-primary-container rounded-lg p-4 flex items-center gap-3">
                <span class="material-symbols-outlined text-on-primary-container text-3xl">local_offer</span>
                <div>
                    <p class="text-on-primary-container text-xs font-medium">Total</p>
                    <p id="total-ofertas" class="text-on-primary-container text-2xl font-black">0</p>
                </div>
            </div>
            <div class="bg-surface-container-low rounded-lg p-4 flex items-center gap-3">
                <span class="material-symbols-outlined text-primary text-3xl">schedule</span>
                <div>
                    <p class="text-on-surface-variant text-xs font-medium">Última atualização</p>
                    <p id="ultima-atualizacao" class="text-on-surface text-lg font-bold">--:--</p>
                </div>
            </div>
            <div class="bg-tertiary-container rounded-lg p-4 flex items-center gap-3">
                <span class="material-symbols-outlined text-on-tertiary-container text-3xl">discount</span>
                <div>
                    <p class="text-on-tertiary-container text-xs font-medium">Cupons</p>
                    <p id="total-cupons" class="text-on-tertiary-container text-2xl font-black">0</p>
                </div>
            </div>
        </div>

        <div id="loading" class="text-center py-12 text-on-surface-variant hidden">
            <span class="material-symbols-outlined text-4xl animate-spin">sync</span>
            <p class="mt-2">Carregando...</p>
        </div>

        <div id="ofertas-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4 gap-6">
        </div>

        <div id="empty" class="text-center py-12 text-on-surface-variant hidden">
            <span class="material-symbols-outlined text-6xl">search_off</span>
            <p class="mt-4 text-lg">Nenhum resultado encontrado</p>
        </div>
    </main>

    <script>
        const API_URL = '/api/ofertas';
        
        function buildParams() {
            const params = new URLSearchParams();
            const search = document.getElementById('search').value;
            const precoMin = document.getElementById('preco_min').value;
            const precoMax = document.getElementById('preco_max').value;
            const tipo = document.getElementById('tipo').value;
            const ordenar = document.getElementById('ordenar').value;
            const ordem = document.getElementById('ordem').value;
            
            if (search) params.append('search', search);
            if (precoMin) params.append('preco_min', precoMin);
            if (precoMax) params.append('preco_max', precoMax);
            if (tipo) params.append('tipo', tipo);
            params.append('ordenar', ordenar);
            params.append('ordem', ordem);
            params.append('limite', 100);
            
            return params.toString();
        }

        function renderOferta(oferta) {
            const preco = oferta.preco ? `R$ ${oferta.preco.toFixed(2)}` : 'Sem preco';
            const tipoLabel = oferta.tipo === 'cupom' ? '<span class="text-xs bg-tertiary-container text-on-tertiary-container px-2 py-1 rounded font-bold">CUPOM</span>' : '';
            const imgHtml = oferta.imagem 
                ? `<img class="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" src="${oferta.imagem}" />`
                : `<span class="material-symbols-outlined text-[#834c4c] text-4xl">shopping_cart</span>`;
            const msg = oferta.mensagem ? (oferta.mensagem.length > 100 ? oferta.mensagem.slice(0, 100) + '...' : oferta.mensagem) : '';
            
            return `
                <div class="bg-surface-container-low rounded-lg overflow-hidden group">
                    <div class="relative h-48 overflow-hidden rounded-lg bg-white flex items-center justify-center">
                        ${imgHtml}
                    </div>
                    <div class="p-4">
                        <div class="flex items-center justify-between mb-2">
                            <h4 class="font-bold text-sm truncate">${oferta.canal || 'N/A'}</h4>
                            ${tipoLabel}
                        </div>
                        <p class="text-xs text-on-surface-variant mb-3 line-clamp-2">${msg}</p>
                        <div class="flex items-center justify-between">
                            <span class="text-lg font-black text-on-surface">${preco}</span>
                            <a href="${oferta.link || '#'}" target="_blank" class="text-primary text-sm font-bold flex items-center gap-1">
                                Ver <span class="material-symbols-outlined text-sm">open_in_new</span>
                            </a>
                        </div>
                    </div>
                </div>
            `;
        }

        async function loadOfertas() {
            const loading = document.getElementById('loading');
            const grid = document.getElementById('ofertas-grid');
            const empty = document.getElementById('empty');
            
            loading.classList.remove('hidden');
            grid.innerHTML = '';
            empty.classList.add('hidden');
            
            try {
                const response = await fetch(`${API_URL}?${buildParams()}`);
                const ofertas = await response.json();
                
                loading.classList.add('hidden');
                
                if (ofertas.length === 0) {
                    empty.classList.remove('hidden');
                    return;
                }
                
                const totalOfertas = ofertas.filter(o => o.tipo === 'oferta').length;
                const totalCupons = ofertas.filter(o => o.tipo === 'cupom').length;
                
                document.getElementById('total-ofertas').textContent = totalOfertas;
                document.getElementById('total-cupons').textContent = totalCupons;
                document.getElementById('ultima-atualizacao').textContent = new Date().toLocaleString('pt-BR');
                
                grid.innerHTML = ofertas.map(renderOferta).join('');
            } catch (err) {
                console.error(err);
                loading.classList.add('hidden');
                empty.classList.remove('hidden');
            }
        }

        document.getElementById('filters').addEventListener('submit', (e) => {
            e.preventDefault();
            loadOfertas();
        });

        loadOfertas();
    </script>
</body>

</html>'''


@app.route('/')
def index():
    return render_template_string(HTML_TEMPLATE)


@app.route('/api/ofertas')
def api_ofertas():
    filtros = {}
    
    if request.args.get('search'):
        filtros['search'] = request.args.get('search')
    if request.args.get('preco_min'):
        filtros['preco_min'] = float(request.args.get('preco_min'))
    if request.args.get('preco_max'):
        filtros['preco_max'] = float(request.args.get('preco_max'))
    if request.args.get('tipo'):
        filtros['tipo'] = request.args.get('tipo')
    if request.args.get('ordenar'):
        filtros['ordenar'] = request.args.get('ordenar')
    if request.args.get('ordem'):
        filtros['ordem'] = request.args.get('ordem')
    if request.args.get('limite'):
        filtros['limite'] = int(request.args.get('limite'))
    
    ofertas_raw = get_ofertas(filtros=filtros)
    ofertas = [oferta_to_dict(o) for o in ofertas_raw]
    return jsonify(ofertas)


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
