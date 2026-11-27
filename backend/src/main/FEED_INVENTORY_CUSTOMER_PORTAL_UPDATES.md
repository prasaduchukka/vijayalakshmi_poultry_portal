# Feed Inventory and Customer Portal Updates

## Customer portal accounts
- Customer portal users remain in `app_user` with role `CUSTOMER`.
- The Staff page now separates staff accounts from customer portal accounts.
- Customer portal usernames accept customer numbers/mobile numbers (1–40 allowed characters).
- Create/reset credentials from Customers -> Portal Login. The username prompt is prefilled with the customer's mobile number, or the internal customer ID when no mobile number is present.
- Customer passwords remain BCrypt-hashed.

If the database still has an older role constraint, run once against the same PostgreSQL database:
```sql
ALTER TABLE app_user DROP CONSTRAINT IF EXISTS app_user_role_check;
ALTER TABLE app_user ADD CONSTRAINT app_user_role_check
CHECK (role IN ('ADMIN', 'GUMASTA', 'CUSTOMER'));
```

## Feed stock workflow
- Admin opens the Feed tab and records bags received from the godown plus the current selling price per bag.
- Restocking adds to the current available bag count and updates the price for future sales.
- Payments -> Feed Sale and Customer -> Record Feed Sale now ask for bag count, not a manually entered amount.
- Amount is calculated server-side as `bag count × current bag price`; the displayed amount is only a preview.
- Successful sales deduct stock and create the customer ledger debit in one database transaction.
- Insufficient stock or an unset bag price rejects the sale without changing stock or ledger.
- Deleting a feed sale through the API restores its bags to stock. Editing a sale adjusts stock by the bag-count difference.
- Existing historical feed-sale rows remain in the ledger. They have no bag count/price because the earlier system only stored a manually entered amount; they do not automatically reduce the new inventory. Before using the new module, enter the actual remaining physical stock in the Feed tab.

Hibernate `ddl-auto: update` creates the `feed_inventory` table and adds nullable `bag_count` / `unit_price` columns to `feed_sale` on application startup.

## Public website contact
- Phone: +91 94919 59697
- Email: pativadaroopchand@gmail.com
- Google Maps directions link added to the contact section and footer.
