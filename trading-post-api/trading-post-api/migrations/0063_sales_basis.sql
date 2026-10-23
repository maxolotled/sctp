-- Where a sale comes from (see detectConfirmedSales): 'paid' (stock down + payment up),
-- 'buy' (the mod saw its player buy it; clientId keeps a retried upload from doubling it),
-- 'drop' (a small part of a still-stocked chest went, with no payment to show for it).
ALTER TABLE shopSales ADD COLUMN basis TEXT NOT NULL DEFAULT 'paid';
ALTER TABLE shopSales ADD COLUMN clientId TEXT;
CREATE UNIQUE INDEX idx_shopSales_client ON shopSales(clientId) WHERE clientId IS NOT NULL;
