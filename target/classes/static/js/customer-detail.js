const customerId = new URLSearchParams(location.search).get('id');
let currentSummary = null;
let cachedDeliveries = [];
let cachedPayments = [];
let cachedFeedSales = [];

async function loadCustomerDetail() {
  if (!customerId) {
    document.getElementById('customer-header-card').innerHTML = '<div class="alert alert-error">No customer specified.</div>';
    return;
  }
  try {
    const [summary, deliveries, payments, feedSales] = await Promise.all([
      Api.get(`/api/customers/${customerId}/account`),
      Api.get(`/api/deliveries?customerId=${customerId}`),
      Api.get(`/api/customer-payments?customerId=${customerId}`),
      Api.get(`/api/feed-sales?customerId=${customerId}`),
    ]);
    currentSummary = summary;
    cachedDeliveries = deliveries;
    cachedPayments = payments;
    cachedFeedSales = feedSales;
    renderHeader(summary);
    renderStats(summary);
    renderPaperLedger(summary.customer.openingBalance, deliveries, payments, feedSales);
    renderDeliveries(deliveries);
    renderPayments(payments);
  } catch (err) {
    handleError(err);
  }
}

function renderHeader(summary) {
  const c = summary.customer;
  document.title = c.chickenCenterName + ' \u2013 Vijayalakshmi Poultry Farm';
  document.getElementById('customer-header-card').innerHTML = `
    <div class="card-title-row">
      <div>
        <h2 style="margin-bottom:4px;">${escapeHtml(c.chickenCenterName)}</h2>
        <div class="text-muted">${escapeHtml(c.ownerContactPerson || '')} ${c.phoneNumber ? '\u00b7 ' + escapeHtml(c.phoneNumber) : ''}</div>
        <div class="text-muted">${escapeHtml(c.address || '')}</div>
      </div>
      ${statusBadge(c.status)}
    </div>
  `;
}

function renderStats(summary) {
  const outstanding = Number(summary.currentOutstandingBalance);
  document.getElementById('customer-stats').innerHTML = `
    <div class="stat-card ${outstanding > 0 ? 'red' : ''}">
      <div class="stat-label">Current Outstanding Balance</div>
      <div class="stat-value">${fmtMoney(summary.currentOutstandingBalance)}</div>
    </div>
    <div class="stat-card">
      <div class="stat-label">Total Deliveries</div>
      <div class="stat-value">${summary.totalDeliveries}</div>
    </div>
    <div class="stat-card">
      <div class="stat-label">Total Boxes / Received Wt</div>
      <div class="stat-value" style="font-size:18px;">${summary.totalBoxes} boxes &middot; ${fmtKg(summary.totalReceivedWeight)}</div>
    </div>
    <div class="stat-card amber">
      <div class="stat-label">Total Sales / Total Paid</div>
      <div class="stat-value" style="font-size:18px;">${fmtMoney(summary.totalSales)} / ${fmtMoney(summary.totalPaid)}</div>
    </div>
  `;
}

// ---------- Ledger tab: merged, paper-format table ----------
// Mirrors the backend PDF's merge logic so the on-screen view matches the printed statement.
function renderPaperLedger(openingBalance, deliveries, payments, feedSales) {
  const rows = [];
  deliveries.forEach(d => rows.push({ date: d.deliveryDate, kind: 'delivery', data: d }));
  payments.forEach(p => rows.push({ date: p.paymentDate, kind: 'payment', data: p }));
  feedSales.forEach(f => rows.push({ date: f.saleDate, kind: 'feed', data: f }));
  rows.sort((a, b) => new Date(a.date) - new Date(b.date));

  let running = Number(openingBalance);
  const body = document.getElementById('paper-ledger-body');

  if (!rows.length) {
    body.innerHTML = '<tr><td colspan="10" class="table-empty">No transactions yet</td></tr>';
    return;
  }

  body.innerHTML = rows.map(row => {
    const oBal = running;
    if (row.kind === 'delivery') {
      const d = row.data;
      const tBal = oBal + Number(d.salesAmount);
      running = tBal;
      const kg = d.receivedWeight ?? d.dispatchWeight;
      const desc = d.vehicleNumber ? `Vehicle ${escapeHtml(d.vehicleNumber)}` : (d.notes ? escapeHtml(d.notes) : '-');
      return `<tr>
        <td>${fmtDate(d.deliveryDate)}</td>
        <td>${d.numberOfBirds ?? '-'}</td>
        <td>${fmtKg(kg)}</td>
        <td>${fmtMoney(d.sellingRate)}</td>
        <td class="text-right mono">${fmtMoney(d.salesAmount)}</td>
        <td class="text-right mono">${fmtMoney(oBal)}</td>
        <td class="text-right mono">${fmtMoney(tBal)}</td>
        <td class="text-right mono">-</td>
        <td class="text-right mono"><strong>${fmtMoney(running)}</strong></td>
        <td class="text-muted">${desc}</td>
      </tr>`;
    } else if (row.kind === 'feed') {
      const f = row.data;
      const tBal = oBal + Number(f.amount);
      running = tBal;
      return `<tr>
        <td>${fmtDate(f.saleDate)}</td>
        <td>-</td>
        <td class="text-muted">Feed</td>
        <td>-</td>
        <td class="text-right mono">${fmtMoney(f.amount)}</td>
        <td class="text-right mono">${fmtMoney(oBal)}</td>
        <td class="text-right mono">${fmtMoney(tBal)}</td>
        <td class="text-right mono">-</td>
        <td class="text-right mono"><strong>${fmtMoney(running)}</strong></td>
        <td class="text-muted">${escapeHtml(f.description || 'Feed sale')}</td>
      </tr>`;
    } else {
      const p = row.data;
      running = oBal - Number(p.amount);
      const desc = p.paymentMethod + (p.referenceNumber ? ` \u00b7 Ref: ${escapeHtml(p.referenceNumber)}` : '') + (p.notes ? ` \u00b7 ${escapeHtml(p.notes)}` : '');
      return `<tr>
        <td>${fmtDate(p.paymentDate)}</td>
        <td>-</td><td>-</td><td>-</td><td>-</td>
        <td class="text-right mono">${fmtMoney(oBal)}</td>
        <td class="text-right mono">${fmtMoney(oBal)}</td>
        <td class="text-right mono text-green">${fmtMoney(p.amount)}</td>
        <td class="text-right mono"><strong>${fmtMoney(running)}</strong></td>
        <td class="text-muted">${desc}</td>
      </tr>`;
    }
  }).join('');
}

