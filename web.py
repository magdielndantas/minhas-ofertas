from datetime import datetime
import os
from flask import Flask, render_template_string, jsonify, send_from_directory, request
from src.database import get_ofertas, get_canais, get_ofertas_similares, get_todas_ofertas_com_produto, get_ofertas_por_palavras

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
        'codigo': oferta.get('codigo'),
        'desconto': oferta.get('desconto'),
        'produto': oferta.get('produto'),
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
                    "on-error-container": "#ffdad9",
                    "on-error": "#ffffff",
                    "inverse-primary": "#fb923c",
                    "surface": "#0f172a",
                    "on-secondary": "#cbd5e1",
                    "outline": "#475569",
                    "primary-dim": "#ea580c",
                    "surface-bright": "#1e293b",
                    "on-tertiary-fixed": "#e0e7ff",
                    "on-surface-variant": "#cbd5e1",
                    "tertiary-container": "#312e81",
                    "secondary-fixed": "#334155",
                    "error-container": "#7f1d1d",
                    "inverse-on-surface": "#0f172a",
                    "tertiary-fixed-dim": "#6366f1",
                    "on-tertiary-container": "#eef2ff",
                    "on-primary-container": "#fff7ed",
                    "on-primary-fixed": "#431407",
                    "surface-container-high": "#1e293b",
                    "primary-fixed-dim": "#ea580c",
                    "surface-container-low": "#020617",
                    "tertiary": "#818cf8",
                    "primary": "#fb923c",
                    "on-secondary-fixed": "#f8fafc",
                    "outline-variant": "#334155",
                    "on-background": "#f8fafc",
                    "tertiary-dim": "#6366f1",
                    "inverse-surface": "#f8fafc",
                    "secondary-fixed-dim": "#475569",
                    "on-tertiary-fixed-variant": "#c7d2fe",
                    "on-primary": "#ffffff",
                    "on-primary-fixed-variant": "#7c2d12",
                    "secondary": "#64748b",
                    "on-tertiary": "#1e1b4b",
                    "secondary-dim": "#475569",
                    "surface-tint": "#fb923c",
                    "on-secondary-container": "#f1f5f9",
                    "background": "#020617",
                    "primary-fixed": "#ffedea",
                    "surface-container-lowest": "#020617",
                    "error": "#ef4444",
                    "error-dim": "#dc2626",
                    "surface-variant": "#1e293b",
                    "primary-container": "#9a3412",
                    "secondary-container": "#1e293b",
                    "tertiary-fixed": "#312e81",
                    "on-surface": "#f8fafc",
                    "surface-container-highest": "#334155",
                    "surface-dim": "#0a0f1e",
                    "on-secondary-fixed-variant": "#64748b",
                    "surface-container": "#1e293b"
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
                <p class="text-on-surface-variant max-w-lg">Monitoramento de canais de ofertas do Telegram.</p>
            </section>

            <!-- Filters -->
            <div class="bg-surface-container-low rounded-lg p-6">
                <form id="filters" class="flex flex-wrap gap-4 items-end">
                    <div class="flex-1 min-w-[180px]">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Buscar</label>
                        <input type="text" id="search" placeholder="Palavra ou código..." 
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
                    <div class="w-40">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Canal</label>
                        <select id="canal" class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                            <option value="">Todos</option>
                        </select>
                    </div>
                    <div class="w-32">
                        <label class="block text-sm font-medium text-on-surface-variant mb-1">Período</label>
                        <select id="periodo" class="w-full px-4 py-2 rounded-lg border border-outline focus:border-primary outline-none">
                            <option value="">Todos</option>
                            <option value="hoje">Hoje</option>
                            <option value="semana">Esta semana</option>
                            <option value="mes">Este mês</option>
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

            <!-- Tabs -->
            <div class="flex border-b border-outline">
                <button id="tab-ofertas" class="px-6 py-3 font-bold text-primary border-b-2 border-primary" onclick="switchTab('ofertas')">Ofertas</button>
                <button id="tab-cupons" class="px-6 py-3 font-bold text-on-surface-variant hover:text-on-surface" onclick="switchTab('cupons')">Cupons</button>
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
                    <span class="material-symbols-outlined text-on-tertiary-container text-4xl">tag</span>
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

    <script>
        console.log('JS starting...');
        const API_URL = '/api/ofertas';
        
        function buildParams() {
            const params = new URLSearchParams();
            const search = document.getElementById('search').value;
            const precoMin = document.getElementById('preco_min').value;
            const precoMax = document.getElementById('preco_max').value;
            const tipo = document.getElementById('tipo').value;
            const canal = document.getElementById('canal').value;
            const periodo = document.getElementById('periodo').value;
            const ordenar = document.getElementById('ordenar').value;
            const ordem = document.getElementById('ordem').value;
            
            if (search) params.append('search', search);
            if (precoMin) params.append('preco_min', precoMin);
            if (precoMax) params.append('preco_max', precoMax);
            if (tipo) params.append('tipo', tipo);
            if (canal) params.append('canal', canal);
            if (periodo) params.append('periodo', periodo);
            params.append('ordenar', ordenar);
            params.append('ordem', ordem);
            
            return params.toString();
        }

        var allOfertas = [];
        var offset = 0;
        var limit = 50;
        var loadingMore = false;
        var hasMore = true;
        var autoRefreshInterval;

        function loadCanais() {
            fetch('/api/canais')
                .then(function(res) { return res.json(); })
                .then(function(canais) {
                    var select = document.getElementById('canal');
                    canais.forEach(function(c) {
                        var opt = document.createElement('option');
                        opt.value = c;
                        opt.textContent = c;
                        select.appendChild(opt);
                    });
                })
                .catch(function(e) { console.error(e); });
        }

        function loadOfertas(reset) {
            console.log('>>> loadOfertas START, reset =', reset);
            if (reset === undefined) reset = true;
            if (reset) {
                offset = 0;
                allOfertas = [];
                hasMore = true;
            }
            if (loadingMore || !hasMore) return;
            
            var loading = document.getElementById('loading');
            var grid = document.getElementById('ofertas-grid');
            
            if (reset) {
                loading.classList.remove('hidden');
                grid.innerHTML = '';
                document.getElementById('empty').classList.add('hidden');
            }
            
            loadingMore = true;
            
            var params = buildParams() + '&offset=' + offset + '&limit=' + limit;
            console.log('Fetching URL:', API_URL + '?' + params);
            fetch(API_URL + '?' + params)
                .then(function(response) { console.log('Response:', response.status); return response.json(); })
                .then(function(ofertas) { console.log('Ofertas:', ofertas.length);
                    loading.classList.add('hidden');
                    
                    if (reset && ofertas.length === 0) {
                        document.getElementById('empty').classList.remove('hidden');
                        return;
                    }
                    
                    if (ofertas.length < limit) {
                        hasMore = false;
                    }
                    
                    allOfertas = reset ? ofertas : allOfertas.concat(ofertas);
                    offset += ofertas.length;
                    
                    var totalOfertas = 0;
                    var totalCupons = 0;
                    for (var i = 0; i < allOfertas.length; i++) {
                        if (allOfertas[i].tipo === 'oferta') totalOfertas++;
                        else if (allOfertas[i].tipo === 'cupom') totalCupons++;
                    }
                    
                    document.getElementById('total-ofertas').textContent = totalOfertas;
                    document.getElementById('total-cupons').textContent = totalCupons;
                    document.getElementById('ultima-atualizacao').textContent = new Date().toLocaleString('pt-BR');
                    document.getElementById('result-count').textContent = allOfertas.length + ' Oferta(s)';
                    
                    var html = '';
                    for (var j = 0; j < allOfertas.length; j++) {
                        html += renderOferta(allOfertas[j], false);
                    }
                    grid.innerHTML = html;
                })
                .catch(function(err) {
                    console.error(err);
                    loading.classList.add('hidden');
                })
                .then(function() {
                    loadingMore = false;
                });
        }

        function setupInfiniteScroll() {
            window.addEventListener('scroll', function() {
                if (loadingMore || !hasMore) return;
                var scrollBottom = window.innerHeight + window.scrollY;
                var pageHeight = document.documentElement.scrollHeight;
                if (pageHeight - scrollBottom < 300) {
                    loadOfertas(false);
                }
            });
        }

        function setupAutoRefresh() {
            autoRefreshInterval = setInterval(function() {
                loadOfertas(true);
            }, 60000);
        }

        function renderOferta(oferta, isLowestPrice) {
            if (isLowestPrice === undefined) isLowestPrice = false;
            var preco = oferta.preco ? 'R$ ' + Number(oferta.preco).toLocaleString('pt-BR', {minimumFractionDigits: 2}) : 'Grátis';
            var tipoLabel = oferta.tipo === 'cupom' ? '<span class="text-xs bg-tertiary-container text-on-tertiary-container px-2 py-1 rounded font-bold">CUPOM</span>' : '';
            var descontoLabel = oferta.desconto ? '<span class="text-xs bg-green-600 text-white px-2 py-1 rounded font-bold">' + oferta.desconto + '% OFF</span>' : '';
            var melhorPrecoBadge = isLowestPrice ? '<span class="text-xs bg-blue-600 text-white px-2 py-1 rounded font-bold">MELHOR PREÇO</span>' : '';
            var imgSrc = oferta.imagem ? oferta.imagem : '';
            var imgHtml = imgSrc ? '<img class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" src="' + imgSrc + '" />' : '<span class="material-symbols-outlined text-[#834c4c] text-4xl">shopping_cart</span>';
            var msg = oferta.mensagem || '';
            if (msg.length > 150) msg = msg.substring(0, 150) + '...';
            var codigoCupom = oferta.codigo ? '<div class="text-sm font-bold text-primary mt-2">Código: ' + String(oferta.codigo).replace(/</g, '&lt;') + '</div>' : '';
            var canal = oferta.canal || 'N/A';
            var link = oferta.link || '#';

            var html = '<div class="bg-surface-container-low rounded-lg p-1 overflow-hidden group cursor-pointer hover:ring-2 hover:ring-primary transition" onclick="openModal(' + oferta.id + ')">';
            html += '<div class="relative h-64 overflow-hidden rounded-lg flex items-center justify-center bg-surface-container">' + imgHtml + '</div>';
            html += '<div class="p-6">';
            html += '<div class="flex justify-between items-start mb-2 gap-2 flex-wrap"><h4 class="font-bold text-xl">' + canal + '</h4>' + tipoLabel + descontoLabel + melhorPrecoBadge + '</div>';
            html += '<p class="text-sm text-on-surface-variant mb-2">' + msg + '</p>' + codigoCupom;
            html += '<div class="flex items-center justify-between mt-4">';
            html += '<span class="text-2xl font-black text-on-background">' + preco + '</span>';
            html += '<a href="' + link + '" target="_blank" class="text-primary font-bold text-sm flex items-center gap-1 hover:gap-2 transition-all" onclick="event.stopPropagation()">Ver Oferta <span class="material-symbols-outlined text-sm">open_in_new</span></a>';
            html += '</div></div></div>';
            return html;
        }

        function openModal(id) {
            var oferta = allOfertas.find(function(o) { return o.id === id; });
            if (!oferta) return;
            
            var preco = oferta.preco ? 'R$ ' + Number(oferta.preco).toLocaleString('pt-BR', {minimumFractionDigits: 2}) : 'Sem preco';
            var tipoLabel = oferta.tipo === 'cupom' ? '<span class="bg-tertiary-container text-on-tertiary-container px-3 py-1 rounded font-bold">CUPOM</span>' : '';
            var descontoLabel = oferta.desconto ? '<span class="bg-green-600 text-white px-3 py-1 rounded font-bold">' + oferta.desconto + '% OFF</span>' : '';
            
            var imgHtml = oferta.imagem ? '<img class="w-full max-h-64 object-contain rounded-lg mb-4" src="' + oferta.imagem + '" />' : '<div class="w-full h-48 bg-surface-container flex items-center justify-center rounded-lg mb-4"><span class="material-symbols-outlined text-6xl text-[#834c4c]">shopping_cart</span></div>';
            
            var codigoHtml = '';
            if (oferta.codigo) {
                var btnOnclick = 'copiarCodigo(this, ' + JSON.stringify(String(oferta.codigo).replace(/</g, '&lt;')) + ')';
                codigoHtml = '<div class="text-lg font-bold text-primary mb-4">Código: ' + String(oferta.codigo).replace(/</g, '&lt;') + ' <button class="text-sm font-normal ml-2 underline" onclick="' + btnOnclick + '">Copiar</button></div>';
            }
            
            var canal = oferta.canal || 'N/A';
            var mensagem = oferta.mensagem || '';
            var link = oferta.link || '#';
            var data = oferta.data || '';
            
            var html = '<div class="bg-surface-container rounded-2xl p-6 max-w-lg w-full max-h-[90vh] overflow-y-auto">';
            html += '<div class="flex justify-between items-start mb-4 gap-2"><h3 class="text-2xl font-black">' + canal + '</h3><button onclick="closeModal()" class="text-on-surface-variant hover:text-on-surface text-2xl">&times;</button></div>';
            html += imgHtml;
            html += '<div class="flex gap-2 mb-4 flex-wrap">' + tipoLabel + descontoLabel + '</div>';
            html += '<p class="text-on-surface-variant mb-4 whitespace-pre-wrap">' + mensagem + '</p>';
            html += codigoHtml;
            html += '<div class="text-3xl font-black mb-4">' + preco + '</div>';
            html += '<a href="' + link + '" target="_blank" class="block w-full text-center bg-primary text-on-primary py-3 rounded-lg font-bold text-lg hover:bg-primary-dim transition">Abrir Oferta</a>';
            html += '<div id="similares-container"></div>';
            html += '<div id="relacionados-container"></div>';
            html += '<div class="text-sm text-on-surface-variant mt-4 text-center">' + data + '</div>';
            html += '</div>';
            
            var modal = document.createElement('div');
            modal.id = 'modal-overlay';
            modal.className = 'fixed inset-0 bg-black/70 flex items-center justify-center z-50 p-4';
            modal.onclick = function(e) { if(e.target === modal) closeModal(); };
            modal.innerHTML = html;
            
            document.body.appendChild(modal);
            document.body.style.overflow = 'hidden';
            
            if (oferta.produto) {
                fetch('/api/ofertas/' + id + '/similares')
                    .then(function(res) { return res.json(); })
                    .then(function(similares) {
                        if (similares.length > 0) {
                            var container = document.getElementById('similares-container');
                            var simHtml = '<div class="mt-4 pt-4 border-t border-outline"><h4 class="font-bold text-lg mb-3">Preços Similares</h4>';
                            for (var i = 0; i < similares.length; i++) {
                                var s = similares[i];
                                var sLink = s.link || '#';
                                var sCanal = s.canal || 'N/A';
                                var sPreco = s.preco ? Number(s.preco).toLocaleString('pt-BR', {minimumFractionDigits: 2}) : '-';
                                simHtml += '<a href="' + sLink + '" target="_blank" class="block p-3 bg-surface-container-low rounded-lg mb-2 hover:bg-surface-container transition"><div class="flex justify-between items-center"><span class="font-medium">' + sCanal + '</span><span class="font-bold text-primary">' + sPreco + '</span></div></a>';
                            }
                            simHtml += '</div>';
                            container.innerHTML = simHtml;
                        }
                    })
                    .catch(function() {});
                
                fetch('/api/ofertas/' + id + '/relacionados')
                    .then(function(res) { return res.json(); })
                    .then(function(relacionados) {
                        if (relacionados.length > 0) {
                            var container = document.getElementById('relacionados-container');
                            var relHtml = '<div class="mt-4 pt-4 border-t border-outline"><h4 class="font-bold text-lg mb-3">Produtos Relacionados</h4>';
                            for (var i = 0; i < relacionados.length; i++) {
                                var r = relacionados[i];
                                var rLink = r.link || '#';
                                var rCanal = r.canal || 'N/A';
                                var rPreco = r.preco ? Number(r.preco).toLocaleString('pt-BR', {minimumFractionDigits: 2}) : '-';
                                relHtml += '<a href="' + rLink + '" target="_blank" class="block p-3 bg-surface-container-low rounded-lg mb-2 hover:bg-surface-container transition"><div class="flex justify-between items-center"><span class="font-medium">' + rCanal + '</span><span class="font-bold text-tertiary">' + rPreco + '</span></div></a>';
                            }
                            relHtml += '</div>';
                            container.innerHTML = relHtml;
                        }
                    })
                    .catch(function() {});
            }
}
             
        function closeModal() {
            const modal = document.getElementById('modal-overlay');
            if (modal) {
                modal.remove();
                document.body.style.overflow = '';
            }
        }

        function copiarCodigo(btn, codigo) {
            navigator.clipboard.writeText(codigo);
            btn.textContent = 'Copiado!';
        }

        document.addEventListener('keydown', function(e) {
            if (e.key === 'Escape') closeModal();
        });

        document.getElementById('filters').addEventListener('submit', function(e) {
            e.preventDefault();
            loadOfertas();
        });

        var debounceTimer;
        var filtros = ['search', 'preco_min', 'preco_max', 'tipo', 'canal', 'periodo', 'ordenar', 'ordem'];
        for (var i = 0; i < filtros.length; i++) {
            var el = document.getElementById(filtros[i]);
            if (el) {
                el.addEventListener('input', function() {
                    clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(loadOfertas, 500);
                });
                el.addEventListener('change', function() {
                    clearTimeout(debounceTimer);
                    debounceTimer = setTimeout(loadOfertas, 500);
                });
            }
        }

        document.getElementById('tipo').addEventListener('change', function() {
            var tipo = this.value;
            if (tipo === 'cupom') {
                switchTab('cupons');
            } else if (tipo === 'oferta') {
                switchTab('ofertas');
            }
        });

        var currentTab = 'ofertas';

        function switchTab(tab) {
            currentTab = tab;
            document.getElementById('tab-ofertas').className = tab === 'ofertas' ? 'px-6 py-3 font-bold text-primary border-b-2 border-primary' : 'px-6 py-3 font-bold text-on-surface-variant hover:text-on-surface';
            document.getElementById('tab-cupons').className = tab === 'cupons' ? 'px-6 py-3 font-bold text-primary border-b-2 border-primary' : 'px-6 py-3 font-bold text-on-surface-variant hover:text-on-surface';

            var tipoSelect = document.getElementById('tipo');
            if (tab === 'cupons') {
                tipoSelect.value = 'cupom';
            } else {
                tipoSelect.value = 'oferta';
            }
            loadOfertas(true);
        }

        loadCanais();
        loadOfertas(true);
        setupInfiniteScroll();
        setupAutoRefresh();
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
    if request.args.get('canal'):
        filtros['canal'] = request.args.get('canal')
    if request.args.get('ordenar'):
        filtros['ordenar'] = request.args.get('ordenar')
    if request.args.get('ordem'):
        filtros['ordem'] = request.args.get('ordem')
    if request.args.get('limite'):
        filtros['limite'] = int(request.args.get('limite'))
    if request.args.get('periodo'):
        filtros['periodo'] = request.args.get('periodo')
    
    offset = int(request.args.get('offset', 0))
    limite = int(request.args.get('limite', 50))
    
    ofertas_raw = get_ofertas(filtros=filtros, limite=limite, offset=offset)
    ofertas = [oferta_to_dict(o) for o in ofertas_raw]
    return jsonify(ofertas)


