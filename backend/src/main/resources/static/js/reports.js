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
    document.getElementById('sp-supplier').innerHTML =
      `<option value="ALL">\u2014 All Suppliers \u2014</option>` +
      suppliers.map(s => `<option value="${s.id}">${escapeHtml(s.supplierName)}</option>`).join('');
    document.getElementById('vt-vehicle').innerHTML = vehicles.length
      ? vehicles.map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('')
      : '<option value="">No vehicles added yet</option>';

    document.getElementById('cs-from').value = firstOfMonthIso();
    document.getElementById('cs-to').value = todayIso();
    document.getElementById('sp-from').value = firstOfMonthIso();
    document.getElementById('sp-to').value = todayIso();
    document.getElementById('ex-from').value = firstOfMonthIso();
    document.getElementById('ex-to').value = todayIso();
    document.getElementById('vt-from').value = firstOfMonthIso();
    document.getElementById('vt-to').value = todayIso();

  } catch (err) {
    handleError(err);
  }
}

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
  const from = document.getElementById('sp-from').value;
  const to = document.getElementById('sp-to').value;
  if (!id) { showToast('Add a supplier first', 'error'); return; }
  if (!from || !to) { showToast('Choose From and To dates', 'error'); return; }
  try {
    const blob = await Api.getBlob(`/api/reports/supplier-purchase/${id === 'ALL' ? 'all' : id}?from=${from}&to=${to}`);
    openPdfBlob(blob, `supplier-purchase-${id === 'ALL' ? 'all' : id}-${from}-to-${to}.pdf`);
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
