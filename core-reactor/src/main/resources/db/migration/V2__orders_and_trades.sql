-- V2__orders_and_trades.sql
-- Order + trade tables backing the matching engine.

CREATE TABLE orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    trading_pair VARCHAR(20) NOT NULL,       -- e.g. 'BTC-USD'
    side VARCHAR(4) NOT NULL,                -- 'BUY' or 'SELL'
    order_type VARCHAR(10) NOT NULL,         -- 'LIMIT' or 'MARKET'
    price DECIMAL(18, 8),                    -- NULL for MARKET orders
    quantity DECIMAL(18, 8) NOT NULL,
    filled_quantity DECIMAL(18, 8) NOT NULL DEFAULT 0,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',  -- OPEN, PARTIALLY_FILLED, FILLED, CANCELLED
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_side CHECK (side IN ('BUY', 'SELL')),
    CONSTRAINT chk_order_type CHECK (order_type IN ('LIMIT', 'MARKET')),
    CONSTRAINT chk_status CHECK (status IN ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED')),
    CONSTRAINT chk_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_filled_not_exceed CHECK (filled_quantity <= quantity)
);

CREATE INDEX idx_orders_pair_status ON orders(trading_pair, status);
CREATE INDEX idx_orders_user_id ON orders(user_id);

CREATE TABLE trades (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    trading_pair VARCHAR(20) NOT NULL,
    buy_order_id UUID NOT NULL REFERENCES orders(id),
    sell_order_id UUID NOT NULL REFERENCES orders(id),
    price DECIMAL(18, 8) NOT NULL,
    quantity DECIMAL(18, 8) NOT NULL,
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_trade_quantity_positive CHECK (quantity > 0),
    CONSTRAINT chk_trade_price_positive CHECK (price > 0)
);

CREATE INDEX idx_trades_pair ON trades(trading_pair);

-- Idempotency key -> order id, so a replayed order-creation request returns
-- the SAME order instead of creating a duplicate.
CREATE TABLE order_idempotency_keys (
    idempotency_key VARCHAR(255) PRIMARY KEY,
    order_id UUID NOT NULL REFERENCES orders(id),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
