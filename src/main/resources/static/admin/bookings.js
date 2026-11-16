const rows = document.getElementById('rows');
const statusSelect = document.getElementById('status');
const searchBox = document.getElementById('search');
const detailModal = new bootstrap.Modal(document.getElementById('detailModal'));
let bookings = [];

function render() {
  const q = searchBox.value.trim().toLowerCase();
  const list = bookings.filter((b) => !q || [b.passenger.name, b.driver?.name, b.pickup.address, b.drop.address, b.id]
    .join(' ').toLowerCase().includes(q));
  rows.innerHTML = list.length ? list.map((b) => `
    <tr class="cursor-pointer" data-id="${b.id}">
      <td class="fw-semibold">${b.id}</td>
      <td class="text-nowrap">${UI.dateTime(b.requestedAt)}</td>
      <td>${UI.esc(b.passenger.name)}</td>
      <td>${b.driver ? UI.esc(b.driver.name) : '<span class="text-muted">—</span>'}</td>
      <td class="small" style="min-width: 240px">${UI.esc(b.pickup.address)}<br><span class="text-muted">→ ${UI.esc(b.drop.address)}</span></td>
      <td class="text-nowrap">${UI.money(b.fare)}<div class="small text-muted">${UI.km(b.distanceKm)}</div></td>
      <td>${UI.esc(b.paymentMethod || '')} ${b.paymentStatus ? UI.badge(b.paymentStatus) : ''}</td>
      <td>${UI.badge(b.status)}</td>
    </tr>`).join('') : `<tr><td colspan="8">${UI.empty('bi-receipt', 'No bookings found.')}</td></tr>`;
  rows.querySelectorAll('[data-id]').forEach((tr) => tr.addEventListener('click', () => showDetail(Number(tr.dataset.id))));
}

function showDetail(id) {
  const b = bookings.find((x) => x.id === id);
  const item = (label, value) => `<div class="col-6 col-md-4"><div class="small text-muted">${label}</div><div class="fw-semibold">${value}</div></div>`;
  const timeline = [['Requested', b.requestedAt], ['Accepted', b.acceptedAt], ['Started', b.startedAt],
    ['Completed', b.completedAt], ['Cancelled', b.cancelledAt]].filter(([, t]) => t);
  document.getElementById('detailTitle').innerHTML = `Booking #${b.id} ${UI.badge(b.status)}`;
  document.getElementById('detailBody').innerHTML = `
    <div class="route-line mb-4">
      <div class="route-stop mb-2"><div class="small text-muted">Pickup</div><div class="fw-semibold">${UI.esc(b.pickup.address)}</div></div>
      <div class="route-stop drop"><div class="small text-muted">Drop</div><div class="fw-semibold">${UI.esc(b.drop.address)}</div></div>
    </div>
    <div class="row g-3 mb-4">
      ${item('Passenger', `${UI.esc(b.passenger.name)}<div class="small text-muted">${UI.esc(b.passenger.phone)}</div>`)}
      ${item('Captain', b.driver ? `${UI.esc(b.driver.name)}<div class="small text-muted">${UI.esc(b.driver.phone)}</div>` : '—')}
      ${item('Vehicle', b.vehicle ? `<span class="plate">${UI.esc(b.vehicle.registrationNumber)}</span>` : '—')}
      ${item('Distance / time', `${UI.km(b.distanceKm)} · ${b.durationMin} min`)}
      ${item('Fare', UI.money(b.fare))}
      ${item('Payment', `${UI.esc(b.paymentMethod)} ${UI.badge(b.paymentStatus)}`)}
      ${item('Commission', b.paymentStatus === 'PAID' ? UI.money(b.commission) : '—')}
      ${item('Captain earned', b.paymentStatus === 'PAID' ? UI.money(b.driverEarning) : '—')}
      ${item('Rating', b.stars ? UI.stars(b.stars) : '—')}
    </div>
    ${b.ratingComment ? `<div class="alert alert-light border small"><i class="bi bi-chat-quote me-1"></i>${UI.esc(b.ratingComment)}</div>` : ''}
    <h6 class="fw-bold">Timeline</h6>
    <ul class="list-unstyled small mb-0">${timeline.map(([label, t]) =>
      `<li class="mb-1"><i class="bi bi-dot"></i>${label}: <b>${UI.dateTime(t)}</b>${label === 'Cancelled' && b.cancelledBy ? ` by ${UI.esc(b.cancelledBy.toLowerCase())}` : ''}</li>`).join('')}</ul>`;
  detailModal.show();
}

async function load() {
  rows.innerHTML = UI.loadingRows(8);
  try {
    bookings = await Api.get(`/api/admin/bookings${statusSelect.value ? `?status=${statusSelect.value}` : ''}`);
    render();
  } catch (e) {
    rows.innerHTML = `<tr><td colspan="8" class="text-danger">${UI.esc(e.message)}</td></tr>`;
  }
}

statusSelect.addEventListener('change', load);
searchBox.addEventListener('input', render);
UI.nav().then(load).catch(() => {});
