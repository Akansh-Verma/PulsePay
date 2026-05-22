#!/bin/bash

# PulsePay — Kafka topic creation via Docker exec
# Requires: docker compose -f docker-compose.infra.yml up -d
# Usage: bash scripts/create-topics-docker.sh

CONTAINER="kafka"

echo "Creating PulsePay Kafka topics via Docker..."

topics=(
  "fraud.alerts:3:604800000"
  "notification.requests:3:86400000"
  "audit.events:3:2592000000"
  "payment.events.dlq:3:2592000000"
  "wallet.events.dlq:3:2592000000"
  "fraud.alerts.dlq:1:2592000000"
  "notification.requests.dlq:2:2592000000"
  "audit.events.dlq:1:2592000000"
)

for entry in "${topics[@]}"; do
  IFS=':' read -r topic partitions retention <<< "$entry"
  echo "Creating: $topic (partitions=$partitions, retention=${retention}ms)"
  docker exec $CONTAINER kafka-topics.sh \
    --bootstrap-server localhost:9092 \
    --create \
    --topic "$topic" \
    --partitions "$partitions" \
    --replication-factor 1 \
    --config retention.ms="$retention" \
    --config max.message.bytes=2097152 \
    --if-not-exists
done

echo ""
echo "All topics:"
docker exec $CONTAINER kafka-topics.sh \
  --bootstrap-server localhost:9092 --list \
  | grep -E "payment|wallet|fraud|notification|audit"
