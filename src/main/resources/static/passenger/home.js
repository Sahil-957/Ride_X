// Passenger home: plan a trip on the map, see the upfront fare, book, then follow the ride live.
const panel = document.getElementById('panel');
const map = RxMap.create('map');
const TERMINAL = ['COMPLETED', 'CANCELLED', 'EXPIRED'];

const trip = { pickup: null, drop: null }; // each { lat, lng, address }
const markers = { pickup: null, drop: null, driver: null };
let mode = 'pickup';        // which point the next map tap sets
let routeLine = null;
let paymentMethod = 'CASH';
let booking = null;         // the ride being tracked
let stopPolling = null;
let estimateSeq = 0;        // ignore out-of-order estimate responses

// ------------------------------------------------------------------ planner

function renderPlanner() {
  booking = null;
  panel.innerHTML = `
    <h5 class="fw-bold mb-3">Where are you going?</h5>
    ${pointField('pickup', 'Pickup location')}
    ${pointField('drop', 'Where to?')}
    <p class="small text-muted mb-3" id="modeHint"></p>
    <div id="estimateBox"></div>`;
  ['pickup', 'drop'].forEach((kind) => {
    const input = document.getElementById(`${kind}Input`);
    input.value = trip[kind]?.address || '';
    input.addEventListener('keydown', (e) => { if (e.key === 'Enter') { e.preventDefault(); search(kind); } });
    document.getElementById(`${kind}Search`).addEventListener('click', () => search(kind));
    document.getElementById(`${kind}Map`).addEventListener('click', () => setMode(kind));
    markers[kind]?.dragging?.enable();
  });
  document.getElementById('gpsBtn').addEventListener('click', useMyLocation);
  setMode(trip.pickup ? 'drop' : 'pickup');
  if (trip.pickup && trip.drop) updateEstimate();
}

function pointField(kind, placeholder) {
  return `
    <div class="mb-2 position-relative">
      <div class="input-group">
        <span class="input-group-text bg-white"><span class="rx-dot ${kind}"></span></span>
        <input id="${kind}Input" class="form-control border-start-0" placeholder="${placeholder}" autocomplete="off" maxlength="200">
        <button class="btn btn-outline-secondary" id="${kind}Search" title="Search address" type="button"><i class="bi bi-search"></i></button>
        <button class="btn btn-outline-secondary" id="${kind}Map" title="Choose on map" type="button"><i class="bi bi-crosshair"></i></button>
        ${kind === 'pickup' ? '<button class="btn btn-outline-secondary" id="gpsBtn" title="Use my location" type="button"><i class="bi bi-geo-alt"></i></button>' : ''}
      </div>
      <div class="search-results list-group shadow-sm" id="${kind}Results"></div>
    </div>`;
}

function setMode(kind) {
  mode = kind;
  ['pickup', 'drop'].forEach((k) => document.getElementById(`${k}Map`)?.classList.toggle('active', k === kind));
  const hint = document.getElementById('modeHint');
  if (hint) hint.innerHTML = `<i class="bi bi-hand-index me-1"></i>Tap the map to set your <b>${kind}</b> point, or search above. Drag a pin to adjust.`;
}

async function search(kind) {
  const query = document.getElementById(`${kind}Input`).value.trim();
  const box = document.getElementById(`${kind}Results`);
  if (query.length < 3) {
    UI.toast('Type at least 3 characters to search.', 'warning');
    return;
  }
  box.innerHTML = '<div class="list-group-item small text-muted"><span class="spinner-border spinner-border-sm me-2"></span>Searching…</div>';
  try {
    const places = await Api.get(`/api/geo/search?q=${encodeURIComponent(query)}`);
    if (!places.length) {
      box.innerHTML = '<div class="list-group-item small text-muted">No matches. Try another name, or tap the map.</div>';
      return;
    }
    box.innerHTML = places.map((p, i) => `
      <button type="button" class="list-group-item list-group-item-action" data-i="${i}">
        <div class="fw-semibold">${UI.esc(p.name)}</div>
        <div class="small text-muted text-truncate">${UI.esc(p.address)}</div>
      </button>`).join('');
    box.querySelectorAll('[data-i]').forEach((btn) => btn.addEventListener('click', () => {
      const place = places[btn.dataset.i];
      box.innerHTML = '';
      setPoint(kind, [place.lat, place.lng], place.name);
    }));
  } catch (e) {
    box.innerHTML = '';
    UI.toast(e.message);
  }
}

