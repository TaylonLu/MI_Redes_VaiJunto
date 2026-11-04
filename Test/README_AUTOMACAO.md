# Testes automatizados locais — VaiJunto

Esta suíte executa os três nós do consórcio em uma única máquina, usando Docker Compose e as APIs HTTP reais do projeto.

## Pré-requisitos

- Docker Engine/Desktop com Docker Compose v2.
- Python 3.10 ou superior (somente biblioteca padrão).
- Portas locais 2602, 2603 e 2604 disponíveis.
- Executar os comandos a partir da raiz do repositório.

## Execução completa

```bash
python3 Test/executar_testes.py
```

O script:

1. Constrói e inicia Unova, Kalos e Johto.
2. Aguarda o health check dos três nós.
3. Cria motoristas, caronas e passageiros de teste com identificadores únicos.
4. Executa carga concorrente de buscas e reservas distribuídas.
5. Derruba Kalos deterministicamente após persistir `PREPARADO`, reinicia o nó e verifica o rollback.
6. Derruba Kalos deterministicamente após persistir `COMITADO`, reinicia o nó e verifica a propagação do commit.
7. Executa a auditoria de consistência existente.

Os dois pontos de falha são ativados por arquivos-marcadores no volume compartilhado. O próprio servidor remove o marcador e encerra o processo; o orquestrador detecta a parada e inicia o contêiner novamente. Não há dependência de `sleep` para acertar a janela da requisição.

## Opções

```bash
# Somente carga
python3 Test/executar_testes.py --somente-carga

# Somente falhas e recuperação
python3 Test/executar_testes.py --somente-falhas

# Ajustar carga
python3 Test/executar_testes.py --passageiros 40 --threads 12

# Reutilizar os containers já iniciados, sem rebuild/recreate
python3 Test/executar_testes.py --sem-build
```

## Testes de falha

### 1. Falha durante PREPARE

Kalos persiste a reserva local em `PREPARADO` e encerra o processo antes de encaminhar o restante da cadeia. Unova deve abortar a transação. Após a recuperação, Kalos consulta o coordenador, identifica a decisão de aborto e libera a vaga. O teste verifica que não existe transação confirmada para o passageiro e que as vagas locais foram liberadas.

### 2. Falha durante COMMIT

Kalos persiste `COMITADO` e encerra o processo antes de propagar a decisão para Johto. Unova pode responder com a confirmação local e indicar propagação pendente. Após a recuperação, os recuperadores de Unova e Kalos repetem a propagação. O teste aguarda os três registros `COMITADO` e verifica a ocupação em cada servidor.

A injeção é exclusiva do serviço Kalos no arquivo `compose+3.yaml`. Sem os marcadores, as propriedades não provocam falha.

## Dados e limpeza

Os testes criam novos usuários e caronas com UUIDs e **não apagam os dados existentes**. Os arquivos persistem em `data/2602`, `data/2603` e `data/2604`, conforme o volume do Compose. Para remover o ambiente Docker:

```bash
docker compose -f compose+3.yaml down
```

Não remova `data/` se quiser preservar o histórico. Para começar com bases vazias, faça backup e limpe os arquivos de dados conscientemente antes de executar.

## Limitações importantes

- O teste de carga cria um itinerário distribuído Unova → Kalos → Johto. Unova coordena essas reservas e os outros dois servidores participam do 2PC.
- A falha é injetada no limite entre persistência local e próximo repasse, não por atraso artificial de rede.
- A auditoria final também examina o histórico existente em `data/`. Uma transação antiga pendente pode aparecer no relatório global, mesmo que os cenários recém-executados tenham passado.
- A suíte verifica a consistência observável pelos arquivos persistidos e pelas respostas HTTP; ela não substitui testes unitários de cada método.
