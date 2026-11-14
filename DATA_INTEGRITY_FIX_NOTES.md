# Vijayalakshmi Poultry Farm – Data Integrity Fixes

## Changes included

1. **Purchase → Trip synchronization**
   - Creating, editing, or moving a purchase recalculates the linked trip's loaded weight from all purchases on that trip.
   - Vehicle/trip metadata is retained.
   - Existing rule preventing a purchase from being moved away from a trip that already has deliveries remains in place.

2. **Delivery → Ledger synchronization**
   - Chicken sales are stored as a `DELIVERY` debit using `salesAmount` only.
   - Feed attached to a delivery is stored as its own `DELIVERY_FEED` debit, with bag count in the description.
   - Editing a delivery replaces both rows; removing feed removes the feed row; changing customer moves both rows and recalculates balances.
   - `DELIVERY_FEED` is a distinct enum value so it cannot collide with standalone `FEED_SALE` records.

3. **Delivery → Payment synchronization**
   - Delivery responses include the sum of linked payments, so the edit form pre-fills the saved payment amount.
   - Editing the inline payment amount updates the linked `CustomerPayment` and its ledger credit in the same transaction.
   - Enter `0` to remove the linked inline payment; leaving the field blank preserves it.
   - If multiple payments are linked to one delivery, changing the inline amount is rejected for manual review rather than silently overwriting or deleting multiple payment records.
   - Payment is validated against the complete delivery total (chicken + feed).

4. **PDF statement**
   - Feed attached to a delivery is shown as a separate feed row in the customer statement PDF.
   - Chicken delivery, feed, and payment rows remain separate.

5. **Delete handling**
   - Deleting a delivery or a trip also removes its delivery-feed ledger row and recalculates the customer's running balance.

## Important deployment notes

- No database table or business record is dropped or recreated by these code changes.
- The new `DELIVERY_FEED` ledger reference type is stored as a string enum; no destructive schema migration is included.
- When an older delivery is edited, its previous combined chicken+feed `DELIVERY` ledger row is replaced with separate chicken and feed rows. Older deliveries that are never edited retain their historical ledger representation until separately reconciled.
- Back up the Railway PostgreSQL database before deploying. Deploy the backend and static resources together, then test with a non-production customer/delivery first.

## Verification status

- JavaScript syntax check (`node --check`) passed for `deliveries.js`.
- Source delimiter sanity checks passed for the modified Java files.
- A full Maven compile/test could not be run in the preparation environment because Maven (`mvn`) is not installed and the project has no Maven wrapper. Please run `mvn clean test` in the backend directory (or import the Maven project in STS/Eclipse) before production deployment.

## Ledger balance sync fix - 09 Nov 2026 (Current Outstanding != Ledger)
- Cause: "Current Outstanding Balance" read the `balanceAfter` snapshot of the LAST row in `customer_ledger_entry`, while the on-screen ledger/PDF are rebuilt from the real deliveries, feed sales and payments. A ledger row with no live source record (e.g. Rs.1500 left behind) showed only in the snapshot.
- `LedgerService.getCurrentCustomerBalance` / `getCurrentSupplierBalance` now compute from the real records, so outstanding always equals the ledger statement.
- `LedgerService.reconcileCustomerLedger` removes orphan rows, adds missing ones and recalculates balances. It runs at startup for every customer, when a customer account page loads, and when the ledger API is called. It never touches deliveries/payments/feed sales.
- Dashboard "customer outstanding" now includes standalone feed sales (it previously ignored them).
- Supplier ledger table rows are not rebuilt yet; only the supplier balance itself is now computed from real records.
- Still needs `mvn clean package` / STS build before deploy (not compiled in the preparation sandbox).

## DB connection reset + mobile header - 09 Nov 2026
- Error: `PSQLException: An I/O error occurred while sending to the backend` / `SocketException: Connection reset` (SQLSTATE 08006) on `GET /api/deliveries`. The Railway Postgres socket was dropped while idle and a dead pooled connection was handed to a request.
- `application.yml`: Hikari keepalive 30s, idle-timeout 2min, max-lifetime 4min, plus JDBC connect/socket timeouts.
- `DbRetryFilter` (new): a GET /api/** request that fails on a dead connection is retried once automatically. POST/PUT/DELETE are never retried.
- `GlobalExceptionHandler`: connection failures now return a clean 503 "Temporary database connection problem. Please try again." instead of the raw JDBC message.
- Mobile header (<=980px): Row 1 = logo/company name + Account (right); Row 2 = Menu button + Log out (left, under company name). Desktop unchanged. Files: `static/css/style.css`, `static/js/layout.js`.
- Still needs `mvn clean package` (not compiled in the preparation sandbox).