/** Pickup/drop pins are always created draggable with a drag handler; track() disables dragging during a ride. */
function pin(kind, latLng) {
  if (markers[kind]) {
    markers[kind].setLatLng(latLng);
    return;
  }
  markers[kind] = RxMap.placeMarker(map, null, kind, latLng, { draggable: true });
  markers[kind].on('dragend', (ev) => {
    const p = ev.target.getLatLng();
    setPoint(kind, [p.lat, p.lng]);
  });
}

function setPoint(kind, latLng, address) {
  trip[kind] = { lat: latLng[0], lng: latLng[1], address: address || null };
  pin(kind, latLng);
  const input = document.getElementById(`${kind}Input`);
  if (input) input.value = address || 'Finding address…';
  if (!address) reverseLookup(kind);
  if (kind === 'pickup' && !trip.drop) setMode('drop');
  if (!(trip.pickup && trip.drop)) map.setView(latLng, Math.max(map.getZoom(), 15));
  updateEstimate();
}

async function reverseLookup(kind) {
  const point = trip[kind];
  try {
    const { name } = await Api.get(`/api/geo/reverse?lat=${point.lat}&lng=${point.lng}`);
    if (trip[kind] !== point) return; // the pin moved again meanwhile
    point.address = name;
    const input = document.getElementById(`${kind}Input`);
    if (input) input.value = name;
  } catch {
    point.address = null;
  }
}

function useMyLocation() {
  RxMap.currentPosition()
    .then((p) => setPoint('pickup', p))
    .catch((e) => UI.toast(e.message, 'warning'));
}

function tripBody() {
  return {
    pickupAddress: trip.pickup.address, pickupLat: trip.pickup.lat, pickupLng: trip.pickup.lng,
    dropAddress: trip.drop.address, dropLat: trip.drop.lat, dropLng: trip.drop.lng,
    paymentMethod,
  };
}

async function updateEstimate() {
  const box = document.getElementById('estimateBox');
  if (!box || !trip.pickup || !trip.drop) return;
  const seq = ++estimateSeq;
  box.innerHTML = '<div class="skeleton mb-2" style="height: 86px"></div><div class="skeleton" style="height: 46px"></div>';
  try {
    const est = await Api.post('/api/passenger/estimate', tripBody());
    if (seq !== estimateSeq || booking) return;
    routeLine = RxMap.drawRoute(map, routeLine, est.path);
    const none = est.driversNearby === 0;
    box.innerHTML = `
      <div class="border rounded-4 p-3 mb-3">
        <div class="d-flex justify-content-between align-items-center">
          <div class="d-flex align-items-center gap-2">
            <span class="stat-icon bg-soft-yellow"><i class="bi bi-scooter"></i></span>
            <div><div class="fw-bold">Bike taxi</div>
              <div class="small text-muted">${UI.km(est.distanceKm)} · ${est.durationMin} min</div></div>
          </div>
          <div class="fare-big">${UI.money(est.fare)}</div>
        </div>
        <div class="small mt-2 ${none ? 'text-danger' : 'text-success'}"><i class="bi bi-person-check me-1"></i>${none
          ? 'No captains nearby right now. <button type="button" class="btn btn-link btn-sm p-0 align-baseline" id="recheckBtn">Check again</button>'
          : `${est.driversNearby} captain${est.driversNearby > 1 ? 's' : ''} nearby`}</div>
        ${est.roadRoute ? '' : '<div class="small text-muted"><i class="bi bi-info-circle me-1"></i>Approximate distance (road routing unavailable)</div>'}
      </div>
      <div class="mb-3">
        <div class="form-label">Payment</div>
        <div class="btn-group w-100" role="group">
          <input type="radio" class="btn-check" name="pay" id="payCash" value="CASH" ${paymentMethod === 'CASH' ? 'checked' : ''}>
          <label class="btn btn-outline-dark" for="payCash"><i class="bi bi-cash me-1"></i>Cash</label>
          <input type="radio" class="btn-check" name="pay" id="payUpi" value="UPI" ${paymentMethod === 'UPI' ? 'checked' : ''}>
          <label class="btn btn-outline-dark" for="payUpi"><i class="bi bi-phone me-1"></i>UPI</label>
        </div>
      </div>
      <button class="btn btn-primary w-100 py-2" id="bookBtn" data-busy-text="Booking…" ${none ? 'disabled' : ''}>
        Book ride · ${UI.money(est.fare)}</button>`;
    box.querySelectorAll('input[name=pay]').forEach((r) => r.addEventListener('change', () => { paymentMethod = r.value; }));
    document.getElementById('bookBtn').addEventListener('click', book);
    document.getElementById('recheckBtn')?.addEventListener('click', updateEstimate);
  } catch (e) {
    if (seq !== estimateSeq) return;
    routeLine = RxMap.drawRoute(map, routeLine, null);
    box.innerHTML = `<div class="alert alert-warning mb-0"><i class="bi bi-exclamation-circle me-1"></i>${UI.esc(e.message)}</div>`;
  }
}

