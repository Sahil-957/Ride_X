// Captain dashboard: online switch, GPS heartbeat, nearby requests, and the active ride.
const map = RxMap.create('map');
const statusBox = document.getElementById('statusBox');
const content = document.getElementById('content');

let profile = null;
let myPos = null;          // [lat, lng]
let myMarker = null;
let gpsBlocked = false;
let manualPos = false;     // demo mode: position chosen by tapping the map, GPS updates ignored
let ride = null;           // active booking
let shownKey = null;       // what the content panel currently shows (avoid needless re-renders)
const layers = { pickup: null, drop: null, route: null };

// ------------------------------------------------------------------ location

function startGps() {
  if (!navigator.geolocation) {
    gpsBlocked = true;
    return;
  }
  navigator.geolocation.watchPosition(
    (p) => { if (!manualPos) setMyPos([p.coords.latitude, p.coords.longitude], false); },
    () => { gpsBlocked = true; renderStatus(); },
    { enableHighAccuracy: true, maximumAge: 10000, timeout: 20000 });
}

function setMyPos(latLng, manual) {
  const first = !myPos;
  myPos = latLng;
  myMarker = RxMap.placeMarker(map, myMarker, 'driver', latLng);
  if (first) map.setView(latLng, 15);
  if (first || manual) {
    renderStatus();
    sendLocation();
  }
}

async function sendLocation() {
  if (!myPos || !profile?.online) return;
  try {
    await Api.put('/api/driver/location', { lat: myPos[0], lng: myPos[1] });
  } catch (e) {
    console.warn('Location update failed', e);
  }
}

// ------------------------------------------------------------------ status bar

function renderStatus() {
  const online = profile.online;
  const where = myPos
    ? (manualPos ? 'Location set on the map (demo mode). Tap the map to move.' : 'GPS location active. Tap the map to set a demo location.')
    : (gpsBlocked ? 'Location blocked. Tap the map to set your position.' : 'Waiting for GPS… or tap the map to set your position.');
  statusBox.innerHTML = `
    <div class="d-flex justify-content-between align-items-center gap-3">
      <div>
        <div class="fw-bold">${online ? '<span class="rx-badge ONLINE me-1">●</span>You are online' : 'You are offline'}</div>
        <div class="small text-muted">${online ? 'Receiving ride requests nearby' : 'Go online to receive ride requests'}</div>
      </div>
      <div class="form-check form-switch fs-3 mb-0">
        <input class="form-check-input" type="checkbox" role="switch" id="onlineSwitch" ${online ? 'checked' : ''} aria-label="Online">
      </div>
    </div>
    <div class="small mt-2 ${myPos ? 'text-success' : 'text-warning'}"><i class="bi bi-geo-alt-fill me-1"></i>${where}</div>`;
  document.getElementById('onlineSwitch').addEventListener('change', (e) => setOnline(e.target.checked));
}

async function setOnline(on) {
  if (on && !myPos) {
    UI.toast('We need your location first. Allow location access, or tap the map to set it.', 'warning');
    renderStatus();
    return;
  }
  try {
    // Fresh GPS first: the server takes captains with a silent GPS offline
    if (on) await Api.put('/api/driver/location', { lat: myPos[0], lng: myPos[1] });
    profile = await Api.put('/api/driver/availability', { online: on });
    UI.toast(on ? 'You are online. Requests will appear here.' : 'You are offline.', on ? 'success' : 'info');
  } catch (e) {
    UI.toast(e.message);
  }
  renderStatus();
  shownKey = null;
  refresh();
}

// ------------------------------------------------------------------ requests

let ticks = 0;

/** The admin can take a captain offline (vehicle deactivated, account blocked); notice it within ~12 s. */
async function syncProfile() {
  const fresh = await Api.get('/api/driver/profile');
  const changed = fresh.online !== profile.online;
  profile = fresh;
  if (changed) {
    renderStatus();
    shownKey = null;
    if (!fresh.online) UI.toast('You have been taken offline (no GPS for a while, or by the admin). Switch on to continue.', 'warning');
  }
}

async function refresh() {
  if (++ticks % 3 === 0) await syncProfile();
  const active = await Api.get('/api/driver/bookings/active');
  if (active) {
    showRide(active);
    return;
  }
  if (ride) {
    UI.toast(ride.status === 'ACCEPTED' ? 'The passenger cancelled this ride.' : 'Ride closed.', 'warning');
    ride = null;
    clearTripLayers();
  }
  if (!profile.online) {
    if (shownKey !== 'offline') {
      shownKey = 'offline';
      content.innerHTML = UI.empty('bi-moon-stars', 'You are offline. Switch on to start receiving rides.');
    }
    return;
  }
  renderRequests(await Api.get('/api/driver/requests'));
}

