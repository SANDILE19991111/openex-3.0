import os
from flask import Flask, jsonify, request
from flask_cors import CORS

from market_data import get_market_data, get_candles, list_supported_pairs, list_timeframes
from chat import run_chat

app = Flask(__name__)

# Comma-separated list of allowed frontend origins. In production, set
# FRONTEND_ORIGINS to the deployed frontend's real URL. Defaults to the
# local Vite dev server so nothing extra needs configuring for local dev.
allowed_origins = os.environ.get("FRONTEND_ORIGINS", "http://localhost:5173").split(",")
CORS(app, origins=[o.strip() for o in allowed_origins])


@app.get("/health")
def health():
    return jsonify({"status": "ok"})


@app.get("/api/market-data/pairs")
def market_pairs():
    return jsonify({"pairs": list_supported_pairs()})


@app.get("/api/market-data/timeframes")
def market_timeframes():
    return jsonify({"timeframes": list_timeframes()})


@app.get("/api/market-data/<trading_pair>")
def market_data(trading_pair: str):
    points = request.args.get("points", default=200, type=int)
    try:
        data = get_market_data(trading_pair, points)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 404
    return jsonify(data)


@app.get("/api/market-data/<trading_pair>/candles")
def market_candles(trading_pair: str):
    timeframe = request.args.get("timeframe", default="1m", type=str)
    points = request.args.get("points", default=200, type=int)
    try:
        data = get_candles(trading_pair, timeframe, points)
    except ValueError as exc:
        return jsonify({"error": str(exc)}), 404
    return jsonify(data)


@app.post("/api/chat")
def chat():
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
    except Exception as exc:
        return jsonify({
            "error": "The AI assistant is unavailable right now.",
            "detail": str(exc)
        }), 503

    return jsonify({"reply": reply})


if __name__ == "__main__":
    port = int(os.environ.get("PORT", 5001))
    debug_mode = os.environ.get("FLASK_DEBUG", "false").lower() == "true"
    app.run(host="0.0.0.0", port=port, debug=debug_mode)