async function book() {
  try {
    const created = await UI.busy(document.getElementById('bookBtn'), () => Api.post('/api/passenger/bookings', tripBody()));
    track(created);
  } catch {
    // error already shown
  }
}

// ------------------------------------------------------------------ live ride

function track(b) {
  booking = b;
  trip.pickup = { lat: b.pickup.lat, lng: b.pickup.lng, address: b.pickup.address };
  trip.drop = { lat: b.drop.lat, lng: b.drop.lng, address: b.drop.address };
  showOnMap(b, true);
  ['pickup', 'drop'].forEach((k) => markers[k]?.dragging?.disable());
  renderBooking(b);
  stopPolling?.();
  if (TERMINAL.includes(b.status)) return;
  stopPolling = UI.poll(async () => {
    const fresh = await Api.get(`/api/passenger/bookings/${b.id}`);
    if (!booking || booking.id !== fresh.id) return;
    const changed = fresh.status !== booking.status;
    booking = fresh;
    showOnMap(fresh, false);
    if (changed) {
      renderBooking(fresh);
      announce(fresh);
    }
    if (TERMINAL.includes(fresh.status)) stopPolling();
  }, 4000);
}

function showOnMap(b, fit) {
  pin('pickup', [b.pickup.lat, b.pickup.lng]);
  pin('drop', [b.drop.lat, b.drop.lng]);
  if (fit && b.path) routeLine = RxMap.drawRoute(map, routeLine, b.path);
  if (b.driverLat != null) {
    markers.driver = RxMap.placeMarker(map, markers.driver, 'driver', [b.driverLat, b.driverLng]);
  } else if (markers.driver) {
    map.removeLayer(markers.driver);
    markers.driver = null;
  }
}

function announce(b) {
  const messages = {
    ACCEPTED: [`${b.driver?.name || 'A captain'} accepted your ride!`, 'success'],
    STARTED: ['Your ride has started. Enjoy the trip!', 'info'],
    COMPLETED: ['You have arrived. Thanks for riding with CabZen!', 'success'],
    EXPIRED: ['No captain accepted in time. Please try again.', 'warning'],
    CANCELLED: ['This ride was cancelled.', 'info'],
    REQUESTED: ['Your captain had to cancel. Finding you another one…', 'warning'],
  };
  if (messages[b.status]) UI.toast(...messages[b.status]);
}

function tripSummary(b) {
  return `
    <div class="route-line my-3">
      <div class="route-stop mb-3"><div class="small text-muted">Pickup</div><div class="fw-semibold">${UI.esc(b.pickup.address)}</div></div>
      <div class="route-stop drop"><div class="small text-muted">Drop</div><div class="fw-semibold">${UI.esc(b.drop.address)}</div></div>
    </div>
    <div class="d-flex justify-content-between small text-muted border-top pt-2">
      <span><i class="bi bi-signpost-2 me-1"></i>${UI.km(b.distanceKm)} · ${b.durationMin} min</span>
      <span>${UI.money(b.fare)} · ${UI.esc(b.paymentMethod)}</span>
    </div>`;
}

