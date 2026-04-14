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
        if imagem.startswith('data/imagens/'):
            imagem = '/' + imagem
        elif os.path.exists(imagem):
            imagem = '/data/imagens/' + os.path.basename(imagem)
        else:
            imagem = None
    
    link = oferta.get('link', '#')
    if link == 'Link indisponivel' or not link or not link.startswith('http'):
        link = '#'
    
    return {
        'id': oferta.get('id'),
        'canal': oferta.get('canal', 'N/A'),
        'preco': oferta.get('preco'),
        'link': link,
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
    <link
        href="https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@200;300;400;500;600;700;800&amp;display=swap"
        rel="stylesheet" />
    <link
        href="https://fonts.googleapis.com/css2?family=Material+Symbols+Outlined:wght,FILL@100..700,0..1&amp;display=swap"
        rel="stylesheet" />
    <script id="tailwind-config">
        tailwind.config = {
            darkMode: "class",
            theme: {
                extend: {
                    "colors": {
                        "on-error-container": "#510017",
                        "on-error": "#ffefef",
                        "inverse-primary": "#ff562c",
                        "surface": "#fff4f3",
                        "on-secondary": "#f5f2f1",
                        "outline": "#a26767",
                        "primary-dim": "#9a2100",
                        "surface-bright": "#fff4f3",
                        "on-tertiary-fixed": "#3d2b00",
                        "on-surface-variant": "#834c4c",
                        "tertiary-container": "#fdc003",
                        "secondary-fixed": "#e5e2e1",
                        "error-container": "#f74b6d",
                        "inverse-on-surface": "#cd8c8b",
                        "tertiary-fixed-dim": "#ecb200",
                        "on-tertiary-container": "#553e00",
                        "on-primary-container": "#490b00",
                        "on-primary-fixed": "#000000",
                        "surface-container-high": "#ffdad9",
                        "primary-fixed-dim": "#ff5c34",
                        "surface-container-low": "#ffedec",
                        "tertiary": "#755700",
                        "primary": "#af2700",
                        "on-secondary-fixed": "#403f3f",
                        "outline-variant": "#df9c9b",
                        "on-background": "#4e2122",
                        "tertiary-dim": "#664b00",
                        "inverse-surface": "#240305",
                        "secondary-fixed-dim": "#d6d4d3",
                        "on-tertiary-fixed-variant": "#604700",
                        "on-primary": "#ffefec",
                        "on-primary-fixed-variant": "#5a0f00",
                        "secondary": "#5c5b5b",
                        "on-tertiary": "#fff1db",
                        "secondary-dim": "#504f4f",
                        "surface-tint": "#af2700",
                        "on-secondary-container": "#525151",
                        "background": "#fff4f3",
                        "primary-fixed": "#ff7856",
                        "surface-container-lowest": "#ffffff",
                        "error": "#b41340",
                        "error-dim": "#a70138",
                        "surface-variant": "#ffd2d1",
                        "primary-container": "#ff7856",
                        "secondary-container": "#e5e2e1",
                        "tertiary-fixed": "#fdc003",
                        "on-surface": "#4e2122",
                        "surface-container-highest": "#ffd2d1",
                        "surface-dim": "#ffc7c6",
                        "on-secondary-fixed-variant": "#5c5b5b",
                        "surface-container": "#ffe1e0"
                    },
                    "borderRadius": {
                        "DEFAULT": "1rem",
                        "lg": "2rem",
                        "xl": "3rem",
                        "full": "9999px"
                    },
                    "fontFamily": {
                        "headline": ["Plus Jakarta Sans"],
                        "body": ["Plus Jakarta Sans"],
                        "label": ["Plus Jakarta Sans"]
                    }
                },
            },
        }
    </script>
    <style>
        body {
            font-family: 'Plus Jakarta Sans', sans-serif;
        }

        .material-symbols-outlined {
            font-variation-settings: 'FILL' 0, 'wght' 400, 'GRAD' 0, 'opsz' 24;
        }

        .hide-scrollbar::-webkit-scrollbar {
            display: none;
        }

        .hide-scrollbar {
            -ms-overflow-style: none;
            scrollbar-width: none;
        }
    </style>
</head>

<body class="bg-surface text-on-surface min-h-screen">

    <main class="pt-32 pb-20 px-4 md:px-8 max-w-[1440px] mx-auto flex flex-col md:flex-row gap-8">

        <!-- Main Content -->
        <div class="flex-1 space-y-12">
            <!-- Hero Header -->
            <section>
                <h1 class="text-5xl font-black text-on-surface tracking-tighter mb-2">Minhas Ofertas</h1>
                <p class="text-on-surface-variant max-w-lg">High-velocity tracking para os melhores precos.</p>
            </section>

            <!-- Filters -->
            <div class="bg-surface-container-low rounded-lg p-6">
                <form id="filters" class="flex flex-wrap gap-4 items-end">
                    <div class="flex-1 min-w-[180px]">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Buscar</label>
                        <input type="text" id="search" placeholder="Palavra-chave..." 
                            class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                    </div>
                    <div class="w-28">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Preço Mín</label>
                        <input type="number" id="preco_min" placeholder="0" 
                            class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                    </div>
                    <div class="w-28">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Preço Máx</label>
                        <input type="number" id="preco_max" placeholder="9999" 
                            class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                    </div>
                    <div class="w-32">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Tipo</label>
                        <select id="tipo" class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                            <option value="">Todos</option>
                            <option value="oferta">Ofertas</option>
                            <option value="cupom">Cupons</option>
                        </select>
                    </div>
                    <div class="w-36">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Ordenar</label>
                        <select id="ordenar" class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                            <option value="created_at">Data</option>
                            <option value="preco">Preço</option>
                            <option value="canal">Canal</option>
                        </select>
                    </div>
                    <div class="w-32">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Ordem</label>
                        <select id="ordem" class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                            <option value="desc">Mais recentes</option>
                            <option value="asc">Mais antigos</option>
                        </select>
                    </div>
                    <button type="submit" class="px-6 py-2 bg-primary text-on-primary font-bold rounded-lg hover:bg-primary-dim transition">
                        Filtrar
                    </button>
                </form>
            </div>

            <!-- Stats -->
            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div class="bg-primary-container rounded-lg p-6 flex items-center gap-4">
                    <span class="material-symbols-outlined text-on-primary-container text-4xl">local_offer</span>
                    <div>
                        <p class="text-on-primary-container text-sm font-medium">Total de Ofertas</p>
                        <p id="total-ofertas" class="text-on-primary-container text-3xl font-black">0</p>
                    </div>
                </div>
                <div class="bg-surface-container-low rounded-lg p-6 flex items-center gap-4">
                    <span class="material-symbols-outlined text-primary text-4xl">schedule</span>
                    <div>
                        <p class="text-on-surface-variant text-sm font-medium">Busca mais recente</p>
                        <p id="ultima-atualizacao" class="text-on-surface text-xl font-bold">--:--</p>
                    </div>
                </div>
                <div class="bg-tertiary-container rounded-lg p-6 flex items-center gap-4">
                    <span class="material-symbols-outlined text-on-tertiary-container text-4xl">discount</span>
                    <div>
                        <p class="text-on-tertiary-container text-sm font-medium">Cupons</p>
                        <p id="total-cupons" class="text-on-tertiary-container text-3xl font-black">0</p>
                    </div>
                </div>
            </div>
            
            <!-- Loading -->
            <div id="loading" class="text-center py-12 text-on-surface-variant hidden">
                <span class="material-symbols-outlined text-4xl animate-spin">sync</span>
                <p class="mt-2">Carregando...</p>
            </div>

            <!-- Ofertas Grid -->
            <section class="space-y-6">
                <div class="flex justify-between items-end">
                    <h2 class="text-3xl font-black tracking-tight text-on-background">Resultados</h2>
                    <span id="result-count" class="text-sm font-bold text-primary">0 Oferta(s)</span>
                </div>
                <div id="ofertas-grid" class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                </div>
                <div id="empty" class="text-center py-12 text-on-surface-variant hidden">
                    <span class="material-symbols-outlined text-6xl">search_off</span>
                    <p class="mt-4 text-lg">Nenhum resultado encontrado</p>
                </div>
            </section>
        </div>
    </main>
    <footer class="bg-[#ffedec] mt-20">
        <div
            class="flex flex-col md:flex-row justify-between items-center px-12 py-16 w-full max-w-[1920px] mx-auto gap-8">
            <div class="flex flex-col items-center md:items-start">
                <span class="text-2xl font-black text-[#af2700] mb-2 italic">DEAL.EDIT</span>
                <p class="text-sm text-[#834c4c]">Gerado automaticamente via Monitoramento</p>
            </div>
        </div>
    </footer>

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
            const preco = oferta.preco ? 'R$ ' + oferta.preco.toFixed(2).replace('.', ',') : 'Sem preco';
            const tipoLabel = oferta.tipo === 'cupom' ? '<span class="text-xs bg-tertiary-container text-on-tertiary-container px-2 py-1 rounded font-bold">CUPOM</span>' : '';
            const imgHtml = oferta.imagem 
                ? '<img class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" src="' + oferta.imagem + '" />'
                : '<span class="material-symbols-outlined text-[#834c4c] text-4xl">shopping_cart</span>';
            const msg = oferta.mensagem ? (oferta.mensagem.length > 150 ? oferta.mensagem.slice(0, 150) + '...' : oferta.mensagem) : '';
            
            return '<div class="bg-surface-container-low rounded-lg p-1 overflow-hidden group">' +
                '<div class="relative h-64 overflow-hidden rounded-lg">' +
                    imgHtml +
                '</div>' +
                '<div class="p-6">' +
                    '<div class="flex justify-between items-start mb-2">' +
                        '<h4 class="font-bold text-xl">' + (oferta.canal || 'N/A') + '</h4>' +
                        tipoLabel +
                    '</div>' +
                    '<p class="text-sm text-on-surface-variant mb-4">' + msg + '</p>' +
                    '<div class="flex items-center justify-between">' +
                        '<span class="text-2xl font-black text-on-background">' + preco + '</span>' +
                        '<a href="' + (oferta.link || '#') + '" target="_blank" class="text-primary font-bold text-sm flex items-center gap-1 hover:gap-2 transition-all">' +
                            'Ver Oferta <span class="material-symbols-outlined text-sm">open_in_new</span>' +
                        '</a>' +
                    '</div>' +
                '</div>' +
            '</div>';
        }

        async function loadOfertas() {
            const loading = document.getElementById('loading');
            const grid = document.getElementById('ofertas-grid');
            const empty = document.getElementById('empty');
            
            loading.classList.remove('hidden');
            grid.innerHTML = '';
            empty.classList.add('hidden');
            
            try {
                const response = await fetch(API_URL + '?' + buildParams());
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
                document.getElementById('result-count').textContent = ofertas.length + ' Oferta(s)';
                
                grid.innerHTML = ofertas.map(renderOferta).join('');
            } catch (err) {
                console.error(err);
                loading.classList.add('hidden');
                empty.classList.remove('hidden');
            }
        }

        document.getElementById('filters').addEventListener('submit', function(e) {
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
