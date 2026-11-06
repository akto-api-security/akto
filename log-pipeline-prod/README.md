# Logs pipeline on one VM (Azure D4ps_v6, Ubuntu 24.04)
Runs: new logs Mongo (auth on) + 2 consumers (+ Kafka only if you use `--profile kafka`).

1. Copy this folder to the VM:  `scp -r log-pipeline-prod ubuntu@<vm-ip>:~/`
2. On the VM:  `cd ~/log-pipeline-prod && sudo bash setup-vm.sh`   (log out/in once)
3. On your Mac (builds arm64 image, ships it):  `bash log-pipeline-prod/build-and-ship-consumer.sh ubuntu@<vm-ip>`
4. On the VM, edit `.env` if you use an existing Kafka (`KAFKA_BOOTSTRAP`), then start:
   - Kafka on this VM:  `docker compose --profile kafka up -d`
   - Existing Kafka (create topics akto.logs / akto.metrics, 10 partitions, retention 6h first):  `docker compose up -d`
5. Check: `docker compose ps`, `docker compose logs -f log-consumer`.

Apps (cyborg/Ultron/dashboard) use:
- `LOG_KAFKA_BROKER_URL=<vm-private-ip>:9092`
- `LOG_MONGO_CONN=mongodb://logs_admin:<password from .env>@<vm-private-ip>:27017/?authSource=admin`

Notes: Docker published ports bypass ufw, so Mongo/Kafka are bound to the private IP only (see `.env` PRIVATE_IP) and Mongo needs the password. Plain-text Kafka, no SASL (not supported by the code yet).

## Rolling out by account
Set `LOG_PIPELINE_ACCOUNTS` on cyborg, Ultron and the dashboard (and `LOG_MONGO_CONN` on the dashboard):
- unset / empty: nobody (old behaviour). `1000000,1758595089`: only those accounts. `*`: every account.
- Use the same list everywhere, and change cyborg/Ultron and the dashboard together. Remove an account from the list to roll it back.