function driverCard(b) {
  const v = b.vehicle || {};
  return `
    <div class="d-flex gap-3 align-items-center p-3 rounded-4 bg-light mb-3">
      <div class="driver-avatar">${UI.esc(b.driver.name.charAt(0).toUpperCase())}</div>
      <div class="flex-grow-1 min-w-0">
        <div class="fw-bold text-break">${UI.esc(b.driver.name)}</div>
        <div class="small">${UI.stars(b.driverRating)}</div>
        <div class="small text-muted">${UI.esc(v.make)} ${UI.esc(v.model)} · ${UI.esc(v.color)}</div>
      </div>
      <div class="text-end">
        <span class="plate">${UI.esc(v.registrationNumber)}</span><br>
        <a class="btn btn-sm btn-dark mt-2" href="tel:${UI.esc(b.driver.phone)}" title="Call captain"><i class="bi bi-telephone"></i></a>
      </div>
    </div>`;
}

function renderBooking(b) {
  const title = (icon, text, sub) => `
    <div class="d-flex align-items-center gap-2 mb-3">
      <span class="stat-icon bg-soft-yellow"><i class="bi ${icon}"></i></span>
      <div><h5 class="fw-bold mb-0">${text}</h5>${sub ? `<div class="small text-muted">${sub}</div>` : ''}</div>
    </div>`;

  switch (b.status) {
    case 'REQUESTED':
      panel.innerHTML = `
        <div class="text-center py-2">
          <div class="searching-pulse mb-3"><i class="bi bi-scooter"></i></div>
          <h5 class="fw-bold mb-1">Finding a captain near you…</h5>
          <p class="text-muted small mb-0">Your request has been sent to nearby captains.</p>
        </div>
        ${tripSummary(b)}
        <button class="btn btn-outline-danger w-100 mt-3" id="cancelBtn" data-busy-text="Cancelling…">Cancel request</button>`;
      break;
    case 'ACCEPTED':
      panel.innerHTML = `
        ${title('bi-geo-alt-fill', 'Your captain is on the way', 'Meet them at the pickup point')}
        ${driverCard(b)}
        <div class="text-center mb-2">
          <div class="small text-muted mb-1">Share this OTP with your captain to start the ride</div>
          <div class="otp-box">${UI.esc(b.otp)}</div>
        </div>
        ${tripSummary(b)}
        <button class="btn btn-outline-danger w-100 mt-3" id="cancelBtn" data-busy-text="Cancelling…">Cancel ride</button>`;
      break;
    case 'STARTED':
      panel.innerHTML = `${title('bi-signpost-split-fill', 'On the way to your drop', `Started ${UI.dateTime(b.startedAt)}`)}
        ${driverCard(b)}${tripSummary(b)}`;
      break;
    case 'COMPLETED':
      panel.innerHTML = `
        <div class="text-center py-2">
          <span class="stat-icon bg-soft-green mx-auto mb-2"><i class="bi bi-check2-circle"></i></span>
          <h5 class="fw-bold mb-1">You've arrived!</h5>
          <div class="fare-big">${UI.money(b.fare)}</div>
          <div class="small text-muted">${b.paymentMethod === 'UPI' ? 'Paid by UPI' : 'Paid in cash'} to ${UI.esc(b.driver?.name)}</div>
        </div>
        <div id="ratingBox" class="border-top mt-3 pt-3"></div>
        <button class="btn btn-primary w-100 mt-3" id="newRideBtn"><i class="bi bi-plus-lg me-1"></i>Book another ride</button>`;
      renderRating(b);
      break;
    default: // CANCELLED, EXPIRED
      panel.innerHTML = `
        <div class="text-center py-3">
          <span class="stat-icon ${b.status === 'EXPIRED' ? 'bg-soft-gray' : 'bg-soft-red'} mx-auto mb-2">
            <i class="bi ${b.status === 'EXPIRED' ? 'bi-hourglass-bottom' : 'bi-x-circle'}"></i></span>
          <h5 class="fw-bold mb-1">${b.status === 'EXPIRED' ? 'No captain accepted in time' : 'Ride cancelled'}</h5>
          <p class="text-muted small mb-0">${b.status === 'EXPIRED' ? 'Captains nearby were busy. You were not charged.' : 'You were not charged.'}</p>
        </div>
        ${tripSummary(b)}
        <button class="btn btn-primary w-100 mt-3" id="retryBtn"><i class="bi bi-arrow-repeat me-1"></i>Try again</button>`;
  }

  document.getElementById('cancelBtn')?.addEventListener('click', cancelRide);
  document.getElementById('newRideBtn')?.addEventListener('click', () => backToPlanner(true));
  document.getElementById('retryBtn')?.addEventListener('click', () => backToPlanner(false));
}

