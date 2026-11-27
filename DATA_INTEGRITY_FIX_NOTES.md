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

---
## Update 2026-11-20 - Ledger balance must always equal Current Outstanding

Root causes found where a customer's balance was computed differently in different places:
- Feed-sale payments (`amountPaid`) were ignored by: the admin Ledger tab (customer-detail.js), the PDF statements, and the Dashboard "customer outstanding" total.
- Period statements (from/to) started from the customer's original opening balance instead of the balance carried forward to the start date.
- Moving a payment to a different customer/supplier left a row in the old party's ledger.
- Ledger changes were not serialised per customer (two simultaneous saves could interleave).
- Supplier ledger had no self-heal (customer ledger did).
- entity/User.java contained literal `\n` text (compile error) - fixed.

Changes:
- LedgerService: row lock per customer/supplier on every ledger mutation; reconcileSupplierLedger; getCustomerBalanceBefore; verifyAllBalances; startup per-party transactions, integrity log (INFO "passed" / ERROR "FAILED"), WARN for legacy delivery feed amounts.
- New admin endpoint `GET /api/ledger/verify` (read-only): for every customer/supplier compares records-based outstanding, net of ledger rows and last running balance. Status OK or MISMATCH with details.
- DashboardService, PdfService (+ same-day ordering), ReportController, customer-detail.js: feed paid now included everywhere.
- CustomerPaymentService/SupplierPaymentService.update: old party's ledger re-reconciled.
- FeedSaleService.update: legacy sale keeps its original unit price.
- db/review_legacy_delivery_feed.sql: read-only list of old deliveries with a feed amount (not part of any balance).

NOT verified by running (Maven Central is blocked in this environment): the project was not compiled or started. Only JS was executed (Node test of the ledger table) and Java was syntax-checked.
Before deploying: back up Railway DB, run `mvn clean package` in STS, start against a COPY of production data, then open `/api/ledger/verify` as admin - it must say OK.

Open business questions (not changed): compensation (weight loss/mortality) counts in Total Paid; Profit & Loss revenue excludes feed and compensation; deleting a customer does not restore feed stock; supplier opening balance edits are ignored.

## Update 2026-11-20 (2) - Feed tab button and 10-day customer ledger
- Feed tab: new "+ Feed Sale" button at the left of the Feed Sales History header; opens a form (customer, date, bags, auto total at current bag price, amount paid, notes) and posts to /api/feed-sales (same rules as the customer page).
- Customer portal ledger (/api/customer-portal/ledger) now returns only entries from the last 10 days (today and previous 9, India time). The rows are the SAME stored ledger rows with the SAME running balances as the full ledger - nothing is recomputed for the window - so the first visible O.Bal is the true balance carried forward, and the last balance equals Current Outstanding. Admin screens still show the full ledger.

## Update 2026-11-22 - Header behaviour
- Mobile/tablet (<=1499px): right after login the screen is a "menu home": header + the full list of pages (large icons/text), no page content. Choosing a page opens it normally (menu closed); the menu icon also leaves the menu screen. One-time sessionStorage flag set by admin-login.html, consumed in layout.js (body.menu-home).
- Desktop (>=1500px): all page links on ONE row with icons and the logged-in user name kept. 1500-1849px = tighter spacing and icon-only Log out; >=1850px = roomy spacing. Header no longer capped at 1400px. Verified by measuring positions at 1500, 1570, 1700, 1849, 1850, 1920, 2560px (one row, clear gap before the user name, no horizontal scroll).

## Update 2026-11-25 - Supplier simplification, reports, View more
- Supplier payments / supplier ledger removed from the screens: Payments page (supplier tab, "+ Supplier Payment" button and form), supplier page (stats, payment history, ledger, payment form), dashboard "Supplier Outstanding" card, and the supplier opening-balance column/field. Backend tables and endpoints are untouched, so no data is lost and nothing needs migrating.
- Supplier page (supplier-detail.html) now shows purchase history only: Date, Vehicle No, Birds, Boxes, Weight, Driver, Helper, with an optional From/To and a "Generate Purchase PDF" button. Purchases are still recorded/edited from the Purchases tab.
- Supplier Purchase PDF: Date | Vehicle No | Birds | Boxes | Weight | Driver | Helper (Purchase ID, Rate, Amount, payments and outstanding removed). Newest first, with totals (purchases, birds, weight). Reports tab: supplier or "All Suppliers" + From/To dates. New endpoint GET /api/reports/supplier-purchase/all?from&to; /api/reports/supplier-purchase/{id} accepts optional from/to.
- Reports tab: "Estimated Profit / Loss" panel and "Daily Sales PDF" removed (page and /api/reports/profit-loss, /api/reports/daily-sales endpoints and PDF code). The separate /api/profit-loss API is left as is.
- "View more": Purchases tab (Purchases, Supplier Details, Trip Details), Deliveries tab and supplier page show the 5 newest rows; "View more (N more)" reveals 10 more each click, "Show less" folds back. Newest first everywhere (purchases, trips, deliveries, feed sales, expenses; payments already were).
Not compiled/run here (Maven Central blocked): Java syntax-checked only; pages tested in a browser with sample data.
