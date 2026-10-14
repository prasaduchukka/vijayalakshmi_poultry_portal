let customersCache = [];
let suppliersCache = [];
let paymentSession = []; // running list of payments saved this modal session (Save & Next)

async function loadPaymentsPage() {
  try {
    [customersCache, suppliersCache] = await Promise.all([
      Api.get('/api/customers'),
      Api.get('/api/suppliers'),
    ]);
    const custOptions = customersCache.map(c => `<option value="${c.id}">${escapeHtml(c.chickenCenterName)}</option>`).join('');
    const supOptions = suppliersCache.map(s => `<option value="${s.id}">${escapeHtml(s.supplierName)}</option>`).join('');

    document.getElementById('customer-filter').innerHTML += custOptions;
    document.getElementById('supplier-filter').innerHTML += supOptions;
    document.getElementById('cp-customer').innerHTML = custOptions;
    document.getElementById('sp-supplier').innerHTML = supOptions;
    document.getElementById('f-customer').innerHTML = custOptions;

    if (customersCache.length) {
      document.getElementById('customer-filter').value = customersCache[0].id;
      await refreshCustomerPayments();
    } else {
      document.getElementById('customer-payments-body').innerHTML =
        '<tr><td colspan="5" class="table-empty">Add a customer first</td></tr>';
    }
  } catch (err) {
    handleError(err);
  }
}

async function refreshCustomerPayments() {
  const id = document.getElementById('customer-filter').value;
  const body = document.getElementById('customer-payments-body');
  if (!id) { body.innerHTML = '<tr><td colspan="5" class="table-empty">Select a customer</td></tr>'; return; }
  try {
    const list = await Api.get(`/api/customer-payments?customerId=${id}`);
    const admin = isAdmin();
    body.innerHTML = list.length ? list.map(p => `
      <tr>
        <td>${fmtDate(p.paymentDate)}</td>
        <td><a href="customer-detail.html?id=${p.customerId}">${escapeHtml(p.customerName)}</a></td>
        <td>${p.paymentMethod}</td>
        <td>${escapeHtml(p.referenceNumber || '-')}</td>
        <td class="text-right mono">${fmtMoney(p.amount)}</td>
        ${admin ? `<td><button class="btn-danger btn-sm" onclick="deleteCustomerPayment(${p.id})">Delete</button></td>` : ''}
      </tr>
    `).join('') : '<tr><td colspan="5" class="table-empty">No payments recorded for this customer</td></tr>';
  } catch (err) {
    handleError(err);
  }
}

async function deleteCustomerPayment(id) {
  if (!confirm("Permanently delete this payment? This updates the ledger balance. This cannot be undone.")) return;
  try {
    await Api.del(`/api/customer-payments/${id}`);
    showToast('Payment deleted');
    await refreshCustomerPayments();
  } catch (err) { handleError(err); }
}

async function refreshSupplierPayments() {
  const id = document.getElementById('supplier-filter').value;
  const body = document.getElementById('supplier-payments-body');
  if (!id) { body.innerHTML = '<tr><td colspan="5" class="table-empty">Select a supplier</td></tr>'; return; }
  try {
    const list = await Api.get(`/api/supplier-payments?supplierId=${id}`);
    const admin = isAdmin();
    body.innerHTML = list.length ? list.map(p => `
      <tr>
        <td>${fmtDate(p.paymentDate)}</td>
        <td><a href="supplier-detail.html?id=${p.supplierId}">${escapeHtml(p.supplierName)}</a></td>
        <td>${p.paymentMethod}</td>
        <td>${escapeHtml(p.referenceNumber || '-')}</td>
        <td class="text-right mono">${fmtMoney(p.amount)}</td>
        ${admin ? `<td><button class="btn-danger btn-sm" onclick="deleteSupplierPayment(${p.id})">Delete</button></td>` : ''}
      </tr>
    `).join('') : '<tr><td colspan="5" class="table-empty">No payments recorded for this supplier</td></tr>';
  } catch (err) {
    handleError(err);
  }
}

async function deleteSupplierPayment(id) {
  if (!confirm("Permanently delete this payment? This updates the ledger balance. This cannot be undone.")) return;
  try {
    await Api.del(`/api/supplier-payments/${id}`);
    showToast('Payment deleted');
    await refreshSupplierPayments();
  } catch (err) { handleError(err); }
}

document.getElementById('customer-filter').addEventListener('change', refreshCustomerPayments);
document.getElementById('supplier-filter').addEventListener('change', refreshSupplierPayments);

document.getElementById('tabs').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-tab]');
  if (!btn) return;
  document.querySelectorAll('#tabs button').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  document.getElementById('tab-customer').style.display = btn.dataset.tab === 'customer' ? '' : 'none';
  document.getElementById('tab-supplier').style.display = btn.dataset.tab === 'supplier' ? '' : 'none';
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

// ---------- Feed sale ----------
document.getElementById('add-feed-btn').addEventListener('click', () => {
  document.getElementById('feed-form').reset();
  document.getElementById('f-date').value = todayIso();
  document.getElementById('feed-form-error').innerHTML = '';
  openModal('feed-modal');
});

document.getElementById('feed-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    customerId: Number(document.getElementById('f-customer').value),
    saleDate: document.getElementById('f-date').value,
    amount: Number(document.getElementById('f-amount').value),
    description: document.getElementById('f-description').value.trim(),
    notes: document.getElementById('f-notes').value.trim(),
    createdBy: 'admin',
  };
  const btn = document.getElementById('feed-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/feed-sales', payload);
    showToast('Feed sale recorded');
    closeModal('feed-modal');
  } catch (err) {
    document.getElementById('feed-form-error').innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// ---------- Supplier payment ----------
document.getElementById('add-supplier-payment-btn').addEventListener('click', () => {
  document.getElementById('sp-form').reset();
  document.getElementById('sp-date').value = todayIso();
  document.getElementById('sp-form-error').innerHTML = '';
  openModal('supplier-payment-modal');
});

document.getElementById('sp-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    supplierId: Number(document.getElementById('sp-supplier').value),
    paymentDate: document.getElementById('sp-date').value,
    amount: Number(document.getElementById('sp-amount').value),
    paymentMethod: document.getElementById('sp-method').value,
    referenceNumber: document.getElementById('sp-reference').value.trim(),
    notes: document.getElementById('sp-notes').value.trim(),
    createdBy: 'admin',
  };
  const btn = document.getElementById('sp-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/supplier-payments', payload);
    showToast('Payment recorded');
    closeModal('supplier-payment-modal');
    document.getElementById('supplier-filter').value = payload.supplierId;
    await refreshSupplierPayments();
  } catch (err) {
    document.getElementById('sp-form-error').innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadPaymentsPage, 50);
});
