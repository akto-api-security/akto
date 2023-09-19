#! /bin/bash

cd ~/akto/infra
docker-compose -f docker-compose-dashboard.yml pull
docker-compose -f docker-compose-dashboard.yml down
docker-compose -f docker-compose-dashboard.yml up -d