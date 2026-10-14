let allExpenses = [];

async function refreshExpenses() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/expenses';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    allExpenses = await Api.get(url);
    renderExpenses();
  } catch (err) {
    handleError(err);
  }
}

function renderExpenses() {
  const admin = isAdmin();
  const body = document.getElementById('expenses-body');
  body.innerHTML = allExpenses.length ? allExpenses.map(e => `
    <tr>
      <td>${fmtDate(e.expenseDate)}</td>
      <td>${e.category}</td>
      <td>${escapeHtml(e.description || '-')}</td>
      <td class="text-right mono">${fmtMoney(e.amount)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editExpense(${e.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deleteExpense(${e.id})">Delete</button>` : '<span class="text-muted" style="font-size:12px;">View only</span>'}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="5" class="table-empty">No expenses found for this period</td></tr>';

  const total = allExpenses.reduce((sum, e) => sum + Number(e.amount), 0);
  const totalLine = document.getElementById('total-line');
  if (allExpenses.length) {
    totalLine.style.display = 'flex';
    totalLine.innerHTML = `<span>Total Expenses</span><span class="mono">${fmtMoney(total)}</span>`;
  } else {
    totalLine.style.display = 'none';
  }
}

function editExpense(id) {
  const e = allExpenses.find(x => x.id === id);
  if (!e) return;
  document.getElementById('expense-modal-title').textContent = 'Edit Expense';
  document.getElementById('e-id').value = e.id;
  document.getElementById('e-date').value = e.expenseDate;
  document.getElementById('e-category').value = e.category;
  document.getElementById('e-amount').value = e.amount;
  document.getElementById('e-description').value = e.description || '';
  document.getElementById('e-notes').value = e.notes || '';
  document.getElementById('expense-form-error').innerHTML = '';
  openModal('expense-modal');
}

async function deleteExpense(id) {
  if (!confirm('Delete this expense record? This cannot be undone.')) return;
  try {
    await Api.del(`/api/expenses/${id}`);
    showToast('Expense deleted');
    await refreshExpenses();
  } catch (err) {
    handleError(err);
  }
}

document.getElementById('add-expense-btn').addEventListener('click', () => {
  document.getElementById('expense-form').reset();
  document.getElementById('expense-modal-title').textContent = 'Add Expense';
  document.getElementById('e-id').value = '';
  document.getElementById('e-date').value = todayIso();
  document.getElementById('expense-form-error').innerHTML = '';
  openModal('expense-modal');
});

document.getElementById('expense-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('e-id').value;
  const payload = {
    expenseDate: document.getElementById('e-date').value,
    category: document.getElementById('e-category').value,
    amount: Number(document.getElementById('e-amount').value),
    description: document.getElementById('e-description').value.trim(),
    notes: document.getElementById('e-notes').value.trim(),
    createdBy: 'admin',
  };
  const errorBox = document.getElementById('expense-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('expense-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/expenses/${id}`, payload);
      showToast('Expense updated');
    } else {
      await Api.post('/api/expenses', payload);
      showToast('Expense added');
    }
    closeModal('expense-modal');
    await refreshExpenses();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('apply-filter-btn').addEventListener('click', refreshExpenses);

document.addEventListener('DOMContentLoaded', () => {
  document.getElementById('from-filter').value = firstOfMonthIso();
  document.getElementById('to-filter').value = todayIso();
  setTimeout(refreshExpenses, 50);
});
