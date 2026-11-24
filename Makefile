# Shortcuts for the commands you type every day. `make help` lists them.
.DEFAULT_GOAL := help
COMPOSE := docker compose
OBS     := $(COMPOSE) --profile observability

.PHONY: help build test verify up up-obs smoke demo up-db-ui console k8s-up k8s-status k8s-ui k8s-down graph ps logs down clean check-config

help: ## Show this help
	@grep -E '^[a-zA-Z0-9_-]+:.*?## ' $(MAKEFILE_LIST) | awk 'BEGIN {FS = ":.*?## "} {printf "  \033[36m%-14s\033[0m %s\n", $$1, $$2}'

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

up-db-ui: ## Start database web UIs: pgAdmin :5050, mongo-express :8091, RedisInsight :5540 (compose)
	$(COMPOSE) --profile db-ui up -d

smoke: ## End-to-end test against the running stack (register, order saga, search)
	scripts/smoke-test.sh

demo: up smoke ## Start the stack, then run the end-to-end test

K8S := --kube-context docker-desktop --namespace ecommerce

k8s-up: ## Build images, then install/upgrade the Helm chart on Docker Desktop Kubernetes (stop compose first)
	$(COMPOSE) build
	helm $(K8S) upgrade --install ecommerce deploy/helm/ecommerce --create-namespace --wait --timeout 10m

k8s-status: ## Pods and services of the Kubernetes deployment
	kubectl --context docker-desktop -n ecommerce get pods,svc

k8s-ui: ## Port-forward cluster UIs to localhost: Kafka UI :8090, pgAdmin :5050, mongo-express :8091, RedisInsight :5540
	scripts/k8s-ui.sh

k8s-down: ## Uninstall the chart (volumes survive; delete the namespace to wipe data)
	helm $(K8S) uninstall ecommerce

console: ## Open the API console at http://localhost:5500 (needs the stack running)
	python3 tools/api-console/serve.py

GRAPHIFY_VERSION := 0.9.112

graph: ## Build the local code graph in graphify-out/ (optional tool, see docs/ai/graphify.md)
	@command -v graphify >/dev/null || { \
	  echo "graphify is not installed (optional tool). Install the pinned version:"; \
	  echo "  uv tool install --python 3.13 --exclude-newer 2026-11-18T00:00:00Z graphifyy==$(GRAPHIFY_VERSION)"; \
	  exit 1; }
	@graphify --version | grep -qxF "graphify $(GRAPHIFY_VERSION)" || \
	  echo "warning: expected graphify $(GRAPHIFY_VERSION), found: $$(graphify --version)"
	GRAPHIFY_BACKEND=none GRAPHIFY_NO_TIPS=1 GRAPHIFY_NO_AUTO_REFRESH=1 graphify extract . --code-only

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
	$(COMPOSE) --profile db-ui config -q
	scripts/test-guard-hook.sh
