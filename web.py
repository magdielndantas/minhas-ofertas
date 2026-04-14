from datetime import datetime
import os
from flask import Flask, render_template_string, jsonify, send_from_directory
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
        'canal': oferta.get('canal', 'N/A'),
        'preco': oferta.get('preco'),
        'link': oferta.get('link', '#'),
        'mensagem': oferta.get('mensagem', ''),
        'imagem': imagem,
        'data': oferta.get('data', ''),
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

        <div class="flex-1 space-y-12">
            <section>
                <h1 class="text-5xl font-black text-on-surface tracking-tighter mb-2">Minhas Ofertas</h1>
                <p class="text-on-surface-variant max-w-lg">High-velocity tracking para os melhores precos.</p>
            </section>

            <div class="grid grid-cols-1 lg:grid-cols-3 gap-6">
                <div class="bg-primary-container rounded-lg p-6 flex items-center gap-4">
                    <span class="material-symbols-outlined text-on-primary-container text-4xl">local_offer</span>
                    <div>
                        <p class="text-on-primary-container text-sm font-medium">Total de Ofertas</p>
                        <p class="text-on-primary-container text-3xl font-black">{{ ofertas|length }}</p>
                    </div>
                </div>
                <div class="bg-surface-container-low rounded-lg p-6 flex items-center gap-4">
                    <span class="material-symbols-outlined text-primary text-4xl">schedule</span>
                    <div>
                        <p class="text-on-surface-variant text-sm font-medium">Busca mais recente</p>
                        <p class="text-on-surface text-xl font-bold">{{ ultima_atualizacao }}</p>
                    </div>
                </div>
                <div class="bg-tertiary-container rounded-lg p-6 flex items-center gap-4">
                    <span class="material-symbols-outlined text-on-tertiary-container text-4xl">filter_list</span>
                    <div>
                        <p class="text-on-tertiary-container text-sm font-medium">Filtros ativos</p>
                        <p class="text-on-tertiary-container text-xl font-bold">Perifericos</p>
                    </div>
                </div>
            </div>

            <section class="space-y-6">
                <div class="flex justify-between items-end">
                    <h2 class="text-3xl font-black tracking-tight text-on-background">Resultados</h2>
                    <span class="text-sm font-bold text-primary">{{ ofertas|length }} Oferta(s)</span>
                </div>
                <div class="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
                    {% for oferta in ofertas %}
                    {% set preco = "R$ %.2f"|format(oferta.preco) if oferta.preco else "Sem preco" %}
                    {% set mensagem = oferta.mensagem[:150] + '...' if oferta.mensagem and oferta.mensagem|length > 150 else oferta.mensagem or '' %}
                    <div class="bg-surface-container-low rounded-lg p-1 overflow-hidden group">
                        <div class="relative h-64 overflow-hidden rounded-lg bg-surface-container flex items-center justify-center">
                            {% if oferta.imagem %}
                            <img class="w-full h-full object-cover group-hover:scale-110 transition-transform duration-500" src="{{ oferta.imagem }}" />
                            {% else %}
                            <span class="material-symbols-outlined text-[#834c4c] text-4xl">shopping_cart</span>
                            {% endif %}
                        </div>
                        <div class="p-6">
                            <h4 class="font-bold text-xl mb-1">{{ oferta.canal }}</h4>
                            <p class="text-sm text-on-surface-variant mb-4">{{ mensagem }}</p>
                            <div class="flex items-center justify-between">
                                <span class="text-2xl font-black text-on-background">{{ preco }}</span>
                                <a href="{{ oferta.link or '#' }}" target="_blank"
                                    class="text-primary font-bold text-sm flex items-center gap-1 hover:gap-2 transition-all">
                                    Ver Oferta <span class="material-symbols-outlined text-sm">open_in_new</span>
                                </a>
                            </div>
                        </div>
                    </div>
                    {% endfor %}
                </div>
            </section>
        </div>
    </main>
    <footer class="bg-[#ffedec] mt-20">
        <div class="flex flex-col md:flex-row justify-between items-center px-12 py-16 w-full max-w-[1920px] mx-auto gap-8">
            <div class="flex flex-col items-center md:items-start">
                <span class="text-2xl font-black text-[#af2700] mb-2 italic">DEAL.EDIT</span>
                <p class="text-sm text-[#834c4c]">Gerado automaticamente via Monitoramento</p>
            </div>
        </div>
    </footer>
</body>

</html>'''


@app.route('/')
def index():
    ofertas_raw = get_ofertas(limite=200)
    ofertas = [oferta_to_dict(o) for o in ofertas_raw]
    ultima_atualizacao = datetime.now().strftime('%d/%m/%Y %H:%M')
    return render_template_string(HTML_TEMPLATE, ofertas=ofertas, ultima_atualizacao=ultima_atualizacao)


@app.route('/api/ofertas')
def api_ofertas():
    ofertas_raw = get_ofertas(limite=200)
    ofertas = [oferta_to_dict(o) for o in ofertas_raw]
    return jsonify(ofertas)


if __name__ == '__main__':
    app.run(debug=True, host='0.0.0.0', port=5000)
