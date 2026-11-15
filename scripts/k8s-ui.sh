#!/usr/bin/env bash
# Port-forwards the developer UIs of the Kubernetes deployment to 127.0.0.1 (make k8s-ui).
# They have no login, so they are ClusterIP Services and are never published on a LoadBalancer:
# Docker Desktop binds LoadBalancer ports on all network interfaces, which would expose them to the LAN.
set -euo pipefail

CONTEXT=${KUBE_CONTEXT:-docker-desktop}
NAMESPACE=${NAMESPACE:-ecommerce}
UIS=("kafka-ui:8090" "pgadmin:5050" "mongo-express:8091" "redisinsight:5540")

# Ctrl+C stops all of them.
trap 'kill $(jobs -p) 2>/dev/null' EXIT

for ui in "${UIS[@]}"; do
  svc=${ui%%:*} port=${ui##*:}
  kubectl --context "$CONTEXT" -n "$NAMESPACE" port-forward --address 127.0.0.1 "svc/$svc" "$port:$port" >/dev/null &
done

cat <<EOF
Kafka UI       http://localhost:8090
pgAdmin        http://localhost:5050
mongo-express  http://localhost:8091
RedisInsight   http://localhost:5540
Ctrl+C stops the port-forwards.
EOF
# A forward ends when its pod is replaced (e.g. after helm upgrade): run make k8s-ui again then.
wait
