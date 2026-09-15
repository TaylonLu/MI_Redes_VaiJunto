ifeq ($(OS),Windows_NT)
	OS_TYPE := windows
else
	OS_TYPE := linux
endif

DK := docker compose

COMPOSE := $(DK) \
	-f compose.yaml \
	-f compose.$(OS_TYPE).yaml

.PHONY: up build up-build down restart logs clean compile
.PHONY: server server-build server-rebuild
.PHONY: client client-build client-rebuild
.PHONY: docker-start docker-status

# PROJETO COMPLETO

up:
	$(COMPOSE) up

build:
	$(COMPOSE) build

compile:
	$(COMPOSE) run --rm server mvn clean package

up-build:
	$(COMPOSE) up --build

down:
	$(COMPOSE) down

restart:
	$(COMPOSE) down
	$(COMPOSE) up --build

rebuild:
	$(COMPOSE) build --no-cache

logs:
	$(COMPOSE) logs -f

clean:
	$(COMPOSE) run --rm server mvn clean


# SERVIDOR

server:
	$(COMPOSE) up server

server-build:
	$(COMPOSE) up --build server

server-rebuild:
	$(COMPOSE) build server --no-cache


# CLIENTE

client:
	$(COMPOSE) up client

client-build:
	$(COMPOSE) up --build client

client-rebuild:
	$(COMPOSE) build client --no-cache

docker-start:
	sudo systemctl start docker

docker-status:
	sudo systemctl status docker
