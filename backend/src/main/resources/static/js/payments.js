let customersCache = [];
let paymentSession = []; // running list of payments saved this modal session (Save & Next)
let paymentFeedInventory = { stockBags: 0, bagPrice: 0 };

async function loadPaymentsPage() {
  try {
    customersCache = await Api.get('/api/customers');
    const custOptions = customersCache.map(c => `<option value="${c.id}">${escapeHtml(c.chickenCenterName)}</option>`).join('');

    document.getElementById('customer-filter').innerHTML += custOptions;
    document.getElementById('cp-customer').innerHTML = custOptions;
    document.getElementById('f-customer').innerHTML = custOptions;

    await refreshCustomerPayments();
  } catch (err) {
    handleError(err);
  }
}

async function refreshCustomerPayments() {
  const id = document.getElementById('customer-filter').value;
  const body = document.getElementById('customer-payments-body');
  try {
    const suffix = id ? `?customerId=${id}` : '';
    const [payments, feedSales] = await Promise.all([
      Api.get(`/api/customer-payments${suffix}`),
      Api.get(`/api/feed-sales${suffix}`)
    ]);
    const admin = isAdmin();
    const rows = [];
    payments.forEach(p => rows.push({
      date: p.paymentDate, customerId: p.customerId, customerName: p.customerName,
      type: 'PAYMENT RECEIVED', method: p.paymentMethod,
      reference: p.referenceNumber || p.notes || '-',
      amount: Number(p.amount || 0), id: p.id, kind: 'payment',
      calculated: String(p.notes || '').startsWith('AUTO_DELIVERY_ADJUSTMENT:')
    }));
    feedSales.forEach(f => {
      rows.push({
        date: f.saleDate, customerId: f.customerId, customerName: f.customerName,
        type: 'FEED SALE', method: 'Feed', reference: `${f.bagCount || 0} bag(s) × ${fmtMoney(f.unitPrice)}`,
        amount: Number(f.amount || 0), id: f.id, kind: 'feed-sale'
      });
      if (Number(f.amountPaid || 0) > 0) rows.push({
        date: f.saleDate, customerId: f.customerId, customerName: f.customerName,
        type: 'FEED PAYMENT', method: 'Feed payment', reference: `Against feed sale #${f.id}`,
        amount: Number(f.amountPaid || 0), id: f.id, kind: 'feed-payment'
      });
    });
    rows.sort((a,b) => new Date(b.date) - new Date(a.date) || String(b.kind).localeCompare(String(a.kind)));
    body.innerHTML = rows.length ? rows.map(r => `
      <tr>
        <td>${fmtDate(r.date)}</td>
        <td><a href="customer-detail.html?id=${r.customerId}">${escapeHtml(r.customerName)}</a></td>
        <td><span class="badge ${r.kind === 'feed-sale' ? 'badge-pending' : r.kind === 'feed-payment' ? 'badge-confirmed' : ''}">${r.type}</span></td>
        <td>${escapeHtml(r.method)}</td>
        <td>${escapeHtml(r.reference)}</td>
        <td class="text-right mono">${fmtMoney(r.amount)}</td>
        <td>${r.kind === 'payment' && admin && !r.calculated
          ? `<button class="btn-danger btn-sm" onclick="deleteCustomerPayment(${r.id})">Delete</button>`
          : r.kind === 'feed-sale' ? `<a class="btn-outline btn-sm" href="feed.html">Manage Feed</a>` : '—'}</td>
      </tr>
    `).join('') : '<tr><td colspan="7" class="table-empty">No customer payments or feed sales found</td></tr>';
  } catch (err) { handleError(err); }
}

async function deleteCustomerPayment(id) {
  if (!confirm("Permanently delete this payment? This updates the ledger balance. This cannot be undone.")) return;
  try {
    await Api.del(`/api/customer-payments/${id}`);
    showToast('Payment deleted');
    await refreshCustomerPayments();
  } catch (err) { handleError(err); }
}

document.getElementById('customer-filter').addEventListener('change', refreshCustomerPayments);

document.getElementById('tabs').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-tab]');
  if (!btn) return;
  document.querySelectorAll('#tabs button').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById('tab-customer').style.display = btn.dataset.tab === 'customer' ? '' : 'none';
});

// ---------- Customer payment: batch "Save & Next" entry ----------

function renderPaymentSession() {
  const box = document.getElementById('cp-session-list');
  if (!paymentSession.length) { box.innerHTML = ''; return; }
  const total = paymentSession.reduce((s, p) => s + p.amount, 0);
  box.innerHTML = `
    <div class="card" style="background:var(--green-100); border-color:#cfe3d4; padding:10px 14px; margin-bottom:14px;">
      <div style="font-size:12.5px; font-weight:700; color:var(--green-800); margin-bottom:6px;">
        Recorded this session (${paymentSession.length})
      </div>
      ${paymentSession.map(p => `<div class="summary-line"><span>${escapeHtml(p.customerName)}</span><span class="mono">${fmtMoney(p.amount)}</span></div>`).join('')}
      <div class="summary-line total"><span>Total</span><span class="mono">${fmtMoney(total)}</span></div>
    </div>
  `;
}

