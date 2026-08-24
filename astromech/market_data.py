"""
Live-advancing simulated market data, now with selectable timeframes
(1m, 2h, 3h, 4h, 1D, 1M, 1Y) for the candlestick chart.

Each trading pair keeps a live price state in memory (module-level, guarded
by a lock). Every time data is requested, elapsed real time since the last
request is used to advance the price with a fresh random-walk step, so the
price genuinely moves between requests instead of being a static series.

For candles, historical bars are generated deterministically (seeded per
pair+timeframe) so repeated requests for the same timeframe return a stable
history - EXCEPT the final, still-forming candle, which is built from the
live price state so it visibly moves as time passes.
"""
import hashlib
import threading
import time
import numpy as np

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

TIMEFRAMES = {
    "1m": 60,
    "2h": 2 * 3600,
    "3h": 3 * 3600,
    "4h": 4 * 3600,
    "1d": 24 * 3600,
    "1mo": 30 * 24 * 3600,
    "1y": 365 * 24 * 3600,
}

DRIFT = 0.0001
VOLATILITY = 0.004

_lock = threading.Lock()
_live_price: dict = {}
_last_update: dict = {}


def _seed_for(*parts: str) -> int:
    digest = hashlib.sha256("|".join(parts).encode()).hexdigest()
    return int(digest[:8], 16)


def _advance_live_price(trading_pair: str) -> float:
    with _lock:
        now = time.time()
        if trading_pair not in _live_price:
            _live_price[trading_pair] = STARTING_PRICES[trading_pair]
            _last_update[trading_pair] = now
            return _live_price[trading_pair]

        elapsed = now - _last_update[trading_pair]
        if elapsed < 1:
            return _live_price[trading_pair]

        steps = min(int(elapsed), 120)
        rng = np.random.default_rng(int(now * 1000) % (2**31))
        price = _live_price[trading_pair]
        for _ in range(steps):
            step_return = rng.normal(loc=DRIFT, scale=VOLATILITY)
            price *= (1 + step_return)
        price = max(price, 0.0000001)

        _live_price[trading_pair] = price
        _last_update[trading_pair] = now
        return price


def list_supported_pairs() -> list:
    return list(STARTING_PRICES.keys())


def list_timeframes() -> list:
    return list(TIMEFRAMES.keys())


def get_market_data(trading_pair: str, points: int = 200) -> dict:
    if trading_pair not in STARTING_PRICES:
        raise ValueError(f"Unknown trading pair: {trading_pair}")

    current = _advance_live_price(trading_pair)

    rng = np.random.default_rng(_seed_for(trading_pair, "line"))
    returns = rng.normal(loc=DRIFT, scale=VOLATILITY, size=points - 1)
    multipliers = np.cumprod(1 + returns)
    start = current / multipliers[-1] if len(multipliers) else current
    history = [start * m for m in multipliers]
    prices = history + [current]

    now = time.time()
    timestamps = [now - (points - 1 - i) * 60 for i in range(points)]

    sma_short, sma_long = _rolling_averages(prices, 10, 30)

    return {
        "tradingPair": trading_pair,
        "currentPrice": round(current, 8),
        "smaShort": round(sma_short[-1], 8),
        "smaLong": round(sma_long[-1], 8),
        "ticks": [
            {
                "timestamp": int(timestamps[i]),
                "price": round(prices[i], 8),
                "smaShort": round(sma_short[i], 8),
                "smaLong": round(sma_long[i], 8),
            }
            for i in range(points)
        ],
    }


def get_candles(trading_pair: str, timeframe: str = "1m", points: int = 200) -> dict:
    if trading_pair not in STARTING_PRICES:
        raise ValueError(f"Unknown trading pair: {trading_pair}")
    if timeframe not in TIMEFRAMES:
        raise ValueError(f"Unknown timeframe: {timeframe}. Use one of {list(TIMEFRAMES.keys())}")

    seconds_per_candle = TIMEFRAMES[timeframe]
    current = _advance_live_price(trading_pair)
    now = time.time()

    rng = np.random.default_rng(_seed_for(trading_pair, timeframe))
    hist_count = max(points - 1, 0)
    returns = rng.normal(loc=DRIFT, scale=VOLATILITY, size=hist_count)
    closes = []
    price = STARTING_PRICES[trading_pair]
    for r in returns:
        price *= (1 + r)
        closes.append(price)

    if closes:
        scale = current / closes[-1]
        closes = [c * scale for c in closes]

    candles = []
    open_price = STARTING_PRICES[trading_pair] * (current / closes[-1] if closes else 1)
    for i, close in enumerate(closes):
        candle_open = open_price if i == 0 else closes[i - 1]
        wiggle = abs(close - candle_open) * 0.6 + close * 0.0015
        high = max(candle_open, close) + abs(rng.normal(0, wiggle))
        low = max(min(candle_open, close) - abs(rng.normal(0, wiggle)), 0.0000001)
        ts = now - (hist_count - i) * seconds_per_candle
        candles.append({
            "timestamp": int(ts),
            "open": round(candle_open, 8),
            "high": round(high, 8),
            "low": round(low, 8),
            "close": round(close, 8),
        })

    prev_close = closes[-1] if closes else STARTING_PRICES[trading_pair]
    live_high = max(prev_close, current)
    live_low = min(prev_close, current)
    candles.append({
        "timestamp": int(now),
        "open": round(prev_close, 8),
        "high": round(live_high, 8),
        "low": round(live_low, 8),
        "close": round(current, 8),
    })

    return {
        "tradingPair": trading_pair,
        "timeframe": timeframe,
        "currentPrice": round(current, 8),
        "candles": candles,
    }


def _rolling_averages(values: list, short_window: int, long_window: int):
    sma_short, sma_long = [], []
    for i in range(len(values)):
        s = values[max(0, i - short_window + 1): i + 1]
        l = values[max(0, i - long_window + 1): i + 1]
        sma_short.append(sum(s) / len(s))
        sma_long.append(sum(l) / len(l))
    return sma_short, sma_long
