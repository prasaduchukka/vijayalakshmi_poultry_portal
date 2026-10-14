// Builds the sticky horizontal top-nav shell around the page's #page-content.
//
// IMPORTANT: this moves the *actual* #page-content DOM node into the shell
// (not a re-serialized HTML string). Page scripts (customers.js, deliveries.js,
// etc.) attach their button listeners to the original elements before this
// runs - if we rebuilt the HTML from a string here, all of those listeners
// would be silently destroyed and every button would go dead. Moving the
// real node keeps every listener intact.

const NAV_ITEMS = [
  { page: 'dashboard', label: 'Dashboard', icon: 'dashboard', href: 'dashboard.html' },
  { page: 'purchases', label: 'Purchases', icon: 'purchases', href: 'purchases.html' },
  { page: 'deliveries', label: 'Deliveries', icon: 'deliveries', href: 'deliveries.html' },
  { page: 'payments', label: 'Payments', icon: 'payments', href: 'payments.html' },
  { page: 'customers', label: 'Customers', icon: 'customers', href: 'customers.html' },
  { page: 'vehicles', label: 'Vehicles', icon: 'truck', href: 'vehicles.html' },
  { page: 'expenses', label: 'Expenses', icon: 'expenses', href: 'expenses.html' },
  { page: 'reports', label: 'Reports', icon: 'reports', href: 'reports.html' },
];

function buildShell() {
  const body = document.body;
  const currentPage = body.dataset.page || '';
  const title = body.dataset.title || '';
  const isRootIndex = !location.pathname.includes('/pages/');
  const base = isRootIndex ? 'pages/' : '';

  // Grab the REAL node (not its HTML string) so we can move it, not recreate it.
  const pageContentNode = document.getElementById('page-content');

  const navHtml = NAV_ITEMS.map(item => `
    <a href="${base}${item.href}" class="nav-link ${item.page === currentPage ? 'active' : ''}">
      <span class="nav-icon">${Icons[item.icon]}</span>
      <span class="nav-label">${item.label}</span>
    </a>`).join('');

  const shell = document.createElement('div');
  shell.id = 'app-shell';
  shell.innerHTML = `
    <header class="topnav">
      <div class="topnav-inner">
        <a href="${isRootIndex ? '#' : '../pages/dashboard.html'}" class="brand">
          <span class="brand-logo"><img src="${isRootIndex ? "assets/" : "../assets/"}vijayalakshmi-poultry-logo.png" alt="Vijayalakshmi Poultry Farm logo"></span>
          <span class="brand-text">
            <span class="brand-name">Vijayalakshmi Poultry Farm</span>
            <span class="brand-tag">Narsipuram &middot; Admin</span>
          </span>
        </a>
        <button class="nav-toggle" id="nav-toggle" aria-label="Toggle menu">${Icons.menu}</button>
        <nav class="topnav-links" id="topnav-links">${navHtml}</nav>
        <div class="topnav-right">
          <span class="user-chip" id="user-chip">&hellip;</span>
          <a href="${base}account.html" class="btn-outline btn-sm" style="text-decoration:none;">Account</a>
          <button class="btn-outline btn-sm" id="logout-btn">${Icons.logout}<span>Log out</span></button>
        </div>
      </div>
      <div class="topnav-title-bar">
        <div class="topnav-title-inner">
          <h1>${title}</h1>
        </div>
      </div>
    </header>
    <main class="page-shell-content"></main>
  `;

  document.body.prepend(shell);

  // Move (not clone) the original content node into its new home.
  const mount = shell.querySelector('.page-shell-content');
  if (pageContentNode) mount.appendChild(pageContentNode);

  document.getElementById('nav-toggle').addEventListener('click', () => {
    document.getElementById('topnav-links').classList.toggle('open');
  });

  document.getElementById('logout-btn').addEventListener('click', async () => {
    try { await Api.post('/api/auth/logout'); } catch (e) { /* ignore */ }
    window.location.href = (isRootIndex ? 'index.html' : '../index.html');
  });
}

let currentUser = null;

