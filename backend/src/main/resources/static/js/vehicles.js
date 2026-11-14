let allVehicles = [];

function dueDateCell(dateStr) {
  if (!dateStr) return '<span class="text-muted">-</span>';
  const due = new Date(dateStr);
  const today = new Date();
  const daysLeft = Math.floor((due - today) / (1000 * 60 * 60 * 24));
  const text = fmtDate(dateStr);
  if (daysLeft < 0) return `<span class="text-red">${text}</span>`;
  if (daysLeft <= 30) return `<span style="color:var(--amber-600); font-weight:600;">${text}</span>`;
  return text;
}

async function loadVehicles() {
  try {
    allVehicles = await Api.get('/api/vehicles');
    renderVehicles();
    document.getElementById('trip-vehicle-filter').innerHTML =
      '<option value="">Select a vehicle</option>' +
      allVehicles.map(v => `<option value="${escapeHtml(v.vehicleNumber)}">${escapeHtml(v.vehicleNumber)}</option>`).join('');
  } catch (err) {
    handleError(err);
  }
}

function renderVehicles() {
  const admin = isAdmin();
  const body = document.getElementById('vehicles-body');
  body.innerHTML = allVehicles.length ? allVehicles.map(v => `
    <tr>
      <td>${escapeHtml(v.ownerName || '-')}</td>
      <td><strong>${escapeHtml(v.vehicleNumber)}</strong></td>
      <td>${dueDateCell(v.insuranceDueDate)}</td>
      <td>${dueDateCell(v.brakeDueDate)}</td>
      <td>${dueDateCell(v.permitDueDate)}</td>
      <td>${dueDateCell(v.roadTaxDueDate)}</td>
      <td>${dueDateCell(v.pollutionDueDate)}</td>
      <td>
        ${admin ? `<button class="btn-outline btn-sm" onclick="editVehicle(${v.id})">Edit</button>
        <button class="btn-danger btn-sm" onclick="deleteVehicle(${v.id})">Delete</button>` : '<span class="text-muted" style="font-size:12px;">View only</span>'}
      </td>
    </tr>
  `).join('') : '<tr><td colspan="8" class="table-empty">No vehicles added yet</td></tr>';
}

function editVehicle(id) {
  const v = allVehicles.find(x => x.id === id);
  if (!v) return;
  document.getElementById('vehicle-modal-title').textContent = 'Edit Vehicle';
  document.getElementById('v-id').value = v.id;
  document.getElementById('v-owner').value = v.ownerName || '';
  document.getElementById('v-number').value = v.vehicleNumber;
  document.getElementById('v-insurance').value = v.insuranceDueDate || '';
  document.getElementById('v-brake').value = v.brakeDueDate || '';
  document.getElementById('v-permit').value = v.permitDueDate || '';
  document.getElementById('v-roadtax').value = v.roadTaxDueDate || '';
  document.getElementById('v-pollution').value = v.pollutionDueDate || '';
  document.getElementById('v-notes').value = v.notes || '';
  document.getElementById('vehicle-form-error').innerHTML = '';
  openModal('vehicle-modal');
}

async function deleteVehicle(id) {
  const vehicle = allVehicles.find(v => v.id === id);
  if (!vehicle) return;

  const confirmed = confirm(
    `Permanently delete vehicle "${vehicle.vehicleNumber}"?\n\n` +
    `Deletion is allowed only when this vehicle has never been used in a trip.\n\n` +
    `If this vehicle has trip/history records, the system will protect those records and block the deletion.\n\n` +
    `Continue?`
  );
  if (!confirmed) return;
  try {
    await Api.del(`/api/vehicles/${id}`);
    showToast(`Vehicle ${vehicle.vehicleNumber} deleted`);
    await loadVehicles();
  } catch (err) { handleError(err); }
}

document.getElementById('add-vehicle-btn').addEventListener('click', () => {
  document.getElementById('vehicle-form').reset();
  document.getElementById('v-id').value = '';
  document.getElementById('vehicle-modal-title').textContent = 'Add Vehicle';
  document.getElementById('vehicle-form-error').innerHTML = '';
  openModal('vehicle-modal');
});

document.getElementById('vehicle-form').addEventListener('submit', async (e) => {
  e.preventDefault();
  const id = document.getElementById('v-id').value;
  const payload = {
    ownerName: document.getElementById('v-owner').value.trim(),
    vehicleNumber: document.getElementById('v-number').value.trim(),
    insuranceDueDate: document.getElementById('v-insurance').value || null,
    brakeDueDate: document.getElementById('v-brake').value || null,
    permitDueDate: document.getElementById('v-permit').value || null,
    roadTaxDueDate: document.getElementById('v-roadtax').value || null,
    pollutionDueDate: document.getElementById('v-pollution').value || null,
    notes: document.getElementById('v-notes').value.trim(),
  };
  const errorBox = document.getElementById('vehicle-form-error');
  errorBox.innerHTML = '';
  const btn = document.getElementById('vehicle-save-btn');
  btn.disabled = true;
  try {
    if (id) {
      await Api.put(`/api/vehicles/${id}`, payload);
      showToast('Vehicle updated');
    } else {
      await Api.post('/api/vehicles', payload);
      showToast('Vehicle added');
    }
    closeModal('vehicle-modal');
    await loadVehicles();
  } catch (err) {
    errorBox.innerHTML = `<div class="alert alert-error">${escapeHtml(err.message)}</div>`;
  } finally {
    btn.disabled = false;
  }
});

document.getElementById('trip-vehicle-filter').addEventListener('change', async (e) => {
  const vehicleNumber = e.target.value;
  const body = document.getElementById('trips-body');
  if (!vehicleNumber) { body.innerHTML = '<tr><td colspan="5" class="table-empty">Select a vehicle above</td></tr>'; return; }
  try {
    const trips = await Api.get(`/api/vehicles/${encodeURIComponent(vehicleNumber)}/trips`);
    body.innerHTML = trips.length ? trips.map(t => `
      <tr>
        <td>${fmtDate(t.tripDate)}</td>
        <td>
          <div>${escapeHtml(t.companyName || '-')}</div>
          <div class="trip-supplier-sub">Supplier: ${escapeHtml(t.supplierName || '-')}</div>
        </td>
        <td>${escapeHtml(t.driverName || '-')}</td>
        <td>${t.distanceKm != null ? t.distanceKm + ' km' : '-'}</td>
        <td>${t.mileage != null ? t.mileage : '-'}</td>
      </tr>
    `).join('') : '<tr><td colspan="5" class="table-empty">No trips recorded for this vehicle yet</td></tr>';
  } catch (err) {
    handleError(err);
  }
});

document.addEventListener('DOMContentLoaded', () => {
  setTimeout(loadVehicles, 50);
});
