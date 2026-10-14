// Shared helpers for the "Start New Trip / Continue Existing Trip" control.
// A vehicle can make several separate trips on the same day, so trip identity
// is always an explicit trip id - never guessed from vehicle+date. This file
// only formats/fetches; each page (purchases.js, deliveries.js) wires the
// radio buttons and fields itself, since the two forms show different fields.

async function fetchTripOptions(vehicleNumber, date) {
  if (!vehicleNumber || !date) return [];
  try {
    return await Api.get(`/api/trips?vehicleNumber=${encodeURIComponent(vehicleNumber)}&date=${encodeURIComponent(date)}`);
  } catch (err) {
    console.error(err);
    return [];
  }
}

function tripOptionLabel(t) {
  const parts = [`Trip ${t.tripNumber ?? '?'}`];
  if (t.supplierName) parts.push(t.supplierName);
  if (t.loadedBirds != null) parts.push(`${t.loadedBirds} birds purchased`);
  if (t.deliveredBirds != null) parts.push(`${t.deliveredBirds} birds delivered`);
  if (t.loadedWeight != null) parts.push(`${Number(t.loadedWeight).toFixed(2)} kg loaded`);
  if (t.deliveredWeight != null) parts.push(`${Number(t.deliveredWeight).toFixed(2)} kg delivered`);
  if (t.driverName) parts.push(`Driver: ${t.driverName}`);
  return parts.join(' \u2014 ');
}

/** Next trip number if the user starts a new trip right now, based on the trips already fetched for this vehicle/date. */
function nextTripNumberPreview(existingTrips) {
  if (!existingTrips || !existingTrips.length) return 1;
  const max = Math.max(...existingTrips.map(t => t.tripNumber || 0));
  return max + 1;
}
