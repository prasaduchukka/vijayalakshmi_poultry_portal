const supplierId = new URLSearchParams(location.search).get('id');

async function loadSupplierDetail() {
  if (!supplierId) {
    document.getElementById('supplier-header-card').innerHTML = '<div class="alert alert-error">No supplier specified.</div>';
    return;
  }
  try {
    const [summary, purchases] = await Promise.all([
      Api.get(`/api/suppliers/${supplierId}/account`),
      Api.get(`/api/purchases?supplierId=${supplierId}`),
    ]);
    renderHeader(summary);
    renderPurchases(purchases);
  } catch (err) {
    handleError(err);
  }
}

function renderHeader(summary) {
  const s = summary.supplier;
  document.title = s.supplierName + ' – Vijayalakshmi Poultry Farm';
  document.getElementById('supplier-header-card').innerHTML = `
    <div class="card-title-row">
      <div>
        <h2 style="margin-bottom:4px;">${escapeHtml(s.supplierName)}</h2>
        <div class="text-muted">${escapeHtml(s.contactPerson || '')} ${s.phoneNumber ? '· ' + escapeHtml(s.phoneNumber) : ''}</div>
        <div class="text-muted">${escapeHtml(s.address || '')}</div>
      </div>
      ${statusBadge(s.status)}
    </div>
  `;
}

// Purchase history only: which vehicle, driver and helper went to load. Newest first.
function renderPurchases(list) {
  const sorted = sortRecentFirst(list, 'purchaseDate');
  renderWithViewMore('purchases-body', sorted, p => `
    <tr>
      <td>${fmtDate(p.purchaseDate)}</td>
      <td>${escapeHtml(p.vehicleNumber || '-')}</td>
      <td>${p.numberOfBirds ?? '-'}</td>
      <td>${p.numberOfBoxes ?? '-'}</td>
      <td>${fmtKg(p.purchaseWeight)}</td>
      <td>${escapeHtml(p.driverName || '-')}</td>
      <td>${escapeHtml(p.helperName || '-')}</td>
    </tr>
  `, '<tr><td colspan="7" class="table-empty">No purchases recorded yet</td></tr>');
}

document.getElementById('generate-pdf-btn').addEventListener('click', async () => {
  const from = document.getElementById('rp-from').value;
  const to = document.getElementById('rp-to').value;
  const qs = from && to ? `?from=${from}&to=${to}` : '';
  try {
    const blob = await Api.getBlob(`/api/reports/supplier-purchase/${supplierId}${qs}`);
    openPdfBlob(blob, `supplier-purchase-${supplierId}.pdf`);
  } catch (err) {
    handleError(err);
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadSupplierDetail, 50);
});
