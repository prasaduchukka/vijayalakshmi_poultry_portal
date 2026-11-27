# Public Website and Customer Ledger Portal

This update adds:
- Public homepage at `/` with the farm's red, gold and green visual style.
- Admin/staff sign-in at `/admin-login.html` (existing admin application is retained).
- Customer sign-in at `/customer-login.html`.
- Customer ledger page at `/customer-portal.html`.
- Admin-only customer portal credential provisioning from Customers -> Portal Login.

Customer portal security:
- A customer account has role `CUSTOMER` and a `customer_id` association in `app_user`.
- Customer portal endpoints derive the customer ID from the authenticated session; the browser cannot request another customer's ledger by changing an ID.
- `/api/customer-portal/**` is restricted to `CUSTOMER`; other `/api/**` routes are restricted to `ADMIN` or `GUMASTA`.
- Customer credentials are BCrypt-hashed. The administrator sets a username and password from the customer list and must share them privately with that customer.
- Hibernate `ddl-auto: update` adds the new nullable `customer_id` column on the next application start.

After deployment, sign in as Admin, open Customers, and use **Portal Login** on a customer row to create or reset that customer's credentials. Give the customer the website URL plus their username and password.
