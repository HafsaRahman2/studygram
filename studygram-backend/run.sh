#!/usr/bin/env bash
#
# Start the StudyGram backend with the settings from .env
#
#   ./run.sh
#
# Copy .env.example to .env first if you haven't already.

set -euo pipefail
cd "$(dirname "$0")"

if [ -f .env ]; then
  # 'set -a' makes every variable defined until 'set +a' an exported
  # environment variable, which is what Spring reads at startup.
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
  echo "Loaded configuration from .env"
else
  echo "No .env found. Copy .env.example to .env and fill it in."
  echo "Starting with defaults..."
fi

exec ./mvnw spring-boot:run
