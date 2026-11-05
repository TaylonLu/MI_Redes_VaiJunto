# Testes automatizados — VaiJunto

A pasta contém três testes independentes. Execute os comandos na raiz do repositório:

| Teste | Comando | Objetivo |
|---|---|---|
| Carga | `python3 Test/teste_carga.py --passageiros 50 --threads 30` | Medir buscas e reservas distribuídas sob volume. |
| Concorrência | `python3 Test/teste_concorrencia.py --passageiros 20` | Fazer passageiros disputarem simultaneamente uma vaga e verificar exclusividade. |
| Falhas | `python3 Test/teste_falhas.py` | Injetar quedas após PREPARADO e COMITADO e verificar recuperação. |

Acrescente `--sem-build` para reutilizar containers já iniciados.

Os executáveis compartilham funções em `suporte.py`, configurações em `config.json` e auditoria em `auditoria.py`. A injeção de falhas depende das propriedades habilitadas para Kalos em `compose+3.yaml`.

Consulte [README_AUTOMACAO.md](README_AUTOMACAO.md) para pré-requisitos, cenários e resultados de carga registrados.
