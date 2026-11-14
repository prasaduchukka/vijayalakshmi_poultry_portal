let allUsers = [];

async function loadUsers() {
  try {
    allUsers = await Api.get('/api/users');
    renderUsers();
  } catch (err) {
    // Non-admins get a 403 - bounce them back to the dashboard rather than show an error wall.
    if (err.message && err.message.includes('403')) {
      window.location.href = 'dashboard.html';
      return;
    }
    handleError(err);
  }
}

function renderUsers() {
  const body = document.getElementById('users-body');
  body.innerHTML = allUsers.length ? allUsers.map(u => `
    <tr>
      <td>${escapeHtml(u.username)}</td>
      <td>${escapeHtml(u.fullName || '-')}</td>
      <td><span class="badge ${u.role === 'ADMIN' ? 'badge-confirmed' : 'badge-pending'}">${u.role === 'ADMIN' ? 'Admin' : 'Gumasta'}</span></td>
      <td>${fmtDate(u.createdDate)}</td>
      <td><button class="btn-danger btn-sm" onclick="deleteUser(${u.id}, '${escapeHtml(u.username)}')">Delete</button></td>
    </tr>
  `).join('') : '<tr><td colspan="5" class="table-empty">No staff accounts yet</td></tr>';
}

async function deleteUser(id, username) {
  if (!confirm(`Permanently delete the account "${username}"? This cannot be undone.`)) return;
  try {
    await Api.del(`/api/users/${id}`);
    showToast('Account deleted');
    await loadUsers();
  } catch (err) {
    handleError(err);
  }
}

document.getElementById('add-user-btn').addEventListener('click', () => {
  document.getElementById('user-form').reset();
  document.getElementById('user-form-error').innerHTML = '';
  openModal('user-modal');
});

document.getElementById('user-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    username: document.getElementById('u-username').value.trim(),
    fullName: document.getElementById('u-fullname').value.trim(),
    password: document.getElementById('u-password').value,
    role: document.getElementById('u-role').value,
  };
  const errorBox = document.getElementById('user-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('user-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/users', payload);
    showToast('Staff account created');
    closeModal('user-modal');
    await loadUsers();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadUsers, 50);
});
