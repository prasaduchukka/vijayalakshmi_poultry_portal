async function loadAccountPage() {
  // Show the Farm Details section only for Admins.
  const check = setInterval(() => {
    if (window.currentUser) {
      clearInterval(check);
      if (isAdmin()) {
        document.getElementById('farm-settings-card').style.display = '';
        document.getElementById('recovery-email-card').style.display = '';
        loadFarmSettings();
        loadRecoveryEmail();
      }
    }
  }, 50);
}


async function loadRecoveryEmail() {
  try {
    const result = await Api.get('/api/auth/recovery-email');
    document.getElementById('recovery-email-address').value = result.email || '';
  } catch (err) {
    const box = document.getElementById('recovery-email-error');
    if (box) box.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  }
}

const recoveryEmailForm = document.getElementById('recovery-email-form');
if (recoveryEmailForm) {
  recoveryEmailForm.addEventListener('submit', async (e) => {
    e.preventDefault();
    const errorBox = document.getElementById('recovery-email-error');
    const successBox = document.getElementById('recovery-email-success');
    errorBox.innerHTML = '';
    successBox.innerHTML = '';
    const btn = document.getElementById('recovery-email-save-btn');
    btn.disabled = true;
    try {
      const result = await Api.post('/api/auth/recovery-email', {
        email: document.getElementById('recovery-email-address').value.trim(),
        currentPassword: document.getElementById('recovery-email-current-password').value
      });
      successBox.innerHTML = `<div class="alert alert-success">${escapeHtml(result.message || 'Recovery email saved.')}</div>`;
      document.getElementById('recovery-email-current-password').value = '';
    } catch (err) {
      errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
    } finally {
      btn.disabled = false;
    }
  });
}

async function loadFarmSettings() {
  try {
    const settings = await Api.get('/api/settings/farm');
    document.getElementById('f-farm-name').value = settings.farmName || '';
    document.getElementById('f-owner-name').value = settings.ownerName || '';
    document.getElementById('f-owner-phone').value = settings.ownerPhone || '';
  } catch (err) {
    handleError(err);
  }
}

document.getElementById('username-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorBox = document.getElementById('username-form-error');
  const successBox = document.getElementById('username-form-success');
  errorBox.innerHTML = '';
  successBox.innerHTML = '';
  const btn = document.getElementById('username-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/auth/change-username', {
      newUsername: document.getElementById('u-new-username').value.trim(),
      currentPassword: document.getElementById('u-current-password').value,
    });
    successBox.innerHTML = '<div class="alert alert-success">Username updated. Redirecting to login...</div>';
    setTimeout(() => { window.location.href = '../index.html'; }, 1800);
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
    btn.disabled = false;
  }
});

document.getElementById('password-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorBox = document.getElementById('password-form-error');
  const successBox = document.getElementById('password-form-success');
  errorBox.innerHTML = '';
  successBox.innerHTML = '';
  const btn = document.getElementById('password-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/auth/change-password', {
      currentPassword: document.getElementById('p-current-password').value,
      newPassword: document.getElementById('p-new-password').value,
    });
    successBox.innerHTML = '<div class="alert alert-success">Password updated.</div>';
    document.getElementById('password-form').reset();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('farm-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorBox = document.getElementById('farm-form-error');
  const successBox = document.getElementById('farm-form-success');
  errorBox.innerHTML = '';
  successBox.innerHTML = '';
  const btn = document.getElementById('farm-save-btn');
  btn.disabled = true;
  try {
    await Api.put('/api/settings/farm', {
      farmName: document.getElementById('f-farm-name').value.trim(),
      ownerName: document.getElementById('f-owner-name').value.trim(),
      ownerPhone: document.getElementById('f-owner-phone').value.trim(),
    });
    successBox.innerHTML = '<div class="alert alert-success">Farm details saved.</div>';
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.addEventListener('DOMContentLoaded', loadAccountPage);
