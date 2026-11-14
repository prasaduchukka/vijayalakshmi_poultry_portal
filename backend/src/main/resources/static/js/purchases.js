let allPurchases = [];
let suppliersCache = [];
let vehiclesCache = [];
let purchaseTripOptionsCache = [];
let currentPurchaseTripId = null; // tripId already linked to the purchase being edited, if any
let currentPurchaseOriginalSupplierId = null;

async function loadPurchasesPage() {
  try {
    [suppliersCache, vehiclesCache] = await Promise.all([
      Api.get('/api/suppliers'),
      Api.get('/api/vehicles'),
    ]);
    document.getElementById('pu-supplier').innerHTML = suppliersCache
      .map(s => `<option value="${s.id}">${escapeHtml(s.supplierName)}</option>`).join('');
    document.getElementById('pu-vehicle').innerHTML = '<option value="">None</option>' + vehiclesCache
      .map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('');
    renderSuppliers(suppliersCache);
    await refreshPurchases();
    await refreshTripSummaries();
  } catch (err) {
    handleError(err);
  }
}

function renderSuppliers(list) {
  const admin = isAdmin();
  const body = document.getElementById('suppliers-body');
  body.innerHTML = list.length ? list.map(s => `
    <tr>
      <td><a href="supplier-detail.html?id=${s.id}">${escapeHtml(s.supplierName)}</a></td>
      <td>${escapeHtml(s.contactPerson || '-')}</td>
      <td>${escapeHtml(s.phoneNumber || '-')}</td>
      <td>${escapeHtml(s.address || '-')}</td>
      <td class="text-right mono">${fmtMoney(s.openingPayableBalance)}</td>
      <td>${escapeHtml(s.notes || '-')}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editSupplier(${s.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deleteSupplierRow(${s.id})">Delete</button>` : ''}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="7" class="table-empty">No suppliers yet. Click "Add Supplier" to get started.</td></tr>';
}

async function refreshPurchases() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/purchases';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    allPurchases = await Api.get(url);
    renderPurchases(allPurchases);
  } catch (err) {
    handleError(err);
  }
}

function renderPurchases(list) {
  const admin = isAdmin();
  const body = document.getElementById('purchases-body');
  body.innerHTML = list.length ? list.map(p => `
    <tr>
      <td>${fmtDate(p.purchaseDate)}</td>
      <td><a href="supplier-detail.html?id=${p.supplierId}">${escapeHtml(p.supplierName)}</a></td>
      <td>${escapeHtml(p.vehicleNumber || '-')}${p.tripNumber ? ` <span class="trip-number-badge">Trip ${p.tripNumber}</span>` : ''}</td>
      <td>${p.numberOfBirds ?? '-'}</td>
      <td>${p.numberOfBoxes ?? '-'}</td>
      <td>${fmtKg(p.purchaseWeight)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editPurchase(${p.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deletePurchaseRow(${p.id})">Delete</button>` : ''}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="7" class="table-empty">No purchases found for this period</td></tr>';
}

async function refreshTripSummaries() {
  const from = document.getElementById('from-filter').value;
  const to = document.getElementById('to-filter').value;
  let url = '/api/trips/summary';
  if (from && to) url += `?from=${from}&to=${to}`;
  try {
    const trips = await Api.get(url);
    renderTripSummaries(trips);
  } catch (err) {
    handleError(err);
  }
}

function renderTripSummaries(list) {
  const body = document.getElementById('trips-body');
  body.innerHTML = list.length ? list.map(t => {
    const diff = t.weightDifference != null ? Number(t.weightDifference) : null;
    const birdsDiff = t.birdsDifference != null ? Number(t.birdsDifference) : null;
    return `
    <tr>
      <td>${fmtDate(t.tripDate)}</td>
      <td><strong>${escapeHtml(t.vehicleNumber)}</strong></td>
      <td>${t.tripNumber ? `Trip ${t.tripNumber}` : '-'}</td>
      <td>
        <div>${escapeHtml(t.companyName || '-')}</div>
        <div class="trip-supplier-sub">Supplier: ${escapeHtml(t.supplierName || '-')}</div>
      </td>
      <td class="text-right">${t.customerCount}</td>
      <td class="text-right">${t.totalLoadedBirds != null ? t.totalLoadedBirds : '-'}</td>
      <td class="text-right">${t.totalDeliveredBirds != null ? t.totalDeliveredBirds : '-'}</td>
      <td class="text-right ${birdsDiff != null && birdsDiff !== 0 ? (birdsDiff > 0 ? 'text-red' : 'text-green') : ''}">${birdsDiff != null ? birdsDiff : '-'}</td>
      <td class="text-right">${t.totalLoadedWeight != null ? fmtKg(t.totalLoadedWeight) : '-'}</td>
      <td class="text-right">${fmtKg(t.totalDeliveredWeight)}</td>
      <td class="text-right ${diff != null && diff !== 0 ? (diff > 0 ? 'text-red' : 'text-green') : ''}">${diff != null ? fmtKg(diff) : '-'}</td>
      <td class="text-right mono">${fmtMoney(t.totalSalesAmount)}</td>
    </tr>
  `;
  }).join('') : '<tr><td colspan="12" class="table-empty">No trips recorded yet</td></tr>';
}