function renderDeliveries(list) {
  const admin = isAdmin();
  const body = document.getElementById('deliveries-body');
  body.innerHTML = list.length ? list.map(d => `
    <tr>
      <td>${fmtDate(d.deliveryDate)}</td>
      <td>${d.numberOfBirds ?? '-'}</td>
      <td>${fmtKg(d.dispatchWeight)}</td>
      <td>${fmtMoney(d.sellingRate)}</td>
      <td class="text-right mono">${fmtMoney(d.salesAmount)}</td>
      <td>${admin ? `<button class="btn-outline btn-sm" onclick="editDelivery(${d.id})">Edit</button>
      <button class="btn-danger btn-sm" onclick="deleteDelivery(${d.id})">Delete</button>` : ''}</td>
    </tr>
  `).join('') : '<tr><td colspan="6" class="table-empty">No deliveries recorded yet</td></tr>';
}

function renderPayments(list) {
  const admin = isAdmin();
  const body = document.getElementById('payments-body');
  body.innerHTML = list.length ? list.map(p => `
    <tr>
      <td>${fmtDate(p.paymentDate)}</td>
      <td>${p.paymentMethod}</td>
      <td>${escapeHtml(p.referenceNumber || '-')}</td>
      <td>${escapeHtml(p.notes || '-')}</td>
      <td class="text-right mono">${fmtMoney(p.amount)}</td>
      <td>${admin ? `<button class="btn-outline btn-sm" onclick="editPayment(${p.id})">Edit</button>
      <button class="btn-danger btn-sm" onclick="deletePayment(${p.id})">Delete</button>` : ''}</td>
    </tr>
  `).join('') : '<tr><td colspan="6" class="table-empty">No payments recorded yet</td></tr>';
}

function editDelivery(id) {
  const d = cachedDeliveries.find(x => x.id === id);
  if (!d) return;
  document.getElementById('delivery-modal-title') && (document.getElementById('delivery-modal-title').textContent = 'Edit Delivery');
  document.getElementById('d-id').value = d.id;
  document.getElementById('d-date').value = d.deliveryDate;
  document.getElementById('d-birds').value = d.numberOfBirds ?? '';
  document.getElementById('d-dispatch').value = d.dispatchWeight;
  document.getElementById('d-rate').value = d.sellingRate;
  document.getElementById('d-sales-amount').value = fmtMoney(d.salesAmount);
  document.getElementById('d-notes').value = d.notes || '';
  document.getElementById('delivery-form-error').innerHTML = '';
  openModal('delivery-modal');
}

function editPayment(id) {
  const p = cachedPayments.find(x => x.id === id);
  if (!p) return;
  document.getElementById('p-id').value = p.id;
  document.getElementById('p-date').value = p.paymentDate;
  document.getElementById('p-amount').value = p.amount;
  document.getElementById('p-method').value = p.paymentMethod;
  document.getElementById('p-reference').value = p.referenceNumber || '';
  document.getElementById('p-notes').value = p.notes || '';
  document.getElementById('payment-form-error').innerHTML = '';
  openModal('payment-modal');
}

async function deleteDelivery(id) {
  if (!confirm(`Permanently delete this delivery?

This will delete the delivery, every payment tied to this delivery, and all related ledger entries. This cannot be undone.`)) return;
  try {
    await Api.del(`/api/deliveries/${id}`);
    showToast('Delivery deleted');
    await loadCustomerDetail();
  } catch (err) { handleError(err); }
}

