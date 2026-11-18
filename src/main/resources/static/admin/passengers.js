const rows = document.getElementById('rows');
const searchBox = document.getElementById('search');
let passengers = [];

function render() {
  const q = searchBox.value.trim().toLowerCase();
  const list = passengers.filter((p) => !q || `${p.fullName} ${p.email} ${p.phone}`.toLowerCase().includes(q));
  document.getElementById('summary').textContent =
    `${passengers.length} registered · ${passengers.filter((p) => p.status === 'BLOCKED').length} blocked`;
  rows.innerHTML = list.length ? list.map((p) => `
    <tr>
      <td class="fw-semibold">${UI.esc(p.fullName)}</td>
      <td>${UI.esc(p.email)}</td>
      <td>${UI.esc(p.phone)}</td>
      <td>${p.rides}</td>
      <td class="text-nowrap">${UI.date(p.createdAt)}</td>
      <td>${UI.badge(p.status)}</td>
      <td class="text-end">${p.status === 'ACTIVE'
        ? `<button class="btn btn-sm btn-outline-danger" data-id="${p.id}" data-status="BLOCKED"><i class="bi bi-slash-circle me-1"></i>Block</button>`
        : `<button class="btn btn-sm btn-outline-success" data-id="${p.id}" data-status="ACTIVE"><i class="bi bi-check-circle me-1"></i>Unblock</button>`}</td>
    </tr>`).join('') : `<tr><td colspan="7">${UI.empty('bi-people', q ? 'No passengers match your search.' : 'No passengers yet.')}</td></tr>`;
  rows.querySelectorAll('[data-status]').forEach((btn) => btn.addEventListener('click', () => setStatus(btn)));
}

async function setStatus(button) {
  const blocking = button.dataset.status === 'BLOCKED';
  if (blocking && !confirm('Block this passenger? They are logged out and will not be able to log in.')) return;
  try {
    await UI.busy(button, () => Api.patch(`/api/admin/users/${button.dataset.id}/status`, { status: button.dataset.status }));
    UI.toast(blocking ? 'Passenger blocked.' : 'Passenger unblocked.', 'success');
    load();
  } catch {
    // error already shown
  }
}

async function load() {
  try {
    passengers = await Api.get('/api/admin/passengers');
    render();
  } catch (e) {
    rows.innerHTML = `<tr><td colspan="7" class="text-danger">${UI.esc(e.message)}</td></tr>`;
  }
}

rows.innerHTML = UI.loadingRows(7);
searchBox.addEventListener('input', render);
UI.nav().then(load).catch(() => {});