// ---------- Trip picker (Start New Trip / Continue Existing Trip) ----------

function updatePurchaseTripModeUI() {
  const existing = document.getElementById('pu-trip-mode-existing').checked;
  document.getElementById('pu-trip-existing-row').style.display = existing ? '' : 'none';
  document.getElementById('pu-trip-new-fields').style.display = existing ? 'none' : '';
}

function togglePurchaseTripSection() {
  const vehicle = document.getElementById('pu-vehicle').value;
  document.getElementById('pu-trip-section').style.display = vehicle ? '' : 'none';
  if (vehicle) refreshPurchaseTripOptions();
}

async function refreshPurchaseTripOptions() {
  const vehicle = document.getElementById('pu-vehicle').value;
  const date = document.getElementById('pu-date').value;
  const existingSelect = document.getElementById('pu-trip-existing');
  if (!vehicle || !date) {
    purchaseTripOptionsCache = [];
    existingSelect.innerHTML = '';
    return;
  }
  purchaseTripOptionsCache = await fetchTripOptions(vehicle, date);
  existingSelect.innerHTML = purchaseTripOptionsCache.length
    ? purchaseTripOptionsCache.map(t => `<option value="${t.id}">${escapeHtml(tripOptionLabel(t))}</option>`).join('')
    : '';
  document.getElementById('pu-trip-existing-hint').textContent = purchaseTripOptionsCache.length
    ? 'Choose the trip this purchase belongs to.'
    : 'No trips recorded yet for this vehicle on this date — start a new one instead.';
  document.getElementById('pu-trip-number-hint').textContent =
    `This will be recorded as Trip ${nextTripNumberPreview(purchaseTripOptionsCache)} for this vehicle on this date.`;

  // Editing a purchase that already belongs to a trip: default to Continue
  // Existing with that same trip preselected, so saving doesn't spawn a new one.
  if (currentPurchaseTripId) {
    document.getElementById('pu-trip-mode-existing').checked = true;
    if (purchaseTripOptionsCache.some(t => t.id === currentPurchaseTripId)) {
      existingSelect.value = String(currentPurchaseTripId);
    }
    updatePurchaseTripModeUI();
  }
}

document.getElementById('pu-vehicle').addEventListener('change', togglePurchaseTripSection);
document.getElementById('pu-date').addEventListener('change', () => {
  if (document.getElementById('pu-vehicle').value) refreshPurchaseTripOptions();
});
document.querySelectorAll('input[name="pu-trip-mode"]').forEach(r =>
  r.addEventListener('change', updatePurchaseTripModeUI));

/** Builds the explicit TripSelectionRequest - never an implicit vehicle+date guess. */
function buildPurchaseTripSelection() {
  const vehicle = document.getElementById('pu-vehicle').value;

  // No vehicle means this purchase is not trip-tracked.
  if (!vehicle) return null;

  const mode = document.querySelector('input[name="pu-trip-mode"]:checked').value;

  // Continue an existing trip.
  if (mode === 'existing') {
    const tripId = document.getElementById('pu-trip-existing').value;
    if (!tripId) {
      throw new Error('Please select an existing trip to continue, or choose "Start New Trip".');
    }
    return { tripId: Number(tripId) };
  }

  // Start a new trip. Company Name and Total Loaded Weight are no longer
  // entered separately. The Purchase Weight is the trip loaded weight.
  const purchaseWeight = document.getElementById('pu-weight').value;

  return {
    newTrip: {
      vehicleNumber: vehicle,
      companyName: null,
      totalWeightDispatched: purchaseWeight ? Number(purchaseWeight) : null,
      driverName: document.getElementById('pu-driver').value.trim(),
      helperName: document.getElementById('pu-helper').value.trim(),
      distanceKm: document.getElementById('pu-distance').value ? Number(document.getElementById('pu-distance').value) : null,
      mileage: document.getElementById('pu-mileage').value ? Number(document.getElementById('pu-mileage').value) : null,
      startTime: document.getElementById('pu-start-time').value || null,
    },
  };
}

