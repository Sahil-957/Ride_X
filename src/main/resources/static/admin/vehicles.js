const rows = document.getElementById('rows');
const searchBox = document.getElementById('search');
let vehicles = [];

function render() {
  const q = searchBox.value.trim().toLowerCase();
  const list = vehicles.filter((v) => !q || `${v.registrationNumber} ${v.make} ${v.model} ${v.driverName}`.toLowerCase().includes(q));
  rows.innerHTML = list.length ? list.map((v) => `
    <tr>
      <td><span class="plate">${UI.esc(v.registrationNumber)}</span></td>
      <td>${UI.esc(v.make)} ${UI.esc(v.model)}</td>
      <td>${UI.esc(v.color)}</td>
      <td>${v.manufactureYear}</td>
      <td>${UI.esc(v.driverName)}</td>
      <td>${UI.badge(v.status)}</td>
      <td class="text-end">${v.status === 'ACTIVE'
        ? `<button class="btn btn-sm btn-outline-danger" data-id="${v.id}" data-status="INACTIVE">Deactivate</button>`
        : `<button class="btn btn-sm btn-outline-success" data-id="${v.id}" data-status="ACTIVE">Activate</button>`}</td>
    </tr>`).join('') : `<tr><td colspan="7">${UI.empty('bi-bicycle', q ? 'No vehicles match your search.' : 'No vehicles registered yet.')}</td></tr>`;
  rows.querySelectorAll('[data-status]').forEach((btn) => btn.addEventListener('click', async () => {
    try {
      await UI.busy(btn, () => Api.patch(`/api/admin/vehicles/${btn.dataset.id}/status`, { status: btn.dataset.status }));
      UI.toast('Vehicle updated.', 'success');
      load();
    } catch {
      // error already shown
    }
  }));
}

async function load() {
  try {
    vehicles = await Api.get('/api/admin/vehicles');
    render();
  } catch (e) {
    rows.innerHTML = `<tr><td colspan="7" class="text-danger">${UI.esc(e.message)}</td></tr>`;
  }
}

rows.innerHTML = UI.loadingRows(7);
searchBox.addEventListener('input', render);
UI.nav().then(load).catch(() => {});
