let allUsers = [];

async function loadUsers() {
  try {
    allUsers = await Api.get('/api/users');
    renderUsers();
  } catch (err) { handleError(err); }
}

function renderUsers() {
  const staff = allUsers.filter(u => u.role !== 'CUSTOMER');
  const customers = allUsers.filter(u => u.role === 'CUSTOMER');
  document.getElementById('users-body').innerHTML = staff.length ? staff.map(u => `
    <tr><td>${escapeHtml(u.username)}</td><td>${escapeHtml(u.fullName || '-')}</td>
    <td><span class="badge ${u.role === 'ADMIN' ? 'badge-confirmed' : 'badge-pending'}">${u.role === 'ADMIN' ? 'ADMIN' : 'GUMASTA'}</span></td>
    <td>${fmtDate(u.createdDate)}</td><td><button class="btn-danger btn-sm" onclick="deleteUser(${u.id}, '${escapeHtml(u.username)}')">Delete</button></td></tr>
  `).join('') : '<tr><td colspan="5" class="table-empty">No staff accounts yet</td></tr>';

  document.getElementById('customer-users-body').innerHTML = customers.length ? customers.map(u => `
    <tr><td>${escapeHtml(u.fullName || '-')}</td><td>${escapeHtml(u.username)}</td>
    <td><span class="badge badge-confirmed">CUSTOMER</span></td><td>${fmtDate(u.createdDate)}</td>
    <td>${u.enabled ? 'Active' : 'Disabled'}</td>
    <td><button class="btn-danger btn-sm" onclick="deleteUser(${u.id}, '${escapeHtml(u.username)}')">Delete</button></td></tr>
  `).join('') : '<tr><td colspan="6" class="table-empty">No customer portal accounts created yet. Use Portal Login from the Customers page.</td></tr>';
}

async function deleteUser(id, username) {
  if (!confirm(`Permanently delete the account "${username}"? This cannot be undone.`)) return;
  try { await Api.del(`/api/users/${id}`); showToast('Account deleted'); await loadUsers(); }
  catch (err) { handleError(err); }
}

document.getElementById('add-user-btn').addEventListener('click', () => {
  document.getElementById('user-form').reset();
  document.getElementById('user-form-error').innerHTML = '';
  openModal('user-modal');
});

document.getElementById('user-form').addEventListener('submit', async e => {
  e.preventDefault();
  const payload = {
    username: document.getElementById('u-username').value.trim(),
    fullName: document.getElementById('u-fullname').value.trim(),
    password: document.getElementById('u-password').value,
    role: document.getElementById('u-role').value
  };
  const errorBox = document.getElementById('user-form-error');
  const btn = document.getElementById('user-save-btn');
  errorBox.innerHTML = ''; btn.disabled = true;
  try {
    await Api.post('/api/users', payload);
    showToast('Staff account created'); closeModal('user-modal'); await loadUsers();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally { btn.disabled = false; }
});
document.addEventListener('DOMContentLoaded', () => setTimeout(loadUsers, 50));