async function requireAuth() {
  try {
    const me = await Api.get('/api/auth/me');
    currentUser = me;
    window.currentUser = me;
    const chip = document.getElementById('user-chip');
    if (chip) chip.textContent = (me.fullName || me.username) + (me.role === 'ADMIN' ? ' (Admin)' : ' (Staff)');

    if (me.role === 'ADMIN') {
      const isRootIndex = !location.pathname.includes('/pages/');
      const base = isRootIndex ? 'pages/' : '';
      const nav = document.getElementById('topnav-links');
      if (nav && !nav.querySelector('[data-page="users"]')) {
        const link = document.createElement('a');
        link.href = base + 'users.html';
        link.className = 'nav-link' + (document.body.dataset.page === 'users' ? ' active' : '');
        link.dataset.page = 'users';
        link.innerHTML = `<span class="nav-icon">${Icons.handshake}</span><span class="nav-label">Staff</span>`;
        nav.appendChild(link);
      }
    }
    return me;
  } catch (e) {
    const isRootIndex = !location.pathname.includes('/pages/');
    window.location.href = (isRootIndex ? 'index.html' : '../index.html');
    throw e;
  }
}

/** True once requireAuth() has resolved and the signed-in user is an Admin. */
function isAdmin() {
  return !!(window.currentUser && window.currentUser.role === 'ADMIN');
}

/**
 * Turns normal data tables into readable key/value cards on small screens.
 * The page scripts can keep rendering ordinary <table> rows; this helper
 * derives each cell label from the table header and keeps working when rows
 * are replaced after an API call.
 */
function enhanceResponsiveTables(root = document) {
  // querySelectorAll() does not include root itself when root is a <table>.
  // The MutationObserver below passes the table as root, so explicitly include it.
  const tables = [];
  if (root && root.nodeType === 1 && root.tagName === 'TABLE') tables.push(root);
  if (root && root.querySelectorAll) tables.push(...root.querySelectorAll('table'));

  tables.forEach(table => {
    table.classList.add('mobile-responsive');
    const ths = Array.from(table.querySelectorAll('thead th'));
    const headers = ths.map((th, index) => {
      const label = th.textContent.trim().replace(/\s+/g, ' ');
      return label || (index === ths.length - 1 ? 'Actions' : `Field ${index + 1}`);
    });

    table.querySelectorAll('tbody tr').forEach(row => {
      const cells = Array.from(row.children).filter(el => el.tagName === 'TD');
      if (cells.some(cell => cell.hasAttribute('colspan'))) return;

      cells.forEach((cell, index) => {
        if (headers[index]) cell.dataset.label = headers[index];
        if (index === cells.length - 1 && headers[index] === 'Actions') {
          cell.dataset.mobileActions = 'true';
        }
      });

      // Turn Edit/Delete text buttons into compact icon buttons on mobile.
      // Desktop keeps the familiar text labels.
      cells.forEach(cell => {
        cell.querySelectorAll('button').forEach(button => {
          const text = button.textContent.trim().toLowerCase();
          if (text !== 'edit' && text !== 'delete') return;
          if (button.dataset.mobileActionReady === 'true') return;
          button.dataset.mobileActionReady = 'true';
          button.classList.add('mobile-action-button');
          button.dataset.action = text;
          button.setAttribute('aria-label', text === 'edit' ? 'Edit' : 'Delete');
          button.title = text === 'edit' ? 'Edit' : 'Delete';
          const icon = text === 'edit' ? Icons.edit : Icons.trash;
          button.innerHTML = `<span class="action-label">${text === 'edit' ? 'Edit' : 'Delete'}</span><span class="mobile-action-icon" aria-hidden="true">${icon}</span>`;
        });
      });
    });
  });
}

function initResponsiveTables() {
  enhanceResponsiveTables();
  document.querySelectorAll('table tbody').forEach(tbody => {
    const observer = new MutationObserver(() => {
      const table = tbody.closest('table');
      if (table) enhanceResponsiveTables(table);
    });
    observer.observe(tbody, { childList: true });
  });
}

document.addEventListener('DOMContentLoaded', () => {
  buildShell();
  initResponsiveTables();
  requireAuth();
});
