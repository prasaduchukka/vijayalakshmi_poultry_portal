-- Run once on the existing PostgreSQL database before deploying this update.
ALTER TABLE feed_sale
    ADD COLUMN IF NOT EXISTS amount_paid NUMERIC(14,2);

UPDATE feed_sale
SET amount_paid = 0
WHERE amount_paid IS NULL;

-- Keep existing historical feed sales intact. Their amount paid defaults to zero.