async function deletePayment(id) {
  if (!confirm("Permanently delete this payment? This updates the ledger balance. This cannot be undone.")) return;
  try {
    await Api.del(`/api/customer-payments/${id}`);
    showToast('Payment deleted');
    await loadCustomerDetail();
  } catch (err) { handleError(err); }
}

// ---------- Tabs ----------
document.getElementById('tabs').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-tab]');
  if (!btn) return;
  document.querySelectorAll('#tabs button').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  ['ledger', 'deliveries', 'payments'].forEach(t => {
    document.getElementById('tab-' + t).style.display = (t === btn.dataset.tab) ? '' : 'none';
  });
});

// ---------- Delivery modal ----------
function recalcDeliveryPreview() {
  const dispatch = Number(document.getElementById('d-dispatch').value || 0);
  const rate = Number(document.getElementById('d-rate').value || 0);
  document.getElementById('d-sales-amount').value = '\u20b9 ' + (dispatch * rate).toFixed(2);
}
['d-dispatch', 'd-rate'].forEach(id =>
  document.getElementById(id).addEventListener('input', recalcDeliveryPreview));

document.getElementById('record-delivery-btn').addEventListener('click', () => {
  document.getElementById('delivery-form').reset();
  document.getElementById('d-id').value = '';
  document.getElementById('delivery-modal-title').textContent = 'Record Delivery';
  document.getElementById('d-date').value = todayIso();
  document.getElementById('d-sales-amount').value = '';
  document.getElementById('delivery-form-error').innerHTML = '';
  openModal('delivery-modal');
});

document.getElementById('delivery-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('d-id').value;
  const payload = {
    customerId: Number(customerId),
    deliveryDate: document.getElementById('d-date').value,
    numberOfBirds: document.getElementById('d-birds').value ? Number(document.getElementById('d-birds').value) : null,
    dispatchWeight: Number(document.getElementById('d-dispatch').value),
    sellingRate: Number(document.getElementById('d-rate').value),
    notes: document.getElementById('d-notes').value.trim(),
    createdBy: 'admin',
  };
  const btn = document.getElementById('delivery-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/deliveries/${id}`, payload);
      showToast('Delivery updated');
    } else {
      await Api.post('/api/deliveries', payload);
      showToast('Delivery recorded');
    }
    closeModal('delivery-modal');
    await loadCustomerDetail();
  } catch (err) {
    document.getElementById('delivery-form-error').innerHTML =
      `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// ---------- Feed sale modal ----------
document.getElementById('record-feed-btn').addEventListener('click', () => {
  document.getElementById('feed-form').reset();
  document.getElementById('f-date').value = todayIso();
  document.getElementById('feed-form-error').innerHTML = '';
  openModal('feed-modal');
});

document.getElementById('feed-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    customerId: Number(customerId),
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
    await loadCustomerDetail();
  } catch (err) {
    document.getElementById('feed-form-error').innerHTML =
      `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// ---------- Payment modal ----------
document.getElementById('record-payment-btn').addEventListener('click', () => {
  document.getElementById('payment-form').reset();
  document.getElementById('p-id').value = '';
  document.getElementById('p-date').value = todayIso();
  document.getElementById('payment-form-error').innerHTML = '';
  openModal('payment-modal');
});

document.getElementById('payment-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('p-id').value;
  const payload = {
    customerId: Number(customerId),
    paymentDate: document.getElementById('p-date').value,
    amount: Number(document.getElementById('p-amount').value),
    paymentMethod: document.getElementById('p-method').value,
    referenceNumber: document.getElementById('p-reference').value.trim(),
    notes: document.getElementById('p-notes').value.trim(),
    createdBy: 'admin',
  };
  const btn = document.getElementById('payment-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/customer-payments/${id}`, payload);
      showToast('Payment updated');
    } else {
      await Api.post('/api/customer-payments', payload);
      showToast('Payment recorded');
    }
    closeModal('payment-modal');
    await loadCustomerDetail();
  } catch (err) {
    document.getElementById('payment-form-error').innerHTML =
      `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// ---------- PDF ----------
document.getElementById('generate-pdf-btn').addEventListener('click', () => {
  document.getElementById('pdf-from').value = firstOfMonthIso();
  document.getElementById('pdf-to').value = todayIso();
  openModal('pdf-modal');
});

document.getElementById('pdf-generate-btn').addEventListener('click', async () => {
  const from = document.getElementById('pdf-from').value;
  const to = document.getElementById('pdf-to').value;
  try {
    const blob = await Api.getBlob(`/api/reports/customer-statement/${customerId}?from=${from}&to=${to}`);
    openPdfBlob(blob, `customer-statement-${customerId}.pdf`);
    closeModal('pdf-modal');
  } catch (err) {
    handleError(err);
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadCustomerDetail, 50);
});
