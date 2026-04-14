# Minhas Ofertas

Script Python para monitorar ofertas de canais do Telegram e enviar notificações quando encontrar produtos de interesse (periféricos, hardware gamer, etc).

## Recursos

- Monitoramento em tempo real de múltiplos canais do Telegram
- Filtragem por palavras-chave personalizáveis
- Extração automática de preços (múltiplos formatos brasileiros)
- Banco de dados SQLite para persistência
- Interface HTML para visualização das ofertas
- CLI completo com múltiplos comandos

## Instalação

```bash
# Clone o repositório
git clone https://github.com/seu-usuario/minhas-ofertas.git
cd minhas-ofertas

# Crie um ambiente virtual (opcional)
python -m venv venv
source venv/bin/activate  # Linux/Mac
# ou
venv\Scripts\activate  # Windows

# Instale as dependências
pip install -r requirements.txt
```

## Configuração

### 1. Credenciais do Telegram

1. Acesse https://my.telegram.org/apps
2. Crie um novo aplicativo
3. Copie o `API_ID` e `API_HASH`
4. Crie um arquivo `.env` na raiz do projeto:

```bash
# .env
TELEGRAM_API_ID=12345678
TELEGRAM_API_HASH=abcdef1234567890abcdef1234567890
```

### 2. Configuração de Canais e Palavras

Edite o arquivo `data/config.json` para customizar:

```json
{
    "canais": ["canal1", "canal2"],
    "palavras_chave": ["mouse", "teclado", "monitor"],
    "termos_ignorar": ["usado", "seminovo"],
    "preco_maximo": null,
    "rate_limit": {
        "max_por_minuto": 20,
        "delay_segundos": 2
    }
}
```

## Uso

```bash
# Monitoramento em tempo real
python -m src.app

# Buscar histórico (últimas mensagens dos canais)
python -m src.app --history

# Gerar visualização HTML das ofertas salvas
python -m src.app --view

# Ver estatísticas do banco
python -m src.app --estats

# Listar canais configurados
python -m src.app --list

# Testar extração de preço
python -m src.app --test "R$ 1.299,90"

# Menu interativo de configuração
python -m src.app --config
```

### Comandos Disponíveis

| Comando | Descrição |
|---------|-----------|
| (sem args) | Iniciar monitoramento em tempo real |
| `--history` | Buscar mensagens históricas |
| `--view` | Gerar HTML de visualização |
| `--estats` | Mostrar estatísticas do banco |
| `--list` | Listar canais e palavras-chave |
| `--test TEXTO` | Testar regex de preço |
| `--debug-preco TEXTO` | Debug detalhado da extração |
| `--config` | Menu interativo de configuração |
| `--dry-run` | Testar sem enviar notificações |
| `--save-config ARQUIVO` | Salvar config padrão |
| `-v, --verbose` | Modo verboso |

## Estrutura do Projeto

```
minhas-ofertas/
├── src/                    # Código fonte
│   ├── __init__.py
│   ├── app.py            # Main
│   ├── config.py         # Configurações
│   ├── database.py       # Banco SQLite
│   ├── price_parser.py   # Regex de preços
│   └── telegram.py       # Cliente Telegram
├── data/                  # Dados (não versionado)
│   ├── config.json       # Config do usuário
│   ├── ofertas.db        # Banco SQLite
│   └── imagens/          # Imagens das ofertas
├── logs/                  # Logs (não versionado)
├── output/                # HTML gerado (não versionado)
├── requirements.txt
├── .env.example
├── .gitignore
└── README.md
```

## Banco de Dados

O projeto usa SQLite (`data/ofertas.db`) para armazenar as ofertas. O banco é criado automaticamente na primeira execução.

### Estrutura da Tabela

```sql
CREATE TABLE ofertas (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    canal TEXT NOT NULL,
    preco REAL,
    link TEXT,
    data TEXT,
    mensagem TEXT,
    imagem TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Contribuição

1. Fork o projeto
2. Crie uma branch (`git checkout -b feature/nova-feature`)
3. Commit suas mudanças (`git commit -m 'Add nova feature'`)
4. Push para a branch (`git push origin feature/nova-feature`)
5. Crie um Pull Request

## Licença

MIT License - sinta-se livre para usar e modificar!

## Aviso

Este script é para uso pessoal. O uso indevido de automação no Telegram pode violar os Termos de Serviço da plataforma. Use com responsabilidade.