function recalcPurchaseMileage() {
  const distance = Number(document.getElementById('pu-distance').value || 0);
  const fuel = Number(document.getElementById('pu-fuel').value || 0);
  document.getElementById('pu-mileage').value = fuel > 0 ? (distance / fuel).toFixed(2) : '';
}
['pu-distance', 'pu-fuel'].forEach(id =>
  document.getElementById(id).addEventListener('input', recalcPurchaseMileage));

function resetPurchaseForm() {
  document.getElementById('purchase-form').reset();
  document.getElementById('pu-id').value = '';
  document.getElementById('purchase-modal-title').textContent = 'Record Purchase';
  document.getElementById('pu-date').value = todayIso();
  document.getElementById('purchase-form-error').innerHTML = '';
  currentPurchaseTripId = null;
  currentPurchaseOriginalSupplierId = null;
  document.getElementById('pu-trip-section').style.display = 'none';
  document.getElementById('pu-trip-mode-new').checked = true;
  updatePurchaseTripModeUI();
}

document.getElementById('add-purchase-btn').addEventListener('click', () => {
  resetPurchaseForm();
  openModal('purchase-modal');
});

function editPurchase(id) {
  const p = allPurchases.find(x => x.id === id);
  if (!p) return;
  document.getElementById('purchase-modal-title').textContent = 'Edit Purchase';
  // Supplier/trip changes are validated by the backend; edits never leave the old supplier ledger behind.
  document.getElementById('pu-id').value = p.id;
  document.getElementById('pu-supplier').value = p.supplierId;
  document.getElementById('pu-date').value = p.purchaseDate;
  document.getElementById('pu-birds').value = p.numberOfBirds ?? '';
  document.getElementById('pu-boxes').value = p.numberOfBoxes ?? '';
  document.getElementById('pu-weight').value = p.purchaseWeight;
  document.getElementById('pu-vehicle').value = p.vehicleNumber || '';
  document.getElementById('pu-notes').value = p.notes || '';
  document.getElementById('purchase-form-error').innerHTML = '';

  // Blank the "Start New Trip" fields so a previous edit's values can't leak through -
  // they're only prefilled from the trip itself if the user switches to Start New Trip.
  ['pu-driver', 'pu-helper', 'pu-start-time', 'pu-distance', 'pu-fuel', 'pu-mileage']
    .forEach(id => { document.getElementById(id).value = ''; });

  currentPurchaseTripId = p.tripId || null;
  currentPurchaseOriginalSupplierId = p.supplierId;
  document.getElementById('pu-trip-mode-new').checked = true;
  updatePurchaseTripModeUI();
  if (p.vehicleNumber) {
    document.getElementById('pu-trip-section').style.display = '';
    refreshPurchaseTripOptions();
  } else {
    document.getElementById('pu-trip-section').style.display = 'none';
  }
  openModal('purchase-modal');
}

document.getElementById('purchase-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('pu-id').value;
  const errorBox = document.getElementById('purchase-form-error');
  errorBox.innerHTML = '';

  let tripSelection;
  try {
    tripSelection = buildPurchaseTripSelection();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
    return;
  }

  const payload = {
    supplierId: Number(document.getElementById('pu-supplier').value),
    purchaseDate: document.getElementById('pu-date').value,
    numberOfBirds: document.getElementById('pu-birds').value ? Number(document.getElementById('pu-birds').value) : null,
    numberOfBoxes: document.getElementById('pu-boxes').value ? Number(document.getElementById('pu-boxes').value) : null,
    purchaseWeight: Number(document.getElementById('pu-weight').value),
    notes: document.getElementById('pu-notes').value.trim(),
    createdBy: 'admin',
    trip: tripSelection,
  };
  const btn = document.getElementById('purchase-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      if (currentPurchaseOriginalSupplierId && Number(payload.supplierId) !== Number(currentPurchaseOriginalSupplierId)) {
        const proceed = confirm(`You are moving this purchase to a different supplier.

The old supplier purchase ledger entry will be removed and the same purchase will be recorded against the new supplier. Trip details will use the latest saved supplier.

Continue?`);
        if (!proceed) return;
      }
      await Api.put(`/api/purchases/${id}`, payload);
      showToast('Purchase updated');
    } else {
      await Api.post('/api/purchases', payload);
      showToast('Purchase recorded');
    }
    closeModal('purchase-modal');
    await refreshPurchases();
    await refreshTripSummaries();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

