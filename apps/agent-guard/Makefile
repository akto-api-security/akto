.PHONY: help build up down restart logs health clean ps

help:
	@echo 'Agent Guard - Available commands:'
	@echo '  make build    - Build Docker images'
	@echo '  make up       - Start services'
	@echo '  make down     - Stop services'
	@echo '  make restart  - Restart services'
	@echo '  make logs     - View logs'
	@echo '  make health   - Check health'
	@echo '  make ps       - Show running containers'
	@echo '  make clean    - Remove all containers and images'

build:
	docker-compose build

up:
	docker-compose up -d
	@echo "Services starting..."
	@sleep 10
	@make health

down:
	docker-compose down

restart:
	docker-compose restart

logs:
	docker-compose logs -f

health:
	@echo "Go Service (8091):"
	@curl -sf http://localhost:8091/health || echo "Not ready"
	@echo ""
	@echo "Python Service (8092):"
	@curl -sf http://localhost:8092/health || echo "Not ready"

ps:
	docker-compose ps

clean:
	docker-compose down -v --rmi all

test:
	@curl -s -X POST http://localhost:8091/scan \
		-H "Content-Type: application/json" \
		-d '{"scanner_type":"prompt","scanner_name":"Secrets","text":"test"}' | jq '.'

