# Simulador de passageiros — VaiJunto

Projeto Maven independente para gerar buscas e reservas HTTP sem interface gráfica.

## Pré-requisitos

- Java 24
- Maven
- Os quatro servidores em execução nas portas 2602, 2603, 2604 e 2605.

## Executar

Na raiz do repositório:

```bash
mvn -f Test/simulador/pom.xml clean compile exec:java
```

Configuração personalizada:

```bash
mvn -f Test/simulador/pom.xml clean compile exec:java \
  -Dexec.args="--passageiros 1000 --threads 100 --taxa-reserva 0.8"
```

Ver opções:

```bash
mvn -f Test/simulador/pom.xml exec:java -Dexec.args="--ajuda"
```

## O que o simulador faz

1. Verifica a disponibilidade dos quatro servidores.
2. Consulta as cidades disponibilizadas por cada servidor.
3. Cadastra um motorista por empresa.
4. Cria uma carona em cada empresa, formando um ciclo de itinerários.
5. Cadastra passageiros distribuídos entre as quatro empresas.
6. Executa buscas simultâneas.
7. Seleciona apenas itinerários que envolvam pelo menos três servidores.
8. Uma fração configurável dos passageiros tenta reservar o itinerário encontrado.
9. Apresenta contagens de buscas, reservas, rejeições, erros e latências p50/p95/p99.

## Topologia usada

As caronas de teste formam este ciclo de fronteiras:

- Unova: Castelia (101) até Mistralton City (108).
- Kalos: Mistralton City (108) até Venivile Town (208).
- Johto: Venivile Town (208) até Lavender Town (406).
- Kanto: Lavender Town (406) até Cerulean City (405) e Castelia (101).

As buscas são iniciadas em cada empresa com destinos escolhidos para exigir pelo menos três participantes.

## Observações

- O simulador usa somente as APIs HTTP públicas do projeto.
- Os dados criados permanecem nos servidores após a execução. Cada execução usa emails novos.
- A preparação dos dados é sequencial para evitar concorrência desnecessária durante a escrita dos cadastros.
- A fase de carga executa uma tarefa por passageiro, limitada pelo número de threads.
- Este primeiro estágio não derruba servidores. A injeção de falhas será implementada nos testes automatizados de concorrência/recuperação.
