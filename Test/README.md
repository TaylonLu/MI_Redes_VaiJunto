# Testes automatizados VaiJunto

A suíte de testes atual foi consolidada no orquestrador Python:

```bash
python3 Test/executar_testes.py
```

Ele executa os três servidores locais com Docker Compose, realiza carga concorrente e testa falhas controladas durante PREPARE e COMMIT, incluindo recuperação e auditoria.

Consulte **[README_AUTOMACAO.md](README_AUTOMACAO.md)** para pré-requisitos, opções, cenários e detalhes da execução.

Os scripts auxiliares `simulador.py`, `auditoria.py`, `controle_falhas.py` e `agent.py` continuam disponíveis para uso isolado.