@app.route('/api/canais')
def api_canais():
    canais = get_canais()
    return jsonify(canais)


@app.route('/api/ofertas/<int:oferta_id>/similares')
def api_ofertas_similares(oferta_id):
    from src.price_parser import buscar_produtos_similares
    
    oferta = get_ofertas({'id': oferta_id}, limite=1)
    if not oferta:
        return jsonify([])
    
    produto = oferta[0].get('produto')
    if not produto:
        return jsonify([])
    
    todas = get_todas_ofertas_com_produto()
    similares = buscar_produtos_similares(produto, todas, limite=10, threshold=30)
    
    if oferta_id:
        similares = [s for s in similares if s.get('id') != oferta_id]
    
    return jsonify([oferta_to_dict(o) for o in similares])


@app.route('/api/ofertas/<int:oferta_id>/relacionados')
def api_ofertas_relacionados(oferta_id):
    from src.price_parser import get_palavras_relacionadas
    
    oferta = get_ofertas({'id': oferta_id}, limite=1)
    if not oferta:
        return jsonify([])
    
    produto = oferta[0].get('produto')
    if not produto:
        return jsonify([])
    
    palavras = get_palavras_relacionadas(produto)
    if not palavras:
        return jsonify([])
    
    relacionados = get_ofertas_por_palavras(palavras, exclude_id=oferta_id, limite=10)
    
    return jsonify([oferta_to_dict(o) for o in relacionados])


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
