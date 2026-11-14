let plRange = { from: todayIso(), to: todayIso() };

async function loadReportsPage() {
  try {
    const [customers, suppliers, vehicles] = await Promise.all([
      Api.get('/api/customers'),
      Api.get('/api/suppliers'),
      Api.get('/api/vehicles'),
    ]);
    document.getElementById('cs-customer').innerHTML =
      `<option value="ALL">\u2014 All Customers \u2014</option>` +
      customers.map(c => `<option value="${c.id}">${escapeHtml(c.chickenCenterName)}</option>`).join('');
    document.getElementById('sp-supplier').innerHTML = suppliers
      .map(s => `<option value="${s.id}">${escapeHtml(s.supplierName)}</option>`).join('');
    document.getElementById('vt-vehicle').innerHTML = vehicles.length
      ? vehicles.map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('')
      : '<option value="">No vehicles added yet</option>';

    document.getElementById('cs-from').value = firstOfMonthIso();
    document.getElementById('cs-to').value = todayIso();
    document.getElementById('ds-date').value = todayIso();
    document.getElementById('ex-from').value = firstOfMonthIso();
    document.getElementById('ex-to').value = todayIso();
    document.getElementById('vt-from').value = firstOfMonthIso();
    document.getElementById('vt-to').value = todayIso();

    await loadProfitLoss();
  } catch (err) {
    handleError(err);
  }
}

async function loadProfitLoss() {
  const resultBox = document.getElementById('pl-result');
  resultBox.className = 'loading-row';
  resultBox.textContent = 'Loading...';
  try {
    const pl = await Api.get(`/api/profit-loss?from=${plRange.from}&to=${plRange.to}`);
    resultBox.className = '';
    const negative = Number(pl.estimatedProfitLoss) < 0;
    resultBox.innerHTML = `
      <div class="summary-line"><span>Sales Revenue</span><span class="mono">${fmtMoney(pl.salesRevenue)}</span></div>
      <div class="summary-line"><span>Purchase Cost</span><span class="mono">- ${fmtMoney(pl.purchaseCost)}</span></div>
      <div class="summary-line"><span>Recorded Expenses</span><span class="mono">- ${fmtMoney(pl.recordedExpenses)}</span></div>
      <div class="summary-line total"><span>Estimated Profit/Loss</span><span class="mono ${negative ? 'text-red' : 'text-green'}">${fmtMoney(pl.estimatedProfitLoss)}</span></div>
    `;
  } catch (err) {
    handleError(err);
  }
}

document.getElementById('pl-range-tabs').addEventListener('click', (e) => {
  const btn = e.target.closest('button[data-range]');
  if (!btn) return;
  document.querySelectorAll('#pl-range-tabs button').forEach(b => b.classList.remove('active'));
  btn.classList.add('active');
  const range = btn.dataset.range;
  document.getElementById('pl-custom-range').style.display = range === 'custom' ? 'flex' : 'none';
  if (range === 'today') { plRange = { from: todayIso(), to: todayIso() }; loadProfitLoss(); }
  if (range === 'week') { plRange = { from: firstOfWeekIso(), to: todayIso() }; loadProfitLoss(); }
  if (range === 'month') { plRange = { from: firstOfMonthIso(), to: todayIso() }; loadProfitLoss(); }
});

document.getElementById('pl-custom-apply').addEventListener('click', () => {
  const from = document.getElementById('pl-from').value;
  const to = document.getElementById('pl-to').value;
  if (!from || !to) return;
  plRange = { from, to };
  loadProfitLoss();
});

document.getElementById('pl-pdf-btn').addEventListener('click', async () => {
  try {
    const blob = await Api.getBlob(`/api/reports/profit-loss?from=${plRange.from}&to=${plRange.to}`);
    openPdfBlob(blob, `profit-loss-${plRange.from}-to-${plRange.to}.pdf`);
  } catch (err) { handleError(err); }
});

document.getElementById('cs-generate-btn').addEventListener('click', async () => {
  const id = document.getElementById('cs-customer').value;
  const from = document.getElementById('cs-from').value;
  const to = document.getElementById('cs-to').value;
  if (!id) { showToast('Add a customer first', 'error'); return; }
  try {
    if (id === 'ALL') {
      const blob = await Api.getBlob(`/api/reports/customer-statement/all?from=${from}&to=${to}`);
      openPdfBlob(blob, `all-customer-statements-${from}-to-${to}.pdf`);
    } else {
      const blob = await Api.getBlob(`/api/reports/customer-statement/${id}?from=${from}&to=${to}`);
      openPdfBlob(blob, `customer-statement-${id}.pdf`);
    }
  } catch (err) { handleError(err); }
});

document.getElementById('sp-generate-btn').addEventListener('click', async () => {
  const id = document.getElementById('sp-supplier').value;
  if (!id) { showToast('Add a supplier first', 'error'); return; }
  try {
    const blob = await Api.getBlob(`/api/reports/supplier-purchase/${id}`);
    openPdfBlob(blob, `supplier-purchase-${id}.pdf`);
  } catch (err) { handleError(err); }
});

document.getElementById('ds-generate-btn').addEventListener('click', async () => {
  const date = document.getElementById('ds-date').value;
  try {
    const blob = await Api.getBlob(`/api/reports/daily-sales?date=${date}`);
    openPdfBlob(blob, `daily-sales-${date}.pdf`);
  } catch (err) { handleError(err); }
});

document.getElementById('ex-generate-btn').addEventListener('click', async () => {
  const from = document.getElementById('ex-from').value;
  const to = document.getElementById('ex-to').value;
  try {
    const blob = await Api.getBlob(`/api/reports/expenses?from=${from}&to=${to}`);
    openPdfBlob(blob, `expenses-${from}-to-${to}.pdf`);
  } catch (err) { handleError(err); }
});

document.getElementById('vt-generate-btn').addEventListener('click', async () => {
  const vehicleNumber = document.getElementById('vt-vehicle').value;
  const from = document.getElementById('vt-from').value;
  const to = document.getElementById('vt-to').value;
  if (!vehicleNumber) { showToast('Add a vehicle first', 'error'); return; }
  try {
    const blob = await Api.getBlob(`/api/reports/vehicle-trips?vehicleNumber=${encodeURIComponent(vehicleNumber)}&from=${from}&to=${to}`);
    openPdfBlob(blob, `vehicle-${vehicleNumber}-${from}-to-${to}.pdf`);
  } catch (err) { handleError(err); }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadReportsPage, 50);
});
