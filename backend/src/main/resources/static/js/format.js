function fmtMoney(value) {
  if (value === null || value === undefined || value === '') return '\u20b9 0.00';
  const n = Number(value);
  return '\u20b9 ' + n.toLocaleString('en-IN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}

function fmtKg(value) {
  if (value === null || value === undefined || value === '') return '-';
  return Number(value).toFixed(2) + ' kg';
}

function fmtDate(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (isNaN(d)) return value;
  return d.toLocaleDateString('en-IN', { day: '2-digit', month: 'short', year: 'numeric' });
}

function fmtDateTime(value) {
  if (!value) return '-';
  const d = new Date(value);
  if (isNaN(d)) return value;
  return d.toLocaleString('en-IN', { day: '2-digit', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' });
}

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function daysAgoIso(n) {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return d.toISOString().slice(0, 10);
}

function firstOfMonthIso() {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

function firstOfWeekIso() {
  const d = new Date();
  const day = d.getDay() === 0 ? 6 : d.getDay() - 1; // Monday start
  d.setDate(d.getDate() - day);
  return d.toISOString().slice(0, 10);
}

function statusBadge(status) {
  if (!status) return '';
  const cls = 'badge-' + status.toLowerCase();
  return `<span class="badge ${cls}">${status}</span>`;
}

function escapeHtml(str) {
  if (str === null || str === undefined) return '';
  return String(str)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function showToast(message, type) {
  let container = document.getElementById('toast-container');
  if (!container) {
    container = document.createElement('div');
    container.id = 'toast-container';
    document.body.appendChild(container);
  }
  const toast = document.createElement('div');
  toast.className = 'toast' + (type === 'error' ? ' error' : '');
  toast.textContent = message;
  container.appendChild(toast);
  setTimeout(() => toast.remove(), 4000);
}

function handleError(err) {
  console.error(err);
  showToast(err.message || 'Something went wrong', 'error');
}
