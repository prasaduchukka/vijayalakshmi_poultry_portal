let allDeliveries = [];
let deliverySession = []; // running list saved this modal session (Save & Next)
let deliveryTripOptionsCache = [];
let currentDeliveryTripId = null; // when editing, the trip already linked to this delivery
let currentDeliveryOriginalCustomerId = null;
let sessionTripId = null; // once set, every further stop in this Save & Next session sticks to this trip

async function loadDeliveriesPage() {
  try {
    const [customers, vehicles] = await Promise.all([
      Api.get('/api/customers'),
      Api.get('/api/vehicles'),
    ]);
    const options = customers.map(c => `<option value="${c.id}">${escapeHtml(c.chickenCenterName)}</option>`).join('');
    document.getElementById('d-customer').innerHTML = options;
    document.getElementById('p-customer').innerHTML = options;
    document.getElementById('d-vehicle').innerHTML = '<option value="">None</option>' + vehicles
      .map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('');
    await refreshDeliveries();
  } catch (err) {
    handleError(err);
  }
}

async function refreshDeliveries() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/deliveries';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    allDeliveries = await Api.get(url);
    renderDeliveries(allDeliveries);
  } catch (err) {
    handleError(err);
  }
}

function renderDeliveries(list) {
  const admin = isAdmin();
  const body = document.getElementById('deliveries-body');
  body.innerHTML = list.length ? list.map(d => `
    <tr>
      <td>${fmtDate(d.deliveryDate)}</td>
      <td><a href="customer-detail.html?id=${d.customerId}">${escapeHtml(d.customerName)}</a></td>
      <td>${escapeHtml(d.vehicleNumber || '-')}${d.tripNumber ? ` <span class="trip-number-badge">Trip ${d.tripNumber}</span>` : ''}</td>
      <td>${d.numberOfBirds ?? '-'}</td>
      <td>${fmtKg(d.dispatchWeight)}</td>
      <td>${fmtMoney(d.sellingRate)}</td>
      <td class="text-right mono">${fmtMoney(d.salesAmount)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editDelivery(${d.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deleteDelivery(${d.id})">Delete</button>` : ''}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="8" class="table-empty">No deliveries found for this period</td></tr>';

  const total = list.reduce((sum, d) => sum + Number(d.salesAmount), 0);
  const totalLine = document.getElementById('total-line');
  if (list.length) {
    totalLine.style.display = 'flex';
    totalLine.innerHTML = `<span>Total Sales</span><span class="mono">${fmtMoney(total)}</span>`;
  } else {
    totalLine.style.display = 'none';
  }
}

async function deleteDelivery(id) {
  if (!confirm(`Permanently delete this delivery?

This will delete the delivery sale, EVERY payment recorded specifically for this delivery, its payment ledger entries, its delivery ledger entry, and restore a linked order if no other delivery uses it.

This cannot be undone.`)) return;
  try {
    await Api.del(`/api/deliveries/${id}`);
    showToast('Delivery deleted');
    await refreshDeliveries();
  } catch (err) {
    handleError(err);
  }
}

function recalcDeliveryPreview() {
  const dispatch = Number(document.getElementById('d-dispatch').value || 0);
  const rate = Number(document.getElementById('d-rate').value || 0);
  document.getElementById('d-sales-amount').value = '\u20b9 ' + (dispatch * rate).toFixed(2);
}
['d-dispatch', 'd-rate'].forEach(id =>
  document.getElementById(id).addEventListener('input', recalcDeliveryPreview));

// ---------- Trip picker ----------
// Trips are only ever STARTED from the Purchases tab (where the vehicle is
// loaded at the supplier). Deliveries can only pick which already-started
// trip a stop belongs to - never create one - so there is no "Start New
// Trip" option here, unlike Purchases.

function toggleDeliveryTripSection() {
  const vehicle = document.getElementById('d-vehicle').value;
  document.getElementById('d-trip-existing-row').style.display = vehicle ? '' : 'none';
  if (vehicle) refreshDeliveryTripOptions();
}

