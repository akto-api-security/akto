.PHONY: help build up down restart logs health smoke test-worker clean ps \
        up-legacy down-legacy health-legacy build-legacy lint typecheck setup

help:
	@echo 'Agent Guard - see README.md, ENV.md, PRODUCTION.md'
	@echo '  make setup       - One-time: sync worker-py deps + install pre-commit hook'
	@echo '  make up            - Start worker + anonymizer (:8090)'
	@echo '  make up-legacy     - Start legacy ONNX executor (:8092)'
	@echo '  make down        - Stop services'
	@echo '  make restart     - Restart services'
	@echo '  make logs        - View logs'
	@echo '  make health      - Check service health'
	@echo '  make smoke       - Run smoke tests against localhost:8090'
	@echo '  make test-worker - Run worker-py offline tests'
	@echo '  make lint        - Run ruff check + format --check on worker-py'
	@echo '  make typecheck   - Run mypy on worker-py'
	@echo '  make ps          - Show running containers'
	@echo '  make clean       - Remove containers and images'

build:
	docker compose build

up:
	docker compose up -d
	@echo "Services starting..."
	@sleep 10
	@make health

down:
	docker compose down

restart:
	docker compose restart

logs:
	docker compose logs -f

health:
	@echo "Executor (8090):"
	@curl -sf http://localhost:8090/health || echo "Not ready"
	@echo ""
	@echo "Anonymizer (8093, internal):"
	@docker compose exec -T anonymizer python -c "import urllib.request; print(urllib.request.urlopen('http://localhost:8093/health').read().decode())" 2>/dev/null || echo "Not ready"

ps:
	docker compose ps

clean:
	docker compose down --rmi local

smoke:
	./python-service/worker-py/scripts/smoke.sh http://127.0.0.1:8090

test-worker:
	cd python-service/worker-py && ./tests/run.sh

lint:
	cd python-service/worker-py && uv run ruff check . && uv run ruff format --check .

typecheck:
	cd python-service/worker-py && uv run mypy src

setup:
	cd python-service/worker-py && uv sync && uv run pre-commit install -c "$$(pwd)/.pre-commit-config.yaml"

build-legacy:
	docker compose -f docker-compose.legacy.yml build

up-legacy:
	docker compose -f docker-compose.legacy.yml up -d --build
	@sleep 15
	@make health-legacy

down-legacy:
	docker compose -f docker-compose.legacy.yml down

health-legacy:
	@echo "Legacy ONNX executor (8092):"
	@curl -sf http://localhost:8092/health || echo "Not ready"
