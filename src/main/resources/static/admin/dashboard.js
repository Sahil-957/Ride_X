const liveMap = RxMap.create('liveMap', RxMap.DEFAULT_CENTER, 12);
const liveLayer = L.layerGroup().addTo(liveMap);
let fittedOnce = false;

function stat(icon, tone, label, value, sub = '') {
  return `<div class="col-6 col-md-4 col-xl-2"><div class="rx-card stat-card h-100">
    <span class="stat-icon ${tone}"><i class="bi ${icon}"></i></span>
    <div class="min-w-0"><div class="stat-label">${label}</div><div class="stat-value">${value}</div>
      ${sub ? `<div class="small text-muted">${sub}</div>` : ''}</div></div></div>`;
}

async function loadStats() {
  const s = await Api.get('/api/admin/stats');
  document.getElementById('stats').innerHTML =
    stat('bi-people', 'bg-soft-blue', 'Passengers', s.passengers) +
    stat('bi-person-badge', 'bg-soft-yellow', 'Captains', s.drivers, `${s.approvedDrivers} approved · ${s.pendingDrivers} pending`) +
    stat('bi-broadcast', 'bg-soft-green', 'Online now', s.onlineDrivers) +
    stat('bi-scooter', 'bg-soft-yellow', 'Active rides', s.activeRides, `${s.openRequests} waiting for a captain`) +
    stat('bi-check2-circle', 'bg-soft-green', 'Completed today', s.ridesToday) +
    stat('bi-currency-rupee', 'bg-soft-gray', 'Revenue today', UI.money(s.revenueToday), `${UI.money(s.commissionToday)} commission`);
  document.getElementById('pendingAlert').innerHTML = s.pendingDrivers ? `
    <div class="alert alert-warning border-0 shadow-sm rounded-4 d-flex align-items-center gap-2">
      <i class="bi bi-person-exclamation fs-5"></i>
      <div class="flex-grow-1">${s.pendingDrivers} captain${s.pendingDrivers > 1 ? 's are' : ' is'} waiting for approval.</div>
      <a class="btn btn-sm btn-dark" href="/admin/drivers.html">Review</a></div>` : '';
}

async function loadAnalytics() {
  const a = await Api.get('/api/admin/analytics?days=14');
  document.getElementById('revenueTotals').innerHTML = `
    <div><div class="stat-label">Revenue</div><div class="fs-4 fw-bold">${UI.money(a.revenue)}</div></div>
    <div><div class="stat-label">Commission</div><div class="fs-4 fw-bold">${UI.money(a.commission)}</div></div>
    <div><div class="stat-label">Completed rides</div><div class="fs-4 fw-bold">${a.rides}</div></div>`;
  UI.barChart(document.getElementById('revenueChart'),
    a.daily.map((d) => UI.shortDay(d.date)), a.daily.map((d) => Number(d.revenue)), (v) => `₹${Math.round(v)}`);
  const total = Object.values(a.statusCounts).reduce((x, y) => x + y, 0) || 1;
  document.getElementById('statusBreakdown').innerHTML = Object.entries(a.statusCounts).map(([status, count]) => `
    <div class="mb-3">
      <div class="d-flex justify-content-between small mb-1">${UI.badge(status)}<span class="fw-semibold">${count}</span></div>
      <div class="progress" style="height: 6px"><div class="progress-bar bg-dark" style="width: ${(count / total) * 100}%"></div></div>
    </div>`).join('');
}

async function loadLive() {
  const live = await Api.get('/api/admin/live');
  liveLayer.clearLayers();
  const points = [];
  live.drivers.forEach((d) => {
    L.marker([d.lat, d.lng], { icon: RxMap.icon('driver') })
      .bindTooltip(`${UI.esc(d.name)} · ${d.busy ? 'on a ride' : 'available'}`).addTo(liveLayer);
    points.push([d.lat, d.lng]);
  });
  live.rides.forEach((r) => {
    L.marker([r.pickup.lat, r.pickup.lng], { icon: RxMap.icon('pickup') }).bindTooltip(`#${r.id} pickup`).addTo(liveLayer);
    L.marker([r.drop.lat, r.drop.lng], { icon: RxMap.icon('drop') }).bindTooltip(`#${r.id} drop`).addTo(liveLayer);
    if (r.path) L.polyline(r.path, { color: r.status === 'REQUESTED' ? '#f2b705' : '#111827', weight: 4, opacity: .8 }).addTo(liveLayer);
    points.push([r.pickup.lat, r.pickup.lng], [r.drop.lat, r.drop.lng]);
  });
  if (!fittedOnce && points.length) {
    RxMap.fit(liveMap, points);
    fittedOnce = true;
  }
  document.getElementById('liveCount').textContent = live.rides.length;
  document.getElementById('liveList').innerHTML = live.rides.length ? live.rides.map((r) => `
    <div class="p-3 border-bottom">
      <div class="d-flex justify-content-between"><span class="fw-semibold">#${r.id} · ${UI.esc(r.passenger.name)}</span>${UI.badge(r.status)}</div>
      <div class="small text-muted text-truncate">${UI.esc(r.pickup.address)} → ${UI.esc(r.drop.address)}</div>
      <div class="small">${r.driver ? `<i class="bi bi-scooter me-1"></i>${UI.esc(r.driver.name)}` : '<span class="text-warning">Waiting for a captain</span>'}
        · ${UI.money(r.fare)}</div>
    </div>`).join('') : UI.empty('bi-cup-hot', 'No open rides right now.');
  document.getElementById('updatedAt').textContent = `Updated ${new Date().toLocaleTimeString('en-IN')}`;
}

UI.nav().then(() => {
  loadAnalytics().catch((e) => UI.toast(e.message));
  UI.poll(() => Promise.all([loadStats(), loadLive()]), 10000);
}).catch(() => {});
