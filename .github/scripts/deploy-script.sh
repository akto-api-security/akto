#! /bin/bash

cd ~/akto/infra
docker system prune -a
docker-compose -f docker-compose-dashboard.yml pull
docker-compose -f docker-compose-dashboard.yml down
docker-compose -f docker-compose-dashboard.yml up -d