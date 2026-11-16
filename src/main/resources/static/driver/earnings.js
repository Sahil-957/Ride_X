const history = document.getElementById('history');
history.innerHTML = UI.loadingRows(6);

function statCard(icon, tone, label, value, sub) {
  return `<div class="col-6 col-lg-3"><div class="rx-card stat-card h-100">
    <span class="stat-icon ${tone}"><i class="bi ${icon}"></i></span>
    <div class="min-w-0"><div class="stat-label">${label}</div><div class="stat-value">${value}</div>
      <div class="small text-muted">${sub}</div></div></div></div>`;
}

async function load() {
  try {
    const [earnings, rides] = await Promise.all([Api.get('/api/driver/earnings'), Api.get('/api/driver/bookings')]);
    const plural = (n) => `${n} ride${n === 1 ? '' : 's'}`;
    document.getElementById('cards').innerHTML =
      statCard('bi-sun', 'bg-soft-yellow', 'Today', UI.money(earnings.today.earning), plural(earnings.today.rides)) +
      statCard('bi-calendar-week', 'bg-soft-blue', 'Last 7 days', UI.money(earnings.week.earning), plural(earnings.week.rides)) +
      statCard('bi-calendar3', 'bg-soft-green', 'Last 30 days', UI.money(earnings.month.earning), plural(earnings.month.rides)) +
      statCard('bi-star', 'bg-soft-gray', 'Rating', earnings.rating ? `${earnings.rating.toFixed(1)} ★` : '—',
        `${plural(earnings.completedRides)} completed`);

    UI.barChart(document.getElementById('chart'),
      earnings.daily.map((d) => UI.shortDay(d.date)),
      earnings.daily.map((d) => Number(d.earning)),
      (v) => `₹${Math.round(v)}`);

    history.innerHTML = rides.length ? rides.map((r) => `
      <tr>
        <td class="text-nowrap">${UI.dateTime(r.requestedAt)}</td>
        <td>${UI.esc(r.passenger.name)}</td>
        <td class="small" style="min-width: 220px">${UI.esc(r.pickup.address)}<br><span class="text-muted">→ ${UI.esc(r.drop.address)}</span></td>
        <td>${UI.money(r.fare)}</td>
        <td class="fw-semibold">${r.paymentStatus === 'PAID' ? UI.money(r.driverEarning) : '—'}</td>
        <td>${UI.badge(r.status)}${r.stars ? `<div class="small">${UI.stars(r.stars)}</div>` : ''}</td>
      </tr>`).join('') : `<tr><td colspan="6">${UI.empty('bi-scooter', 'No rides yet. Go online to start earning.')}</td></tr>`;
  } catch (e) {
    UI.toast(e.message);
  }
}

UI.nav().then(load).catch(() => {});
