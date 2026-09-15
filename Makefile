# Detecta o sistema operacional
ifeq ($(OS),Windows_NT)
	OS_TYPE := windows
else
	OS_TYPE := linux
endif

DK := docker compose

COMPOSE := $(DK) \
		-f compose.yaml \
		-f compose.$(OS_TYPE).yaml

.PHONY: up up-build down restart logs build server client clean package

# PROJETO COMPLETO
up:
	$(COMPOSE) up
build:
	$(COMPOSE) build
up-build:
	$(COMPOSE) up --build
down:
	$(COMPOSE) down
restart:
	$(COMPOSE) down
	$(COMPOSE) up --build
rebuild:
	$(DK) build --no-cache
logs:
	$(COMPOSE) logs -f

# MAVEN (Atualizado para lidar com a raiz)
clean:
	mvn clean
package:
	mvn clean package -U

# SERVIDOR
server:
	$(COMPOSE) up server
server-build:
	$(COMPOSE) up --build server
server-rebuild:
	$(DK) build server --no-cache

# CLIENTE
client:
	$(COMPOSE) up client
client-build:
	$(COMPOSE) up --build client
client-rebuild:
	$(DK) build client --no-cache