let allCustomers = [];

async function loadCustomers() {
  try {
    allCustomers = await Api.get('/api/customers');
    renderCustomers(allCustomers);
  } catch (err) {
    handleError(err);
  }
}

function renderCustomers(list) {
  const admin = isAdmin();
  const body = document.getElementById('customers-body');
  if (!list.length) {
    body.innerHTML = '<tr><td colspan="6" class="table-empty">No customers yet. Click "Add Customer" to get started.</td></tr>';
    return;
  }
  body.innerHTML = list.map(c => `
    <tr>
      <td><a href="customer-detail.html?id=${c.id}">${escapeHtml(c.chickenCenterName)}</a></td>
      <td>${escapeHtml(c.ownerContactPerson || '-')}</td>
      <td>${escapeHtml(c.phoneNumber || '-')}</td>
      <td class="text-right mono">${fmtMoney(c.openingBalance)}</td>
      <td>${statusBadge(c.status)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editCustomer(${c.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deleteCustomer(${c.id})">Delete</button>` : '<span class="text-muted" style="font-size:12px;">View only</span>'}
      </td>
    </tr>
  `).join('');
}

async function deleteCustomer(id) {
  const c = allCustomers.find(x => x.id === id);
  const name = c ? c.chickenCenterName : 'this customer';
  if (!confirm(`Permanently delete "${name}" and ALL their deliveries, every delivery-linked payment, other payments, feed sales, orders, and ledger history. Related delivery history will disappear from reports as well. This cannot be undone.`)) return;
  try {
    await Api.del(`/api/customers/${id}`);
    showToast('Customer deleted');
    await loadCustomers();
  } catch (err) { handleError(err); }
}

function editCustomer(id) {
  const c = allCustomers.find(x => x.id === id);
  if (!c) return;
  document.getElementById('customer-modal-title').textContent = 'Edit Customer';
  document.getElementById('c-id').value = c.id;
  document.getElementById('c-name').value = c.chickenCenterName;
  document.getElementById('c-contact').value = c.ownerContactPerson || '';
  document.getElementById('c-phone').value = c.phoneNumber || '';
  document.getElementById('c-address').value = c.address || '';
  document.getElementById('c-opening-balance').value = c.openingBalance;
  document.getElementById('c-opening-balance').disabled = false;
  document.getElementById('opening-balance-hint').textContent = 'Admin can update this after saving. The opening ledger entry and running balances will be recalculated; deliveries and payments remain unchanged.';
  document.getElementById('c-status').value = c.status;
  document.getElementById('c-notes').value = c.notes || '';
  document.getElementById('customer-form-error').innerHTML = '';
  openModal('customer-modal');
}

document.getElementById('add-customer-btn').addEventListener('click', () => {
  document.getElementById('customer-form').reset();
  document.getElementById('customer-modal-title').textContent = 'Add Customer';
  document.getElementById('c-id').value = '';
  document.getElementById('c-opening-balance').disabled = false;
  document.getElementById('c-opening-balance').value = 0;
  document.getElementById('opening-balance-hint').textContent = 'You can edit this opening balance later as an admin.';
  document.getElementById('customer-form-error').innerHTML = '';
  openModal('customer-modal');
});

document.getElementById('customer-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('c-id').value;
  const payload = {
    chickenCenterName: document.getElementById('c-name').value.trim(),
    ownerContactPerson: document.getElementById('c-contact').value.trim(),
    phoneNumber: document.getElementById('c-phone').value.trim(),
    address: document.getElementById('c-address').value.trim(),
    openingBalance: Number(document.getElementById('c-opening-balance').value || 0),
    status: document.getElementById('c-status').value,
    notes: document.getElementById('c-notes').value.trim(),
  };
  const errorBox = document.getElementById('customer-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('customer-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/customers/${id}`, payload);
      showToast('Customer updated');
    } else {
      await Api.post('/api/customers', payload);
      showToast('Customer added');
    }
    closeModal('customer-modal');
    await loadCustomers();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('search-box').addEventListener('input', (e) => {
  const q = e.target.value.toLowerCase();
  renderCustomers(allCustomers.filter(c => c.chickenCenterName.toLowerCase().includes(q)));
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadCustomers, 50);
});
