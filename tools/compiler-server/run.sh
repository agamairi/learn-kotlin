#!/usr/bin/env bash
# (Re)start the kotlin-compiler-server container used by learn-kotlin (Phase 0 spike and dev runner).
#
# Usage:
#   run.sh             recreate the container from the existing image (builds the image if it is missing)
#   run.sh --rebuild   rebuild the image from the clone's build/ output first, then recreate the container
#
# Flags: --name kotlin-compiler-server --restart unless-stopped --memory=2g --memory-swap=2g
#        -p 127.0.0.1:8080:8080 (loopback only; the Android emulator reaches it at 10.0.2.2)
# See README.md in this folder for prerequisites and security notes.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CLONE="${KCS_CLONE:?Set KCS_CLONE to the path of your kotlin-compiler-server clone}"
KOTLIN_VERSION="2.5.0-Beta2"
IMAGE="kotlin-compiler-server:${KOTLIN_VERSION}-host"
NAME="kotlin-compiler-server"
HOST_PORT="${KCS_PORT:-8080}"

log() { printf '%s\n' "$*" >&2; }

command -v docker >/dev/null 2>&1 || { log "docker is not installed or not on PATH"; exit 1; }
docker info >/dev/null 2>&1 || { log "Docker is not running; start Docker Desktop first"; exit 1; }

build_image() {
  local jar="$CLONE/build/libs/kotlin-compiler-server-${KOTLIN_VERSION}-SNAPSHOT.jar"
  if [[ ! -f "$jar" ]]; then
    log "Missing $jar. Build the clone on the host first (see README.md)."
    exit 1
  fi
  # Stage the needed files with hard links so the Docker build context stays small.
  local ctx
  ctx="$(mktemp -d)"
  trap "rm -rf '$ctx'" EXIT
  mkdir -p "$ctx/build/libs"
  cp -al "$jar" "$ctx/build/libs/"
  local dir
  for dir in "$KOTLIN_VERSION" "$KOTLIN_VERSION-js" "$KOTLIN_VERSION-wasm" \
             "$KOTLIN_VERSION-compose-wasm" "$KOTLIN_VERSION-compose-wasm-compiler-plugins" \
             "$KOTLIN_VERSION-compiler-plugins"; do
    if [[ ! -d "$CLONE/$dir" ]]; then
      log "Missing library folder $CLONE/$dir (host build incomplete)."
      exit 1
    fi
    cp -al "$CLONE/$dir" "$ctx/"
  done
  cp "$HERE/Dockerfile" "$ctx/Dockerfile"
  log "Building image $IMAGE from $CLONE ..."
  docker build -t "$IMAGE" "$ctx" >&2
}

if [[ "${1:-}" == "--rebuild" ]] || ! docker image inspect "$IMAGE" >/dev/null 2>&1; then
  build_image
fi

# Recreate the container so the flags always match this script.
docker rm -f "$NAME" >/dev/null 2>&1 || true

docker run -d \
  --name "$NAME" \
  --restart unless-stopped \
  --memory=2g \
  --memory-swap=2g \
  -p "127.0.0.1:${HOST_PORT}:8080" \
  "$IMAGE" >/dev/null

for _ in $(seq 1 60); do
  if curl -sf -m 2 "http://127.0.0.1:${HOST_PORT}/health" >/dev/null 2>&1; then
    versions="$(curl -s -m 2 "http://127.0.0.1:${HOST_PORT}/versions" || true)"
    log "kotlin-compiler-server is healthy at http://127.0.0.1:${HOST_PORT} ${versions}"
    exit 0
  fi
  sleep 1
done

log "kotlin-compiler-server did not become healthy within 60 s. Last log lines:"
docker logs --tail 40 "$NAME" >&2 || true
exit 1
