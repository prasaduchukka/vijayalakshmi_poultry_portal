let feedInventory = null;
let feedSalesCache = [];
let feedCustomersCache = [];
let editingFeedUnitPrice = 0;

async function loadFeedPage() {
  try {
    if (window.authReady) await window.authReady;
    const requests = [Api.get('/api/feed-inventory'), Api.get('/api/feed-sales')];
    requests.push(Api.get('/api/customers').catch(() => []));
    const result = await Promise.all(requests);
    const inventory = result[0], sales = sortRecentFirst(result[1], 'saleDate');
    feedInventory = inventory;
    feedSalesCache = sales;
    feedCustomersCache = result[2] || [];
    document.getElementById('feed-stock').textContent = `${inventory.stockBags ?? 0} bags`;
    document.getElementById('feed-price').textContent = fmtMoney(inventory.bagPrice);
    document.getElementById('feed-sales-count').textContent = sales.length;
    const restockForm = document.getElementById('feed-restock-form');
    if (!isAdmin() && restockForm) restockForm.closest('.card').style.display = 'none';
    document.getElementById('restock-price').value = inventory.bagPrice > 0 ? inventory.bagPrice : '';
    const admin = isAdmin();
    document.getElementById('feed-sales-body').innerHTML = sales.length ? sales.map(s => `
      <tr>
        <td>${fmtDate(s.saleDate)}</td><td>${escapeHtml(s.customerName)}</td>
        <td>${s.bagCount ?? '—'}</td><td>${fmtMoney(s.unitPrice)}</td>
        <td class="text-right mono">${fmtMoney(s.amount)}</td>
        <td class="text-right mono text-green">${fmtMoney(s.amountPaid)}</td>
        <td class="text-right mono"><strong>${fmtMoney(s.balanceDue)}</strong></td>
        <td>${escapeHtml(s.notes || s.description || '-')}</td>
        <td>${admin ? `<button class="btn-outline btn-sm" onclick="editFeedSale(${s.id})">Edit</button>
          <button class="btn-danger btn-sm" onclick="deleteFeedSale(${s.id})">Delete</button>` : '<span class="text-muted">—</span>'}</td>
      </tr>
    `).join('') : '<tr><td colspan="9" class="table-empty">No feed sales recorded yet</td></tr>';
    const options = feedCustomersCache.map(c => `<option value="${c.id}">${escapeHtml(c.chickenCenterName)}</option>`).join('');
    document.getElementById('new-feed-customer').innerHTML = '<option value="">Select customer</option>' + options;
    if (admin) document.getElementById('edit-feed-customer').innerHTML = options;
  } catch (err) { handleError(err); }
}

function updateEditFeedTotal() {
  const bags = Number(document.getElementById('edit-feed-bags').value || 0);
  const price = Number(editingFeedUnitPrice || feedInventory?.bagPrice || 0);
  document.getElementById('edit-feed-total').value = fmtMoney(bags * price);
}
document.getElementById('edit-feed-bags').addEventListener('input', updateEditFeedTotal);

function editFeedSale(id) {
  const sale = feedSalesCache.find(s => s.id === id);
  if (!sale) return;
  editingFeedUnitPrice = Number(sale.unitPrice || feedInventory?.bagPrice || 0);
  document.getElementById('edit-feed-id').value = sale.id;
  document.getElementById('edit-feed-customer').value = sale.customerId;
  document.getElementById('edit-feed-date').value = sale.saleDate;
  document.getElementById('edit-feed-bags').value = sale.bagCount || 1;
  document.getElementById('edit-feed-paid').value = sale.amountPaid || 0;
  document.getElementById('edit-feed-notes').value = sale.notes || '';
  document.getElementById('feed-edit-error').innerHTML = '';
  updateEditFeedTotal();
  openModal('feed-edit-modal');
}