async function refreshDeliveryTripOptions() {
  const vehicle = document.getElementById('d-vehicle').value;
  const date = document.getElementById('d-date').value;
  const existingSelect = document.getElementById('d-trip-existing');
  if (!vehicle || !date) {
    deliveryTripOptionsCache = [];
    existingSelect.innerHTML = '';
    return;
  }
  deliveryTripOptionsCache = await fetchTripOptions(vehicle, date);
  existingSelect.innerHTML = deliveryTripOptionsCache.length
    ? '<option value="">Select a trip\u2026</option>' + deliveryTripOptionsCache.map(t => `<option value="${t.id}">${escapeHtml(tripOptionLabel(t))}</option>`).join('')
    : '<option value="">No trips recorded yet</option>';
  document.getElementById('d-trip-existing-hint').textContent = deliveryTripOptionsCache.length
    ? 'Choose the trip this stop belongs to.'
    : 'No trip has been started for this vehicle today \u2014 record the pickup under Purchases first.';

  // Sticky within a Save & Next session, or when editing a delivery that
  // already belongs to a trip: keep that same trip selected.
  const preselectId = sessionTripId || currentDeliveryTripId;
  if (preselectId && deliveryTripOptionsCache.some(t => t.id === preselectId)) {
    existingSelect.value = String(preselectId);
  }
}

document.getElementById('d-vehicle').addEventListener('change', toggleDeliveryTripSection);
document.getElementById('d-date').addEventListener('change', () => {
  if (document.getElementById('d-vehicle').value) refreshDeliveryTripOptions();
});

/** Builds the explicit TripSelectionRequest - a delivery can only continue an
 *  existing trip (started under Purchases), never create one. */
function buildDeliveryTripSelection() {
  const vehicle = document.getElementById('d-vehicle').value;
  if (!vehicle) return null;
  const tripId = document.getElementById('d-trip-existing').value;
  if (!tripId) throw new Error('Please select a trip for this vehicle, or set Vehicle back to "None" if this delivery isn\'t part of a tracked trip.');
  return { tripId: Number(tripId) };
}

function clearDeliveryEntryFields() {
  // Clears only the per-stop fields, NOT the sticky vehicle/trip section above.
  document.getElementById('d-birds').value = '';
  document.getElementById('d-dispatch').value = '';
  document.getElementById('d-rate').value = '';
  document.getElementById('d-sales-amount').value = '';
  document.getElementById('d-notes').value = '';
  document.getElementById('d-payment-amount').value = '';
}

document.getElementById('add-delivery-btn').addEventListener('click', () => {
  deliverySession = [];
  sessionTripId = null;
  currentDeliveryTripId = null;
  currentDeliveryOriginalCustomerId = null;
  renderDeliverySession();
  document.getElementById('delivery-form').reset();
  document.getElementById('d-id').value = '';
  document.getElementById('delivery-modal-title').textContent = 'Record Delivery';
  document.getElementById('d-date').value = todayIso();
  document.getElementById('d-trip-existing-row').style.display = 'none';
  document.getElementById('delivery-form-error').innerHTML = '';
  openModal('delivery-modal');
});

function editDelivery(id) {
  const d = allDeliveries.find(x => x.id === id);
  if (!d) return;
  deliverySession = [];
  sessionTripId = null;
  renderDeliverySession();
  document.getElementById('delivery-modal-title').textContent = 'Edit Delivery';
  document.getElementById('d-id').value = d.id;
  document.getElementById('d-customer').value = d.customerId;
  document.getElementById('d-date').value = d.deliveryDate;
  document.getElementById('d-birds').value = d.numberOfBirds ?? '';
  document.getElementById('d-dispatch').value = d.dispatchWeight;
  document.getElementById('d-rate').value = d.sellingRate;
  document.getElementById('d-sales-amount').value = fmtMoney(d.salesAmount);
  document.getElementById('d-notes').value = d.notes || '';
  document.getElementById('d-payment-amount').value = '';
  document.getElementById('d-vehicle').value = d.vehicleNumber || '';
  document.getElementById('delivery-form-error').innerHTML = '';

  currentDeliveryTripId = d.tripId || null;
  currentDeliveryOriginalCustomerId = d.customerId;
  if (d.vehicleNumber) {
    document.getElementById('d-trip-existing-row').style.display = '';
    refreshDeliveryTripOptions();
  } else {
    document.getElementById('d-trip-existing-row').style.display = 'none';
  }
  openModal('delivery-modal');
}

function renderDeliverySession() {
  const box = document.getElementById('delivery-session-list');
  if (!deliverySession.length) { box.innerHTML = ''; return; }
  const total = deliverySession.reduce((s, d) => s + d.amount, 0);
  const birds = deliverySession.reduce((s, d) => s + (Number(d.birds) || 0), 0);
  box.innerHTML = `
    <div class="card" style="background:var(--green-100); border-color:#cfe3d4; padding:10px 14px; margin-bottom:14px;">
      <div style="font-size:12.5px; font-weight:700; color:var(--green-800); margin-bottom:6px;">
        Recorded this session (${deliverySession.length})
      </div>
      ${deliverySession.map(d => `<div class="summary-line"><span>${escapeHtml(d.customerName)}</span><span class="mono">${fmtMoney(d.amount)}</span></div>`).join('')}
      <div class="summary-line"><span>Birds in this session</span><span class="mono">${birds}</span></div>
      <div class="summary-line total"><span>Total</span><span class="mono">${fmtMoney(total)}</span></div>
    </div>
  `;
}