function renderRequests(requests) {
  const key = 'req:' + requests.map((r) => r.id).join(',');
  if (key === shownKey) return;
  shownKey = key;
  if (!requests.length) {
    clearTripLayers();
    content.innerHTML = `
      <div class="text-center py-4">
        <div class="searching-pulse mb-3"><i class="bi bi-broadcast"></i></div>
        <div class="fw-bold">Waiting for ride requests…</div>
        <div class="small text-muted">New requests within a few km of you show up here automatically.</div>
      </div>`;
    return;
  }
  content.innerHTML = `<div class="fw-bold mb-2">${requests.length} request${requests.length > 1 ? 's' : ''} near you</div>` +
    requests.map((r) => `
      <div class="border rounded-4 p-3 mb-2 cursor-pointer" data-preview="${r.id}">
        <div class="d-flex justify-content-between align-items-center">
          <div class="fs-5 fw-bold">${UI.money(r.fare)} <span class="small text-muted fw-normal">· ${UI.esc(r.paymentMethod)}</span></div>
          <span class="small text-muted"><i class="bi bi-geo me-1"></i>${r.distanceToPickupKm} km away</span>
        </div>
        <div class="route-line my-2">
          <div class="route-stop small mb-1">${UI.esc(r.pickup.address)}</div>
          <div class="route-stop drop small">${UI.esc(r.drop.address)}</div>
        </div>
        <div class="small text-muted mb-2"><i class="bi bi-person me-1"></i>${UI.esc(r.passengerName)} · trip ${UI.km(r.distanceKm)} · ${r.durationMin} min</div>
        <div class="d-flex gap-2">
          <button class="btn btn-outline-secondary flex-fill" data-reject="${r.id}">Skip</button>
          <button class="btn btn-primary flex-fill" data-accept="${r.id}" data-busy-text="Accepting…">Accept</button>
        </div>
      </div>`).join('');
  const byId = Object.fromEntries(requests.map((r) => [r.id, r]));
  content.querySelectorAll('[data-preview]').forEach((el) => el.addEventListener('click', () => previewTrip(byId[el.dataset.preview])));
  content.querySelectorAll('[data-accept]').forEach((btn) => btn.addEventListener('click', (e) => { e.stopPropagation(); accept(btn); }));
  content.querySelectorAll('[data-reject]').forEach((btn) => btn.addEventListener('click', (e) => { e.stopPropagation(); reject(btn); }));
  previewTrip(requests[0]);
}

function previewTrip(t) {
  layers.pickup = RxMap.placeMarker(map, layers.pickup, 'pickup', [t.pickup.lat, t.pickup.lng]);
  layers.drop = RxMap.placeMarker(map, layers.drop, 'drop', [t.drop.lat, t.drop.lng]);
  layers.route = RxMap.drawRoute(map, layers.route, t.path);
  if (myPos) RxMap.fit(map, [myPos, [t.pickup.lat, t.pickup.lng], [t.drop.lat, t.drop.lng]]);
}

function clearTripLayers() {
  ['pickup', 'drop', 'route'].forEach((k) => { if (layers[k]) { map.removeLayer(layers[k]); layers[k] = null; } });
}

async function accept(button) {
  try {
    const accepted = await UI.busy(button, () => Api.post(`/api/driver/bookings/${button.dataset.accept}/accept`));
    UI.toast('Ride accepted! Head to the pickup point.', 'success');
    showRide(accepted);
  } catch {
    shownKey = null;
    refresh();
  }
}

async function reject(button) {
  button.disabled = true;
  try {
    await Api.post(`/api/driver/bookings/${button.dataset.reject}/reject`);
  } catch (e) {
    UI.toast(e.message);
  }
  shownKey = null;
  refresh();
}

// ------------------------------------------------------------------ active ride