function openCustomerPaymentModal() {
  paymentSession = [];
  renderPaymentSession();
  document.getElementById('cp-form').reset();
  document.getElementById('cp-date').value = todayIso();
  document.getElementById('cp-form-error').innerHTML = '';
  openModal('customer-payment-modal');
}
document.getElementById('add-customer-payment-btn').addEventListener('click', openCustomerPaymentModal);

async function saveCurrentCustomerPayment() {
  const customerSelect = document.getElementById('cp-customer');
  const payload = {
    customerId: Number(customerSelect.value),
    paymentDate: document.getElementById('cp-date').value,
    amount: Number(document.getElementById('cp-amount').value),
    paymentMethod: document.getElementById('cp-method').value,
    referenceNumber: document.getElementById('cp-reference').value.trim(),
    notes: document.getElementById('cp-notes').value.trim(),
    createdBy: 'admin',
  };
  const saved = await Api.post('/api/customer-payments', payload);
  paymentSession.push({ customerName: customerSelect.options[customerSelect.selectedIndex].text, amount: payload.amount });
  return saved;
}

// Save & Next: keep the modal open, clear amount/reference/notes, move to the next customer.
document.getElementById('cp-save-next-btn').addEventListener('click', async () => {
  const errorBox = document.getElementById('cp-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('cp-save-next-btn');
  btn.disabled = true;
  try {
    await saveCurrentCustomerPayment();
    renderPaymentSession();
    showToast('Payment saved \u2014 ready for the next customer');
    document.getElementById('cp-amount').value = '';
    document.getElementById('cp-reference').value = '';
    document.getElementById('cp-notes').value = '';
    document.getElementById('cp-customer').focus();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// Save & Finish: save the current entry (if the form has an amount) and close.
document.getElementById('cp-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorBox = document.getElementById('cp-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('cp-save-btn');
  btn.disabled = true;
  try {
    await saveCurrentCustomerPayment();
    showToast(`${paymentSession.length} payment${paymentSession.length > 1 ? 's' : ''} recorded`);
    closeModal('customer-payment-modal');
    document.getElementById('customer-filter').value = paymentSession.length ? document.getElementById('cp-customer').value : document.getElementById('customer-filter').value;
    await refreshCustomerPayments();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// ---------- Feed sale: bag count × admin-configured price ----------
async function refreshPaymentFeedPreview() {
  try {
    paymentFeedInventory = await Api.get('/api/feed-inventory');
    const bags = Number(document.getElementById('f-bags').value || 0);
    document.getElementById('f-amount').value = fmtMoney(bags * Number(paymentFeedInventory.bagPrice || 0));
    document.getElementById('f-stock-hint').textContent =
      `Available stock: ${paymentFeedInventory.stockBags} bags · Price per bag: ${fmtMoney(paymentFeedInventory.bagPrice)}`;
  } catch (err) {
    document.getElementById('f-stock-hint').textContent = 'Unable to load feed stock. Please try again.';
  }
}
document.getElementById('f-bags').addEventListener('input', refreshPaymentFeedPreview);
document.getElementById('add-feed-btn').addEventListener('click', async () => {
  document.getElementById('feed-form').reset();
  document.getElementById('f-date').value = todayIso();
  document.getElementById('feed-form-error').innerHTML = '';
  openModal('feed-modal');
  await refreshPaymentFeedPreview();
});
document.getElementById('feed-form').addEventListener('submit', async e => {
  e.preventDefault();
  const payload = {
    customerId: Number(document.getElementById('f-customer').value),
    saleDate: document.getElementById('f-date').value,
    bagCount: Number(document.getElementById('f-bags').value),
    amountPaid: Number(document.getElementById('f-paid').value || 0),
    description: `${document.getElementById('f-bags').value} feed bag(s)`,
    notes: document.getElementById('f-notes').value.trim(),
    createdBy: window.currentUser?.username || 'staff'
  };
  const btn = document.getElementById('feed-save-btn');
  btn.disabled = true;
  try {
    const saved = await Api.post('/api/feed-sales', payload);
    showToast(`Feed sale recorded: ${saved.bagCount} bags · ${fmtMoney(saved.amount)}`);
    closeModal('feed-modal');
    await refreshCustomerPayments();
    await refreshPaymentFeedPreview();
  } catch (err) {
    document.getElementById('feed-form-error').innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally { btn.disabled = false; }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadPaymentsPage, 50);
});
