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


// ---------- Newest-first ordering and "View more" for long tables ----------

/** Returns a copy sorted newest first: by the given date field, then by id (later entries first). */
function sortRecentFirst(list, dateKey, idKey = 'id') {
  return [...(list || [])].sort((a, b) => {
    const da = String(a[dateKey] || '');
    const db = String(b[dateKey] || '');
    if (da !== db) return da < db ? 1 : -1;
    return Number(b[idKey] || 0) - Number(a[idKey] || 0);
  });
}

const _viewMoreState = {};

/**
 * Renders rows into a table body showing only the first `pageSize` rows (default 5).
 * When more exist, a "View more" button underneath reveals `step` more each click, and
 * "Show less" folds the table back to the first rows. `items` should already be newest first.
 */
function renderWithViewMore(bodyId, items, rowHtml, emptyHtml, pageSize = 5, step = 10) {
  const body = document.getElementById(bodyId);
  const st = _viewMoreState[bodyId] || (_viewMoreState[bodyId] = { shown: pageSize });
  if (st.items !== items) { st.shown = pageSize; st.items = items; }  // fresh data: start from the top again

  const draw = () => {
    body.innerHTML = items.length ? items.slice(0, st.shown).map(rowHtml).join('') : emptyHtml;
    const table = body.closest('table');
    let bar = document.getElementById(bodyId + '-viewmore');
    if (!bar) {
      bar = document.createElement('div');
      bar.id = bodyId + '-viewmore';
      bar.className = 'view-more-bar';
      table.insertAdjacentElement('afterend', bar);
    }
    const remaining = items.length - st.shown;
    if (items.length <= pageSize) { bar.style.display = 'none'; return; }
    bar.style.display = '';
    bar.innerHTML =
      (remaining > 0 ? `<button type="button" class="btn-outline btn-sm" data-vm="more">View more (${remaining} more)</button>` : '') +
      (st.shown > pageSize ? `<button type="button" class="btn-outline btn-sm" data-vm="less">Show less</button>` : '');
    bar.onclick = (e) => {
      const b = e.target.closest('button[data-vm]');
      if (!b) return;
      st.shown = b.dataset.vm === 'more' ? Math.min(items.length, st.shown + step) : pageSize;
      draw();
    };
  };
  draw();
}
