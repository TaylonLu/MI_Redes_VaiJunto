ifeq ($(OS),Windows_NT)
	OS_TYPE := windows
else
	OS_TYPE := linux
endif

# Ignora o compose.$(OS_TYPE).yaml se ele não existir mais
COMPOSE := docker compose -f compose.yaml

.PHONY: up build down logs clean compile
.PHONY: unova kalos johto kanto

# REDE COMPLETA (Sobe todos os servidores P2P juntos)
up:
	$(COMPOSE) up

build:
	$(COMPOSE) build

down:
	$(COMPOSE) down

logs:
	$(COMPOSE) logs -f

clean:
	$(COMPOSE) run --rm unova mvn clean

compile:
	$(COMPOSE) run --rm unova mvn clean package -DskipTests

# ATALHOS PARA NÓS INDIVIDUAIS
unova:
	$(COMPOSE) up unova

kalos:
	$(COMPOSE) up kalos

johto:
	$(COMPOSE) up johto

kanto:
	$(COMPOSE) up kanto