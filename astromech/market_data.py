"""
Simulated market data feed - now genuinely LIVE. Each trading pair holds a
running price state in memory (module-level, thread-safe). Every time the
market-data or candles endpoint is called, a fresh tick is appended using a
real random-walk step, so the price actually moves between requests instead
of regenerating the same static series every time.

History is capped to a sliding window (MAX_HISTORY ticks) per pair.
"""
import threading
import time
from collections import deque
import numpy as np

# Mirrors SupportedPairs.kt on the Kotlin side.
STARTING_PRICES = {
    "BTC-USD": 65000.00,
    "ETH-USD": 3400.00,
    "SOL-USD": 165.00,
    "XRP-USD": 0.62,
    "ADA-USD": 0.45,
    "DOGE-USD": 0.14,
    "MATIC-USD": 0.78,
    "DOT-USD": 6.80,
    "LTC-USD": 85.00,
    "AVAX-USD": 36.50,
}

DRIFT = 0.00005                # tiny upward drift per tick
VOLATILITY = 0.0015            # per-tick volatility as a fraction of price
SHORT_WINDOW = 10
LONG_WINDOW = 30
DEFAULT_CANDLE_SIZE = 5
MAX_HISTORY = 300              # ticks kept per pair in the sliding window
MIN_TICK_INTERVAL_SECONDS = 1  # don't advance more than once per second per pair

_rng = np.random.default_rng()
_lock = threading.Lock()

# pair -> deque of {"timestamp", "price", "high", "low"}
_history: dict[str, deque] = {}
# pair -> last time a tick was appended, so rapid polling doesn't overshoot
_last_tick_time: dict[str, float] = {}


def _ensure_seeded(trading_pair: str) -> None:
    if trading_pair in _history:
        return
    if trading_pair not in STARTING_PRICES:
        raise ValueError(f"Unknown trading pair: {trading_pair}")

    price = STARTING_PRICES[trading_pair]
    now = time.time()
    hist = deque(maxlen=MAX_HISTORY)
    # Seed with a short backfilled history so the chart isn't empty on first load.
    for i in range(50, 0, -1):
        step = _rng.normal(loc=DRIFT, scale=VOLATILITY)
        price = max(price * (1 + step), 0.00000001)
        wobble = abs(_rng.normal(loc=0, scale=VOLATILITY * 0.5))
        hist.append({
            "timestamp": int(now) - i,
            "price": price,
            "high": price * (1 + wobble),
            "low": price * (1 - wobble),
        })
    _history[trading_pair] = hist
    _last_tick_time[trading_pair] = now


def _maybe_advance(trading_pair: str) -> None:
    """Appends a new live tick if enough real time has passed since the last one."""
    now = time.time()
    last = _last_tick_time.get(trading_pair, 0)
    if now - last < MIN_TICK_INTERVAL_SECONDS:
        return  # too soon - avoid double-advancing on rapid successive requests

    hist = _history[trading_pair]
    last_price = hist[-1]["price"] if hist else STARTING_PRICES[trading_pair]

    step = _rng.normal(loc=DRIFT, scale=VOLATILITY)
    new_price = max(last_price * (1 + step), 0.00000001)
    wobble = abs(_rng.normal(loc=0, scale=VOLATILITY * 0.5))

    hist.append({
        "timestamp": int(now),
        "price": new_price,
        "high": new_price * (1 + wobble),
        "low": new_price * (1 - wobble),
    })
    _last_tick_time[trading_pair] = now


def _sma(values: list[float], window: int) -> float:
    if not values:
        return 0.0
    slice_ = values[-window:]
    return sum(slice_) / len(slice_)


def get_market_data(trading_pair: str, points: int = 200) -> dict:
    with _lock:
        _ensure_seeded(trading_pair)
        _maybe_advance(trading_pair)
        hist = list(_history[trading_pair])[-points:]

    prices = [t["price"] for t in hist]
    ticks = []
    for i, t in enumerate(hist):
        sma_short = _sma(prices[: i + 1], SHORT_WINDOW)
        sma_long = _sma(prices[: i + 1], LONG_WINDOW)
        ticks.append({
            "timestamp": t["timestamp"],
            "price": round(t["price"], 8),
            "smaShort": round(sma_short, 8),
            "smaLong": round(sma_long, 8),
        })

    latest = ticks[-1] if ticks else {"price": STARTING_PRICES[trading_pair], "smaShort": 0, "smaLong": 0}
    return {
        "tradingPair": trading_pair,
        "currentPrice": latest["price"],
        "smaShort": latest["smaShort"],
        "smaLong": latest["smaLong"],
        "ticks": ticks,
    }


def get_candles(trading_pair: str, points: int = 200, candle_size: int = DEFAULT_CANDLE_SIZE) -> dict:
    with _lock:
        _ensure_seeded(trading_pair)
        _maybe_advance(trading_pair)
        hist = list(_history[trading_pair])[-points:]

    candles = []
    for start in range(0, len(hist), candle_size):
        chunk = hist[start:start + candle_size]
        if not chunk:
            continue
        candles.append({
            "timestamp": chunk[0]["timestamp"],
            "open": round(chunk[0]["price"], 8),
            "close": round(chunk[-1]["price"], 8),
            "high": round(max(c["high"] for c in chunk), 8),
            "low": round(min(c["low"] for c in chunk), 8),
        })

    return {"tradingPair": trading_pair, "candles": candles}


def list_supported_pairs() -> list[str]:
    return list(STARTING_PRICES.keys())