function buildDeliveryPayload(finishTrip = false) {
  const customerSelect = document.getElementById('d-customer');
  const tripSelection = buildDeliveryTripSelection(); // throws if a vehicle is picked but no trip is selected
  const payload = {
    customerId: Number(customerSelect.value),
    deliveryDate: document.getElementById('d-date').value,
    numberOfBirds: document.getElementById('d-birds').value ? Number(document.getElementById('d-birds').value) : null,
    dispatchWeight: Number(document.getElementById('d-dispatch').value),
    sellingRate: Number(document.getElementById('d-rate').value),
    notes: document.getElementById('d-notes').value.trim(),
    createdBy: 'admin',
    trip: tripSelection,
    finishTrip,
  };
  if (document.getElementById('d-payment-amount').value) {
    payload.paymentAmount = Number(document.getElementById('d-payment-amount').value);
    payload.paymentMethod = document.getElementById('d-payment-method').value;
  }
  return { payload, customerName: customerSelect.options[customerSelect.selectedIndex].text };
}

/** Once a trip is picked for the first stop in a Save & Next session, every
 *  later stop sticks to that same trip unless the user deliberately changes it. */
async function lockSessionTrip(saved) {
  if (sessionTripId || !saved.tripId) return;
  sessionTripId = saved.tripId;
  await refreshDeliveryTripOptions();
}

async function saveCurrentDelivery(finishTrip = false) {
  const { payload, customerName } = buildDeliveryPayload(finishTrip);
  const saved = await Api.post('/api/deliveries', payload);
  deliverySession.push({ customerName, amount: saved.salesAmount, birds: saved.numberOfBirds });
  await lockSessionTrip(saved);
  return saved;
}

// Save & Next: keep the modal open, clear only the per-stop fields, move to the next one.
document.getElementById('delivery-save-next-btn').addEventListener('click', async () => {
  const errorBox = document.getElementById('delivery-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('delivery-save-next-btn');
  btn.disabled = true;
  try {
    await saveCurrentDelivery();
    renderDeliverySession();
    showToast('Delivery saved \u2014 ready for the next stop');
    clearDeliveryEntryFields();
    document.getElementById('d-customer').focus();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

// Save & Finish: save (create) or update (edit), then close.
document.getElementById('delivery-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const errorBox = document.getElementById('delivery-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('delivery-save-btn');
  btn.disabled = true;
  const id = document.getElementById('d-id').value;
  try {
    if (id) {
      const { payload } = buildDeliveryPayload(true);
      if (currentDeliveryOriginalCustomerId && Number(payload.customerId) !== Number(currentDeliveryOriginalCustomerId)) {
        const proceed = confirm(`You are moving this delivery to a different customer.

Any payment recorded specifically for this delivery will move to the new customer, and the old customer's delivery/payment ledger entries will be cleared and recalculated.

Continue?`);
        if (!proceed) return;
      }
      await Api.put(`/api/deliveries/${id}`, payload);
      showToast('Delivery updated');
    } else {
      await saveCurrentDelivery(true);
      showToast(`${deliverySession.length} deliver${deliverySession.length > 1 ? 'ies' : 'y'} recorded`);
    }
    closeModal('delivery-modal');
    await refreshDeliveries();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('apply-filter-btn').addEventListener('click', refreshDeliveries);

// ---------- Record Payment (unrelated to the delivery batch above) ----------
document.getElementById('add-payment-btn').addEventListener('click', () => {
  document.getElementById('payment-form').reset();
  document.getElementById('p-date').value = todayIso();
  document.getElementById('payment-form-error').innerHTML = '';
  openModal('payment-modal');
});

document.getElementById('payment-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const payload = {
    customerId: Number(document.getElementById('p-customer').value),
    paymentDate: document.getElementById('p-date').value,
    amount: Number(document.getElementById('p-amount').value),
    paymentMethod: document.getElementById('p-method').value,
    referenceNumber: document.getElementById('p-reference').value.trim(),
    notes: document.getElementById('p-notes').value.trim(),
    createdBy: 'admin',
  };
  const btn = document.getElementById('payment-save-btn');
  btn.disabled = true;
  try {
    await Api.post('/api/customer-payments', payload);
    showToast('Payment recorded');
    closeModal('payment-modal');
  } catch (err) {
    document.getElementById('payment-form-error').innerHTML =
      `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadDeliveriesPage, 50);
});
