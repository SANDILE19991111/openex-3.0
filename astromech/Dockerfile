FROM python:3.11-slim
WORKDIR /app
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt
COPY . .
# Render sets $PORT at runtime; gunicorn binds to it via the shell form CMD.
CMD gunicorn --bind 0.0.0.0:${PORT:-5001} --workers 2 --timeout 120 app:app