document.getElementById('feed-edit-form').addEventListener('submit', async e => {
  e.preventDefault();
  const id = Number(document.getElementById('edit-feed-id').value);
  const payload = {
    customerId: Number(document.getElementById('edit-feed-customer').value),
    saleDate: document.getElementById('edit-feed-date').value,
    bagCount: Number(document.getElementById('edit-feed-bags').value),
    amountPaid: Number(document.getElementById('edit-feed-paid').value),
    description: `${document.getElementById('edit-feed-bags').value} feed bag(s)`,
    notes: document.getElementById('edit-feed-notes').value.trim(),
    createdBy: window.currentUser?.username || 'admin'
  };
  const btn = document.getElementById('feed-edit-save');
  btn.disabled = true;
  try {
    await Api.put(`/api/feed-sales/${id}`, payload);
    showToast('Feed sale updated');
    closeModal('feed-edit-modal');
    await loadFeedPage();
  } catch (err) {
    document.getElementById('feed-edit-error').innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally { btn.disabled = false; }
});

function updateNewFeedTotal() {
  const bags = Number(document.getElementById('new-feed-bags').value || 0);
  document.getElementById('new-feed-total').value = fmtMoney(bags * Number(feedInventory?.bagPrice || 0));
}
document.getElementById('new-feed-bags').addEventListener('input', updateNewFeedTotal);
document.getElementById('new-feed-sale-btn').addEventListener('click', () => {
  document.getElementById('feed-new-form').reset();
  document.getElementById('new-feed-date').value = new Date().toLocaleDateString('en-CA');
  document.getElementById('new-feed-paid').value = 0;
  document.getElementById('feed-new-error').innerHTML = '';
  document.getElementById('new-feed-hint').textContent =
    `Price per bag: ${fmtMoney(feedInventory?.bagPrice || 0)} · Stock available: ${feedInventory?.stockBags ?? 0} bags. Reduces stock and adds the amount (less any amount paid) to the customer's ledger.`;
  updateNewFeedTotal();
  openModal('feed-new-modal');
});
document.getElementById('feed-new-form').addEventListener('submit', async e => {
  e.preventDefault();
  const btn = document.getElementById('feed-new-save');
  const payload = {
    customerId: Number(document.getElementById('new-feed-customer').value),
    saleDate: document.getElementById('new-feed-date').value,
    bagCount: Number(document.getElementById('new-feed-bags').value),
    amountPaid: Number(document.getElementById('new-feed-paid').value || 0),
    description: `${document.getElementById('new-feed-bags').value} feed bag(s)`,
    notes: document.getElementById('new-feed-notes').value.trim(),
    createdBy: window.currentUser?.username || 'staff'
  };
  if (!payload.customerId) {
    document.getElementById('feed-new-error').innerHTML = '<div class="alert alert-error">Please select a customer.</div>';
    return;
  }
  btn.disabled = true;
  try {
    const saved = await Api.post('/api/feed-sales', payload);
    showToast(`Feed sale recorded: ${saved.bagCount} bags \u00b7 ${fmtMoney(saved.amount)}`);
    closeModal('feed-new-modal');
    await loadFeedPage();
  } catch (err) {
    document.getElementById('feed-new-error').innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally { btn.disabled = false; }
});

async function deleteFeedSale(id) {
  const sale = feedSalesCache.find(s => s.id === id);
  if (!confirm(`Delete this feed sale for ${sale?.customerName || 'this customer'}? The feed bags will be returned to stock and the customer's ledger will be recalculated.`)) return;
  try {
    await Api.del(`/api/feed-sales/${id}`);
    showToast('Feed sale deleted and stock restored');
    await loadFeedPage();
  } catch (err) { handleError(err); }
}

document.getElementById('feed-restock-form').addEventListener('submit', async e => {
  e.preventDefault();
  const error = document.getElementById('restock-error');
  const btn = document.getElementById('restock-save-btn');
  error.innerHTML = ''; btn.disabled = true;
  try {
    await Api.post('/api/feed-inventory/restock', {
      bags: Number(document.getElementById('restock-bags').value),
      bagPrice: Number(document.getElementById('restock-price').value)
    });
    showToast('Feed stock and selling price updated');
    document.getElementById('restock-bags').value = '';
    await loadFeedPage();
  } catch (err) {
    error.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally { btn.disabled = false; }
});
document.addEventListener('DOMContentLoaded', () => setTimeout(loadFeedPage, 100));
