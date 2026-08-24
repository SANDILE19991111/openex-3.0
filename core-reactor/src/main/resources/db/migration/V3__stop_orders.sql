-- V3__stop_orders.sql
-- Adds support for STOP orders (buy stop / sell stop / stop-loss).

ALTER TABLE orders ADD COLUMN stop_price DECIMAL(18, 8);

-- Widen the status check constraint to include TRIGGERED (a stop order that
-- fired and was converted into a MARKET order) and the STOP order_type.
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_status;
ALTER TABLE orders ADD CONSTRAINT chk_status
    CHECK (status IN ('OPEN', 'PARTIALLY_FILLED', 'FILLED', 'CANCELLED', 'TRIGGERED'));

ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_order_type;
ALTER TABLE orders ADD CONSTRAINT chk_order_type
    CHECK (order_type IN ('LIMIT', 'MARKET', 'STOP'));

-- A STOP order must carry a stop_price; a non-STOP order must not need one
-- (not enforced as a hard DB constraint here to keep the migration simple -
-- enforced instead in MatchingEngine.submit()).
CREATE INDEX idx_orders_pending_stops ON orders(trading_pair, order_type, status)
    WHERE order_type = 'STOP' AND status = 'OPEN';
