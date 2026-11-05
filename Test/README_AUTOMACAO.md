# Testes automatizados locais — VaiJunto

A pasta contém três testes independentes. Todos usam Docker Compose e as APIs HTTP reais dos servidores Unova, Kalos e Johto. Execute os comandos a partir da raiz do repositório.

## Pré-requisitos

- Docker Engine/Desktop com Docker Compose v2.
- Python 3.10 ou superior (somente biblioteca padrão).
- Portas locais 2602, 2603 e 2604 disponíveis.

## Teste de carga

```bash
python3 Test/teste_carga.py --passageiros 50 --threads 30
```

Cria caronas e passageiros, executa buscas nos três servidores e reservas distribuídas coordenadas por Unova. Mede duração e latências p50/p95/p99.

## Teste de concorrência

```bash
python3 Test/teste_concorrencia.py --passageiros 20
```

Cria uma carona com uma vaga e inicia reservas simultâneas de passageiros distintos. O resultado esperado é uma confirmação e conflitos para os demais. A auditoria confere exclusividade dos assentos confirmados.

## Teste de falhas

```bash
python3 Test/teste_falhas.py
```

- **Após PREPARADO:** Kalos encerra o processo depois de persistir PREPARADO. O coordenador deve abortar e as vagas precisam ser liberadas.
- **Após COMITADO:** Kalos encerra o processo depois de persistir COMITADO e antes de propagar. Após reiniciar, o recuperador deve propagar a decisão aos três nós.

A injeção usa marcadores no volume compartilhado e propriedades ativadas no serviço Kalos em `compose+3.yaml`. Após o health check, o teste aguarda 3 segundos para expirar a janela temporária de suspeita do `PeerClient`.

Acrescente `--sem-build` a qualquer comando para reutilizar os containers já iniciados.

## Arquivos compartilhados

- `suporte.py`: funções HTTP, criação de usuários/carona, sincronização, testes de domínio e auditoria.
- `config.json`: endereços, containers e diretório dos dados.
- `auditoria.py`: verifica transações PREPARADO remanescentes, duplicidade de assentos e ocupações órfãs.

## Resultado de carga registrado

Execução realizada em 05/11/2026:

| Métrica | Resultado |
|---|---:|
| Passageiros | 50 |
| Threads simultâneas | 30 |
| Buscas totais | 150 (50 por servidor) |
| Reservas confirmadas | 50 |
| Reservas recusadas | 0 |
| Duração | 2,63 s |
| p50 | 1,805 s |
| p95 | 1,904 s |
| p99 | 1,918 s |

Na mesma execução, a falha após PREPARADO terminou em aborto com vagas liberadas; a falha após COMITADO foi recuperada e os três nós convergiram. A auditoria terminou com `[OK] Nenhuma inconsistência detectada.`

Esse resultado comprova apenas a execução nessa configuração e ambiente local; não define capacidade máxima nem substitui testes de produção.

## Dados

Os testes criam usuários e caronas com identificadores únicos e não apagam dados existentes. Os dados persistem em `data/2602`, `data/2603` e `data/2604`.

Para parar os containers:

```bash
docker compose -f compose+3.yaml down
```

Não remova `data/` sem backup se quiser preservar o histórico.
