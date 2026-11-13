# Shortcuts for the commands you type every day. `make help` lists them.
.DEFAULT_GOAL := help
COMPOSE := docker compose
OBS     := $(COMPOSE) --profile observability

.PHONY: help build test verify up up-obs smoke demo ps logs down clean check-config

help: ## Show this help
	@grep -E '^[a-zA-Z_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "} {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

build: ## Compile and package every module (no tests)
	./mvnw -q -DskipTests package

test: ## Unit tests (no Docker needed)
	./mvnw test

verify: ## Unit tests + Testcontainers integration tests (needs Docker)
	./mvnw verify

up: ## Build images and start the core stack
	$(COMPOSE) up -d --build

up-obs: ## Start the core stack plus Kibana, Prometheus, Grafana and Alertmanager
	$(OBS) up -d --build

smoke: ## End-to-end test against the running stack (register, order saga, search)
	scripts/smoke-test.sh

demo: up smoke ## Start the stack, then run the end-to-end test

ps: ## Show container status
	$(OBS) ps

logs: ## Follow the logs of one service: make logs s=order-service
	$(COMPOSE) logs -f --tail 100 $(s)

down: ## Stop the stack (data volumes are kept)
	$(OBS) down --remove-orphans

clean: ## Stop the stack and DELETE all data volumes
	$(OBS) down -v --remove-orphans

check-config: ## Validate compose files and the Claude Code guard hook
	$(COMPOSE) config -q
	$(OBS) --profile search-tools config -q
	scripts/test-guard-hook.sh
