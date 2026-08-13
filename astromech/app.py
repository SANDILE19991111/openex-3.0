import os
from flask import Flask, jsonify, request
from flask_cors import CORS

from market_data import get_market_data, list_supported_pairs
from chat import run_chat

app = Flask(__name__)
CORS(app)  # dev-friendly; the frontend runs on a different port during local dev


@app.get("/health")
def health():
    return jsonify({"status": "ok"})


@app.get("/api/market-data/pairs")
def market_pairs():
    return jsonify({"pairs": list_supported_pairs()})


@app.get("/api/market-data/<trading_pair>")
def market_data(trading_pair: str):
    points = request.args.get("points", default=200, type=int)
    try:
        data = get_market_data(trading_pair, points)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 404
    return jsonify(data)


@app.post("/api/chat")
def chat():
    """
    Day 13: the agent can now call GET /api/wallets on the Kotlin backend
    using the caller's own JWT, so it can answer balance questions for real.

    Expects:
      Authorization: Bearer <jwt>   (forwarded to the Kotlin API by the wallet tool)
      JSON body: { "message": "...", "userId": "..." }
    """
    body = request.get_json(silent=True) or {}
    message = body.get("message")
    user_id = body.get("userId")

    if not message or not user_id:
        return jsonify({"error": "message and userId are required"}), 400

    auth_header = request.headers.get("Authorization", "")
    jwt_token = auth_header.removeprefix("Bearer ").strip()
    if not jwt_token:
        return jsonify({"error": "missing Authorization bearer token"}), 401

    try:
        reply = run_chat(message, user_id, jwt_token)
    except Exception as exc:  # Ollama not running, model missing, etc.
        return jsonify({
            "error": "The AI assistant is unavailable right now.",
            "detail": str(exc)
        }), 503

    return jsonify({"reply": reply})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    app.run(host="0.0.0.0", port=port, debug=True)
