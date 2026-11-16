const ridesBox = document.getElementById('rides');
const rateModal = new bootstrap.Modal(document.getElementById('rateModal'));
let rides = [];
let rating = { id: null, stars: 0 };

function card(r) {
  const canRate = r.status === 'COMPLETED' && !r.stars;
  return `
    <div class="col-12 col-lg-6">
      <div class="rx-card rx-card-body h-100">
        <div class="d-flex justify-content-between align-items-start mb-2">
          <div><div class="fw-bold">${UI.dateTime(r.requestedAt)}</div>
            <div class="small text-muted">Ride #${r.id} · ${UI.km(r.distanceKm)} · ${r.durationMin} min</div></div>
          <div class="text-end">${UI.badge(r.status)}<div class="fw-bold mt-1">${UI.money(r.fare)}</div></div>
        </div>
        <div class="route-line my-2">
          <div class="route-stop mb-2 small">${UI.esc(r.pickup.address)}</div>
          <div class="route-stop drop small">${UI.esc(r.drop.address)}</div>
        </div>
        <div class="d-flex justify-content-between align-items-center border-top pt-2 small">
          <span class="text-muted">${r.driver ? `<i class="bi bi-person me-1"></i>${UI.esc(r.driver.name)}${r.vehicle ? ` · <span class="plate">${UI.esc(r.vehicle.registrationNumber)}</span>` : ''}` : 'No captain assigned'}</span>
          <span>${r.stars ? UI.stars(r.stars) : canRate ? `<button class="btn btn-sm btn-primary" data-rate="${r.id}"><i class="bi bi-star me-1"></i>Rate</button>` : `<span class="text-muted">${UI.esc(r.paymentMethod || '')}</span>`}</span>
        </div>
      </div>
    </div>`;
}

async function load() {
  try {
    rides = await Api.get('/api/passenger/bookings');
    ridesBox.innerHTML = rides.length ? rides.map(card).join('')
      : `<div class="col-12"><div class="rx-card">${UI.empty('bi-scooter', 'No rides yet. Book your first ride!')}</div></div>`;
    ridesBox.querySelectorAll('[data-rate]').forEach((btn) => btn.addEventListener('click', () => openRating(Number(btn.dataset.rate))));
  } catch (e) {
    ridesBox.innerHTML = `<div class="col-12"><div class="alert alert-danger">${UI.esc(e.message)}</div></div>`;
  }
}

function openRating(id) {
  const ride = rides.find((r) => r.id === id);
  rating = { id, stars: 0 };
  document.getElementById('rateWho').textContent = `How was your ride with ${ride.driver?.name || 'your captain'}?`;
  document.getElementById('rateComment').value = '';
  document.getElementById('rateSubmit').disabled = true;
  const stars = document.getElementById('rateStars');
  stars.innerHTML = [1, 2, 3, 4, 5].map((i) => `<i class="bi bi-star-fill" data-v="${i}"></i>`).join(' ');
  stars.querySelectorAll('[data-v]').forEach((s) => s.addEventListener('click', () => {
    rating.stars = Number(s.dataset.v);
    stars.querySelectorAll('[data-v]').forEach((x) => x.classList.toggle('on', Number(x.dataset.v) <= rating.stars));
    document.getElementById('rateSubmit').disabled = false;
  }));
  rateModal.show();
}

document.getElementById('rateSubmit').addEventListener('click', async (e) => {
  try {
    await UI.busy(e.currentTarget, () => Api.post(`/api/passenger/bookings/${rating.id}/rating`,
      { stars: rating.stars, comment: document.getElementById('rateComment').value }));
    rateModal.hide();
    UI.toast('Thanks for your feedback!', 'success');
    load();
  } catch {
    // error already shown
  }
});

UI.nav().then(load).catch(() => {});
