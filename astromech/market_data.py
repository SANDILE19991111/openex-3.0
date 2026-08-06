"""
Simulated market data feed. Generates a random-walk-with-drift price series
per trading pair, seeded from the same starting prices the Kotlin backend
uses (see SupportedPairs.kt), so the numbers feel consistent across services.

This is intentionally simple and stateless-per-request: each call regenerates
a fresh series using a fixed seed derived from the pair name, so repeated
calls for the same pair return the same historical shape (useful for a demo
chart that shouldn't jump around on every refresh) while still feeling
"live" for the most recent tick.
"""
import hashlib
import time
import numpy as np
import pandas as pd

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

DEFAULT_POINTS = 200          # number of historical ticks to generate
DRIFT = 0.0001                # tiny upward drift per tick
VOLATILITY = 0.004            # per-tick volatility as a fraction of price
SHORT_WINDOW = 10
LONG_WINDOW = 30


def _seed_for(trading_pair: str) -> int:
    """Deterministic seed per pair so the historical shape is stable across calls."""
    digest = hashlib.sha256(trading_pair.encode()).hexdigest()
    return int(digest[:8], 16)


def generate_series(trading_pair: str, points: int = DEFAULT_POINTS) -> pd.DataFrame:
    if trading_pair not in STARTING_PRICES:
        raise ValueError(f"Unknown trading pair: {trading_pair}")

    start_price = STARTING_PRICES[trading_pair]
    rng = np.random.default_rng(_seed_for(trading_pair))

    # Random walk with drift: each step's return ~ Normal(DRIFT, VOLATILITY)
    returns = rng.normal(loc=DRIFT, scale=VOLATILITY, size=points)
    price_multipliers = np.cumprod(1 + returns)
    prices = start_price * price_multipliers

    now = int(time.time())
    # One tick per minute going back from now.
    timestamps = [now - (points - 1 - i) * 60 for i in range(points)]

    df = pd.DataFrame({"timestamp": timestamps, "price": prices})
    df["sma_short"] = df["price"].rolling(window=SHORT_WINDOW, min_periods=1).mean()
    df["sma_long"] = df["price"].rolling(window=LONG_WINDOW, min_periods=1).mean()
    return df


def get_market_data(trading_pair: str, points: int = DEFAULT_POINTS) -> dict:
    df = generate_series(trading_pair, points)
    latest = df.iloc[-1]
    return {
        "tradingPair": trading_pair,
        "currentPrice": round(float(latest["price"]), 8),
        "smaShort": round(float(latest["sma_short"]), 8),
        "smaLong": round(float(latest["sma_long"]), 8),
        "ticks": [
            {
                "timestamp": int(row.timestamp),
                "price": round(float(row.price), 8),
                "smaShort": round(float(row.sma_short), 8),
                "smaLong": round(float(row.sma_long), 8),
            }
            for row in df.itertuples()
        ],
    }


def list_supported_pairs() -> list[str]:
    return list(STARTING_PRICES.keys())