function renderRating(b) {
  const box = document.getElementById('ratingBox');
  if (b.stars) {
    box.innerHTML = `<div class="text-center small">You rated this ride ${UI.stars(b.stars)}</div>`;
    return;
  }
  let chosen = 0;
  box.innerHTML = `
    <div class="text-center fw-semibold mb-1">How was your ride with ${UI.esc(b.driver?.name)}?</div>
    <div class="star-input text-center mb-2">${[1, 2, 3, 4, 5].map((i) => `<i class="bi bi-star-fill" data-v="${i}"></i>`).join(' ')}</div>
    <textarea class="form-control mb-2" id="ratingComment" rows="2" maxlength="500" placeholder="Anything to add? (optional)"></textarea>
    <button class="btn btn-dark w-100" id="rateBtn" data-busy-text="Sending…" disabled>Submit rating</button>`;
  box.querySelectorAll('[data-v]').forEach((star) => star.addEventListener('click', () => {
    chosen = Number(star.dataset.v);
    box.querySelectorAll('[data-v]').forEach((s) => s.classList.toggle('on', Number(s.dataset.v) <= chosen));
    document.getElementById('rateBtn').disabled = false;
  }));
  document.getElementById('rateBtn').addEventListener('click', async (e) => {
    try {
      await UI.busy(e.currentTarget, () => Api.post(`/api/passenger/bookings/${b.id}/rating`,
        { stars: chosen, comment: document.getElementById('ratingComment').value }));
      UI.toast('Thanks for your feedback!', 'success');
      b.stars = chosen;
      renderRating(b);
    } catch {
      // error already shown
    }
  });
}

async function cancelRide(e) {
  if (!confirm('Cancel this ride?')) return;
  try {
    const result = await UI.busy(e.currentTarget, () => Api.post(`/api/passenger/bookings/${booking.id}/cancel`));
    stopPolling?.();
    track(result);
    UI.toast('Ride cancelled.', 'info');
  } catch {
    // error already shown
  }
}

/** After a finished ride the old drop becomes the new pickup; after a failed one keep both points. */
function backToPlanner(fromDrop) {
  stopPolling?.();
  if (markers.driver) { map.removeLayer(markers.driver); markers.driver = null; }
  routeLine = RxMap.drawRoute(map, routeLine, null);
  if (fromDrop) {
    trip.pickup = trip.drop;
    trip.drop = null;
    markers.pickup.setLatLng([trip.pickup.lat, trip.pickup.lng]);
    if (markers.drop) { map.removeLayer(markers.drop); markers.drop = null; }
    map.setView([trip.pickup.lat, trip.pickup.lng], 15);
  }
  renderPlanner();
}

// ------------------------------------------------------------------ start

(async function init() {
  try {
    await UI.nav();
  } catch {
    return; // redirected to login
  }
  map.on('click', (e) => { if (!booking) setPoint(mode, [e.latlng.lat, e.latlng.lng]); });
  try {
    const active = await Api.get('/api/passenger/bookings/active');
    if (active) {
      track(active);
      return;
    }
  } catch (e) {
    UI.toast(e.message);
  }
  renderPlanner();
  RxMap.currentPosition()
    .then((p) => { if (!trip.pickup && !booking) setPoint('pickup', p); })
    .catch(() => { /* user can search or tap instead */ });
})();
