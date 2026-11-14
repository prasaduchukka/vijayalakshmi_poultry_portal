function renderHero(fullName) {
  const hour = new Date().getHours();
  const timeGreeting = hour < 12 ? 'Good morning' : hour < 17 ? 'Good afternoon' : 'Good evening';
  document.getElementById('dash-greeting').textContent = `${timeGreeting}${fullName ? ', ' + fullName : ''}`;
  document.getElementById('dash-date').textContent = new Date().toLocaleDateString('en-IN', {
    weekday: 'long', day: 'numeric', month: 'long', year: 'numeric'
  });
  document.getElementById('farm-art').innerHTML = Illustrations.farmScene;
  renderFeatureStrip('feature-strip');
}

async function loadDashboard() {
  try {
    const d = await Api.get('/api/dashboard');

    // Only the three metrics the owner asked to keep on the dashboard.
    const cards = [
      { label: "Today's Sales", value: fmtMoney(d.todaysSales), icon: 'payments' },
      { label: "Supplier Outstanding", value: fmtMoney(d.supplierOutstandingTotal), cls: 'amber', icon: 'suppliers' },
      { label: "Today's Expenses", value: fmtMoney(d.todaysExpenses), cls: 'red', icon: 'expenses' },
    ];

    document.getElementById('stat-cards').innerHTML = cards.map(c => `
      <div class="stat-card ${c.cls || ''}">
        <div class="stat-card-top">
          <div class="stat-label">${c.label}</div>
          <span class="stat-icon">${Icons[c.icon]}</span>
        </div>
        <div class="stat-value">${c.value}</div>
      </div>
    `).join('');

    const deliveriesBody = document.getElementById('recent-deliveries-body');
    deliveriesBody.innerHTML = d.recentDeliveries.length ? d.recentDeliveries.map(x => `
      <tr>
        <td>${fmtDate(x.deliveryDate)}</td>
        <td>${escapeHtml(x.customerName)}</td>
        <td class="text-right mono">${fmtMoney(x.salesAmount)}</td>
      </tr>
    `).join('') : '<tr><td colspan="3" class="table-empty">No deliveries yet</td></tr>';

    const paymentsBody = document.getElementById('recent-payments-body');
    paymentsBody.innerHTML = d.recentPayments.length ? d.recentPayments.map(x => `
      <tr>
        <td>${fmtDate(x.paymentDate)}</td>
        <td>${escapeHtml(x.customerName)}</td>
        <td>${x.paymentMethod}</td>
        <td class="text-right mono">${fmtMoney(x.amount)}</td>
      </tr>
    `).join('') : '<tr><td colspan="4" class="table-empty">No payments yet</td></tr>';

  } catch (err) {
    handleError(err);
  }
}

document.addEventListener('DOMContentLoaded', () => {
  renderHero();
  setTimeout(loadDashboard, 50); // let requireAuth() settle first
});
