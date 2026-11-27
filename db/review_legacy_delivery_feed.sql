-- READ-ONLY review. Lists old delivery records that still carry a feed amount.
-- Since feed is now recorded as a separate Feed Sale, these amounts are NOT in any customer balance.
-- Check each one with the owner: if the customer really owes it, add a Feed Sale for it; otherwise ignore.
SELECT d.id, d.delivery_date, c.chicken_center_name, d.feed_amount, d.feed_bags
FROM delivery d JOIN customer c ON c.id = d.customer_id
WHERE d.feed_amount IS NOT NULL AND d.feed_amount > 0
ORDER BY d.delivery_date;
