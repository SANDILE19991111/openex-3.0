FROM ollama/ollama:latest

# Listen on all interfaces so other services (Render's internal network) can reach it.
ENV OLLAMA_HOST=0.0.0.0:11434
ENV OLLAMA_MODEL=llama3.2:1b

COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

EXPOSE 11434
ENTRYPOINT ["/entrypoint.sh"]
