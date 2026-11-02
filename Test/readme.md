# Testes locais de carga e consistência — VaiJunto

Ferramentas externas, sem interface gráfica e sem dependências Python de terceiros.
Compatíveis com a API HTTP da branch `parte_2` / `preparacao-testes-3-servidores`.

## 1. Antes de começar

1. Use a configuração de três servidores preparada anteriormente (`unova`, `kalos`, `johto`).
2. Inicie o ambiente na raiz do projeto:
   ```bash
   docker compose up --build -d
   ```
3. Faça backup da pasta `data/`. O teste cria contas e reservas reais nos arquivos persistidos.
4. Confirme que os três servidores respondem:
    - `http://localhost:2602/api/consorcio/health`
    - `http://localhost:2603/api/consorcio/health`
    - `http://localhost:2604/api/consorcio/health`

## 2. Preparar dados de teste

O simulador cria automaticamente contas de passageiros. Ele **não cria caronas**: para exercitar reservas é necessário ter caronas publicadas e abertas.

Publique caronas de teste com vagas limitadas nos três servidores. Para exercitar o 2PC distribuído e a injeção de falha em Kalos, monte ao menos um itinerário que atravesse Unova → Kalos → Johto (ou Kalos → Johto). As cidades de origem/destino abaixo são apenas valores iniciais; confirme que as caronas publicadas formam esses caminhos no seu `cidades.json` e na configuração regional.

Edite `config.json`:
- `servers`: nome lógico, ID (o `id` devolvido no campo `servidor` da busca) e URL de cada servidor.
- `routes`: uma origem/destino pesquisável a partir de cada servidor.
- `docker.container_to_kill`: nome do container Kalos. No Compose preparado anteriormente é `vai-junto-kalos`.

**Importante:** `id` deve corresponder ao valor `servidor` devolvido em cada trecho do itinerário. Nesta configuração é `localhost:2602`, `localhost:2603` e `localhost:2604`, conforme `Regiao.id()`.

## 3. Simulador autônomo de passageiros

Na pasta deste pacote:

```bash
python simulador_passageiros.py --passageiros 30 --iteracoes 10
```

Isso cria uma conta de passageiro por thread em cada servidor e, em paralelo, executa buscas e reservas. O número máximo de operações de reserva é `passageiros × iterações × 3 servidores`; reservas sem itinerário disponível são contabilizadas separadamente.

Para aumentar a carga:

```bash
python simulador_passageiros.py --passageiros 80 --iteracoes 20 --delay 0.01
```

O resultado fica em `resultado-carga.json`. `reservations_ok` conta confirmações HTTP 201; `reservations_failed` conta recusas e erros de reserva; `no_route` conta buscas sem itinerário.

## 4. Teste de consistência e recuperação

Primeiro rode sem falha para testar concorrência normal:

```bash
python teste_consistencia.py --passageiros 24 --iteracoes 8
```

Depois rode com falha abrupta de Kalos:

```bash
python teste_consistencia.py --passageiros 24 --iteracoes 8 --falha
```

O modo `--falha` envia SIGKILL ao container `vai-junto-kalos` durante a carga e o inicia novamente cinco segundos depois. O recuperador do projeto roda periodicamente; por isso o teste aguarda a janela configurada antes de auditar os dados.

Se o nome do container for diferente, ajuste `docker.container_to_kill` em `config.json`.

### O que é verificado

- Nenhuma transação de teste permanece `PREPARADO` após a janela de recuperação.
- Um mesmo assento da mesma perna não aparece em duas transações confirmadas distintas.
- Uma transação `COMITADO` no coordenador também deve estar `COMITADO` em cada servidor listado no itinerário.
- Ocupações criadas pelos passageiros do teste precisam ter uma transação local `COMITADO` correspondente; ocupação sem transação confirmada é reportada como assento órfão.

Relatório: `resultado-consistencia.json`. Código de saída `0` significa que nenhuma violação foi detectada; `1` significa falha na auditoria.

## 5. Limitações importantes

- A injeção de falha ocorre durante a carga, não em um ponto determinístico entre `PREPARE` e `COMMIT`. Portanto, uma execução pode não atingir a janela crítica; repita algumas vezes.
- O teste audita os arquivos persistidos montados em `data/<porta>/`. Execute-o no host, na pasta do projeto, e não dentro de um container isolado.
- O teste não apaga contas, caronas ou reservas. Use uma cópia/ambiente de teste dos dados.
- O teste valida os invariantes observáveis nos arquivos da aplicação; não é uma prova formal do protocolo 2PC.
- A configuração inicial de cidades é ilustrativa. Se a busca retornar `no_route`, ajuste `routes` para pares com caronas realmente abertas.

## 6. Encerrar

```bash
docker compose down
```

Os dados persistidos permanecem em `data/`. Para recomeçar do zero, restaure o backup de teste em vez de apagar dados de produção.
