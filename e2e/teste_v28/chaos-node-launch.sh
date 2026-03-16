#!/usr/bin/env bash
PORT1=${APP_PORT_NODE1:-8080}
PORT2=${APP_PORT_NODE2:-8081}
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

echo "Starting node1 on port $PORT1"
(cd "$PROJECT_ROOT" && SERVER_PORT=$PORT1 ./mvnw spring-boot:run) > logs/node1.log 2>&1 &
PID1=$!

echo "Starting node2 on port $PORT2"
(cd "$PROJECT_ROOT" && SERVER_PORT=$PORT2 ./mvnw spring-boot:run) > logs/node2.log 2>&1 &
PID2=$!

echo $PID1 > logs/node1.pid
echo $PID2 > logs/node2.pid
echo "Nodes started: $PID1 / $PID2"
