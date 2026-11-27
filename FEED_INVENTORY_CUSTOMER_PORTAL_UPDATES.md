
## Follow-up update: feed payments and customer paper ledger

- Feed sales now store `amountPaid` and calculate `balanceDue = amount - amountPaid`.
- Recording/editing a feed sale can record an amount paid. The paid amount creates a `FEED_PAYMENT` credit in the customer's ledger; it cannot exceed the sale cost.
- Feed history shows Total Cost, Amount Paid, Balance Due, and Admin Edit/Delete actions.
- Deleting a feed sale restores its bags and removes both the feed debit and its linked feed-payment credit.
- Payments → Customer Payments Received defaults to All Customers and combines customer payments, feed sale debits, and feed-sale payment credits. Filter by one customer when needed.
- Customer login now shows the paper-ledger columns from the supplied screenshot: Date, Birds, KG, Rate, Amount, O.Bal, T.Bal, Paid, Balance and Description.
- Existing `feed_sale` rows need the new `amount_paid` column. Hibernate update can add it; alternatively run `db/migration/V2__feed_sale_amount_paid.sql` once. Existing historical feed sales are initialized with amount paid = 0.