async function deletePurchaseRow(id) {
  if (!confirm(`Permanently delete this purchase?

If it is the last purchase on its trip, the trip, all deliveries on that trip, every delivery-linked payment, customer ledger entries, and vehicle trip history will also be deleted.

This cannot be undone.`)) return;
  try {
    await Api.del(`/api/purchases/${id}`);
    showToast('Purchase deleted');
    await refreshPurchases();
    await refreshTripSummaries();
  } catch (err) { handleError(err); }
}

// ---------- Add/Edit Supplier ----------
document.getElementById('add-supplier-btn').addEventListener('click', () => {
  document.getElementById('supplier-form').reset();
  document.getElementById('s-id').value = '';
  document.getElementById('supplier-modal-title').textContent = 'Add Supplier';
  document.getElementById('s-opening-balance').value = 0;
  document.getElementById('s-opening-balance-hint').textContent = 'Only used when creating a new supplier.';
  document.getElementById('supplier-form-error').innerHTML = '';
  openModal('supplier-modal');
});

function editSupplier(id) {
  const s = suppliersCache.find(x => x.id === id);
  if (!s) return;
  document.getElementById('supplier-modal-title').textContent = 'Edit Supplier';
  document.getElementById('s-id').value = s.id;
  document.getElementById('s-name').value = s.supplierName;
  document.getElementById('s-contact').value = s.contactPerson || '';
  document.getElementById('s-phone').value = s.phoneNumber || '';
  document.getElementById('s-address').value = s.address || '';
  document.getElementById('s-opening-balance').value = s.openingPayableBalance ?? 0;
  document.getElementById('s-opening-balance-hint').textContent = 'This supplier already exists \u2014 changing this will not replay past ledger entries.';
  document.getElementById('s-status').value = s.status || 'ACTIVE';
  document.getElementById('s-notes').value = s.notes || '';
  document.getElementById('supplier-form-error').innerHTML = '';
  openModal('supplier-modal');
}

async function deleteSupplierRow(id) {
  const s = suppliersCache.find(x => x.id === id);
  const name = s ? s.supplierName : 'this supplier';
  if (!confirm(`Permanently delete "${name}" and ALL their purchases, supplier payments, supplier ledger history, and any trip(s) that become orphaned by those purchases.

If an affected trip is no longer used by any other supplier purchase, its deliveries, delivery payments, customer ledger entries, and vehicle trip history will also be deleted.

This cannot be undone.`)) return;
  try {
    await Api.del(`/api/suppliers/${id}`);
    showToast('Supplier deleted');
    await loadPurchasesPage();
  } catch (err) { handleError(err); }
}

document.getElementById('supplier-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('s-id').value;
  const payload = {
    supplierName: document.getElementById('s-name').value.trim(),
    contactPerson: document.getElementById('s-contact').value.trim(),
    phoneNumber: document.getElementById('s-phone').value.trim(),
    address: document.getElementById('s-address').value.trim(),
    openingPayableBalance: Number(document.getElementById('s-opening-balance').value || 0),
    status: document.getElementById('s-status').value,
    notes: document.getElementById('s-notes').value.trim(),
  };
  const errorBox = document.getElementById('supplier-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('supplier-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/suppliers/${id}`, payload);
      showToast('Supplier updated');
    } else {
      await Api.post('/api/suppliers', payload);
      showToast('Supplier added');
    }
    closeModal('supplier-modal');
    await loadPurchasesPage();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('apply-filter-btn').addEventListener('click', () => {
  refreshPurchases();
  refreshTripSummaries();
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadPurchasesPage, 50);
});
