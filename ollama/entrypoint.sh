#!/bin/bash
set -e

# Start the Ollama server in the background.
ollama serve &
SERVE_PID=$!

# Wait until the server is actually accepting requests before pulling —
# pulling too early just fails with a connection error.
echo "Waiting for Ollama server to start..."
until ollama list >/dev/null 2>&1; do
  sleep 1
done
echo "Ollama server is up."

# Pull the configured model if it isn't already present (avoids re-pulling
# ~1.3GB on every container restart if a persistent disk is attached).
MODEL="${OLLAMA_MODEL:-llama3.2:1b}"
if ! ollama list | grep -q "$MODEL"; then
  echo "Pulling model: $MODEL"
  ollama pull "$MODEL"
else
  echo "Model $MODEL already present, skipping pull."
fi

# Keep the container alive by waiting on the server process.
wait $SERVE_PID