function showRide(b) {
  const key = `ride:${b.id}:${b.status}`;
  ride = b;
  if (key === shownKey) return;
  shownKey = key;
  previewTrip(b);
  const p = b.passenger;
  const navTarget = b.status === 'ACCEPTED' ? b.pickup : b.drop;
  const navUrl = `https://www.openstreetmap.org/directions?engine=fossgis_osrm_car&route=${myPos ? `${myPos[0]},${myPos[1]}` : ''};${navTarget.lat},${navTarget.lng}`;
  const header = b.status === 'ACCEPTED'
    ? `<h5 class="fw-bold mb-1">Pick up ${UI.esc(p.name)}</h5><div class="small text-muted mb-3">Accepted ${UI.dateTime(b.acceptedAt)}</div>`
    : `<h5 class="fw-bold mb-1">Drop ${UI.esc(p.name)}</h5><div class="small text-muted mb-3">Ride started ${UI.dateTime(b.startedAt)}</div>`;

  content.innerHTML = `
    ${header}
    <div class="d-flex gap-3 align-items-center p-3 rounded-4 bg-light mb-3">
      <div class="driver-avatar">${UI.esc(p.name.charAt(0).toUpperCase())}</div>
      <div class="flex-grow-1 min-w-0"><div class="fw-bold text-truncate">${UI.esc(p.name)}</div>
        <div class="small text-muted">${UI.money(b.fare)} · ${UI.esc(b.paymentMethod)}</div></div>
      <a class="btn btn-dark" href="tel:${UI.esc(p.phone)}" title="Call passenger"><i class="bi bi-telephone"></i></a>
    </div>
    <div class="route-line mb-3">
      <div class="route-stop mb-2"><div class="small text-muted">Pickup</div><div class="fw-semibold">${UI.esc(b.pickup.address)}</div></div>
      <div class="route-stop drop"><div class="small text-muted">Drop</div><div class="fw-semibold">${UI.esc(b.drop.address)}</div></div>
    </div>
    <a class="btn btn-outline-dark w-100 mb-3" href="${navUrl}" target="_blank" rel="noopener"><i class="bi bi-sign-turn-right me-1"></i>Navigate to ${b.status === 'ACCEPTED' ? 'pickup' : 'drop'}</a>
    ${b.status === 'ACCEPTED' ? `
      <label class="form-label" for="otpInput">Passenger's 4-digit OTP</label>
      <div class="input-group mb-2">
        <input id="otpInput" class="form-control text-center fs-4 fw-bold" maxlength="4" inputmode="numeric" placeholder="• • • •" autocomplete="off">
        <button class="btn btn-primary px-4" id="startBtn" data-busy-text="Starting…">Start ride</button>
      </div>
      <button class="btn btn-link text-danger w-100" id="releaseBtn">Cancel this ride</button>` : `
      <div class="alert alert-warning small"><i class="bi bi-cash-coin me-1"></i>Collect <b>${UI.money(b.fare)}</b> by ${b.paymentMethod === 'UPI' ? 'UPI' : 'cash'} at the drop.</div>
      <button class="btn btn-primary w-100 py-2" id="completeBtn" data-busy-text="Completing…">Payment collected · Complete ride</button>`}`;

  document.getElementById('startBtn')?.addEventListener('click', startRide);
  document.getElementById('otpInput')?.addEventListener('keydown', (e) => { if (e.key === 'Enter') startRide(); });
  document.getElementById('releaseBtn')?.addEventListener('click', releaseRide);
  document.getElementById('completeBtn')?.addEventListener('click', completeRide);
}

async function startRide() {
  const otp = document.getElementById('otpInput').value.trim();
  if (!/^\d{4}$/.test(otp)) {
    UI.toast('Enter the 4-digit OTP from the passenger.', 'warning');
    return;
  }
  try {
    const started = await UI.busy(document.getElementById('startBtn'), () => Api.post(`/api/driver/bookings/${ride.id}/start`, { otp }));
    UI.toast('Ride started. Drive safe!', 'success');
    showRide(started);
  } catch {
    // error already shown
  }
}

async function completeRide(e) {
  try {
    const done = await UI.busy(e.currentTarget, () => Api.post(`/api/driver/bookings/${ride.id}/complete`));
    UI.toast(`Ride completed. You earned ${UI.money(done.driverEarning)}.`, 'success');
    ride = null;
    shownKey = null;
    clearTripLayers();
    refresh();
  } catch {
    // error already shown
  }
}

async function releaseRide() {
  if (!confirm('Cancel this ride? It will be offered to other captains.')) return;
  try {
    await Api.post(`/api/driver/bookings/${ride.id}/cancel`);
    UI.toast('Ride cancelled.', 'info');
    ride = null;
    shownKey = null;
    clearTripLayers();
    refresh();
  } catch (e) {
    UI.toast(e.message);
  }
}

// ------------------------------------------------------------------ start

(async function init() {
  try {
    await UI.nav();
    profile = await Api.get('/api/driver/profile');
  } catch {
    return;
  }
  if (profile.approvalStatus !== 'APPROVED') {
    statusBox.innerHTML = `<div class="fw-bold">Not approved yet</div>
      <div class="small text-muted">Status: ${UI.badge(profile.approvalStatus)}</div>`;
    content.innerHTML = `${UI.empty('bi-hourglass-split', 'Finish your profile and documents. You can go online once an admin approves you.')}
      <a class="btn btn-primary w-100" href="/driver/onboarding.html">Complete my profile</a>`;
    return;
  }
  renderStatus();
  startGps();
  map.on('click', (e) => {
    if (ride) return; // keep the position honest during a ride
    manualPos = true;
    setMyPos([e.latlng.lat, e.latlng.lng], true);
  });
  setInterval(sendLocation, 15000); // heartbeat: dispatch ignores captains silent for 2+ minutes
  UI.poll(refresh, 4000);
})();
