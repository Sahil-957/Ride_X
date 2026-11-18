// Small UI helpers shared by every page: navbar, toasts, formatting, loading states, polling.
const UI = (() => {
  const NAV = {
    PASSENGER: [
      ['/passenger/home.html', 'bi-geo-alt', 'Book a ride'],
      ['/passenger/history.html', 'bi-clock-history', 'My rides'],
    ],
    DRIVER: [
      ['/driver/home.html', 'bi-speedometer2', 'Dashboard'],
      ['/driver/earnings.html', 'bi-wallet2', 'Earnings'],
      ['/driver/onboarding.html', 'bi-person-vcard', 'Profile & documents'],
    ],
    ADMIN: [
      ['/admin/dashboard.html', 'bi-grid', 'Dashboard'],
      ['/admin/passengers.html', 'bi-people', 'Passengers'],
      ['/admin/drivers.html', 'bi-person-badge', 'Captains'],
      ['/admin/vehicles.html', 'bi-bicycle', 'Vehicles'],
      ['/admin/bookings.html', 'bi-receipt', 'Bookings'],
    ],
  };

  const esc = (value) => String(value ?? '').replace(/[&<>"']/g,
    (c) => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));

  const inr = new Intl.NumberFormat('en-IN', { style: 'currency', currency: 'INR', maximumFractionDigits: 2 });

  function brand() {
    return `<a class="navbar-brand rx-brand" href="/"><span class="rx-brand-mark"><i class="bi bi-scooter"></i></span><span>Ride<span class="x">X</span></span></a>`;
  }

  /** Renders the role-aware navbar into <nav id="app-nav"> and resolves with the current user. */
  async function nav() {
    let me;
    try {
      me = await Api.get('/api/auth/me');
    } catch (e) {
      // 401 is already redirecting to login; anything else would leave a blank page, so say what happened
      if (e.status !== 401) {
        document.getElementById('app-nav').outerHTML = `<nav class="navbar navbar-dark rx-navbar"><div class="container-fluid px-3">${brand()}</div></nav>
          <div class="alert alert-danger m-3"><i class="bi bi-exclamation-triangle-fill me-2"></i>${esc(e.message)}
            <a class="alert-link ms-1" href="">Try again</a></div>`;
      }
      throw e;
    }
    const links = (NAV[me.role] || []).map(([href, icon, label]) => `
      <li class="nav-item"><a class="nav-link ${location.pathname === href ? 'active' : ''}" href="${href}">
        <i class="bi ${icon} me-1"></i>${label}</a></li>`).join('');
    const initial = esc(me.fullName.trim().charAt(0).toUpperCase());
    document.getElementById('app-nav').outerHTML = `
      <nav class="navbar navbar-expand-lg navbar-dark rx-navbar sticky-top">
        <div class="container-fluid px-3 px-lg-4">
          ${brand()}
          <button class="navbar-toggler border-0" type="button" data-bs-toggle="collapse" data-bs-target="#navMain" aria-label="Menu">
            <span class="navbar-toggler-icon"></span></button>
          <div class="collapse navbar-collapse" id="navMain">
            <ul class="navbar-nav me-auto ms-lg-4 gap-1">${links}</ul>
            <div class="d-flex align-items-center gap-3 py-2 py-lg-0">
              <span class="rx-user-chip d-flex align-items-center gap-2"><span class="avatar">${initial}</span>
                <span><span class="fw-semibold">${esc(me.fullName)}</span><br><small class="text-white-50">${{ PASSENGER: 'passenger', DRIVER: 'captain', ADMIN: 'admin' }[me.role]}</small></span></span>
              <button class="btn btn-outline-light btn-sm" id="logoutBtn"><i class="bi bi-box-arrow-right me-1"></i>Log out</button>
            </div>
          </div>
        </div>
      </nav>`;
    document.getElementById('logoutBtn').addEventListener('click', async () => {
      try { await Api.post('/api/auth/logout'); } finally { location.href = '/login.html?loggedout'; }
    });
    return me;
  }

  function toast(message, type = 'danger') {
    let box = document.getElementById('toastBox');
    if (!box) {
      box = document.createElement('div');
      box.id = 'toastBox';
      box.className = 'toast-container position-fixed top-0 end-0 p-3';
      document.body.appendChild(box);
    }
    const icon = { success: 'bi-check-circle-fill', danger: 'bi-exclamation-triangle-fill', info: 'bi-info-circle-fill', warning: 'bi-exclamation-circle-fill' }[type];
    const el = document.createElement('div');
    el.className = `toast align-items-center text-bg-${type} border-0`;
    el.setAttribute('role', 'alert');
    el.innerHTML = `<div class="d-flex"><div class="toast-body"><i class="bi ${icon} me-2"></i>${esc(message)}</div>
      <button type="button" class="btn-close btn-close-white me-2 m-auto" data-bs-dismiss="toast"></button></div>`;
    box.appendChild(el);
    const t = new bootstrap.Toast(el, { delay: 4500 });
    el.addEventListener('hidden.bs.toast', () => el.remove());
    t.show();
  }

  /** Shows a spinner on a button while `task` runs; reports errors as a toast. */
  async function busy(button, task) {
    const original = button.innerHTML;
    button.disabled = true;
    button.innerHTML = `<span class="spinner-border spinner-border-sm me-2"></span>${button.dataset.busyText || 'Please wait…'}`;
    try {
      return await task();
    } catch (e) {
      toast(e.message);
      throw e;
    } finally {
      button.disabled = false;
      button.innerHTML = original;
    }
  }

  /** Marks invalid inputs from a ProblemDetail `errors` map (field name → message). */
  function fieldErrors(form, errors) {
    form.querySelectorAll('.is-invalid').forEach((el) => el.classList.remove('is-invalid'));
    form.querySelectorAll('.invalid-feedback.dynamic').forEach((el) => el.remove());
    Object.entries(errors || {}).forEach(([field, message]) => {
      const input = form.querySelector(`[name="${field}"]`);
      if (!input) return;
      input.classList.add('is-invalid');
      const fb = document.createElement('div');
      fb.className = 'invalid-feedback dynamic';
      fb.textContent = message;
      input.insertAdjacentElement('afterend', fb);
    });
  }

  /** Runs `fn` now and every `ms`, paused while the tab is hidden. Returns a stop() function. */
  function poll(fn, ms) {
    let timer = null;
    let stopped = false;
    const tick = async () => {
      if (stopped) return;
      if (!document.hidden) { try { await fn(); } catch (e) { console.warn(e); } }
      if (!stopped) timer = setTimeout(tick, ms);
    };
    tick();
    return () => { stopped = true; clearTimeout(timer); };
  }

  const badge = (status) => `<span class="rx-badge ${esc(status)}">${esc(String(status).charAt(0) + String(status).slice(1).toLowerCase())}</span>`;
  const money = (n) => inr.format(Number(n || 0));
  const dateTime = (iso) => iso ? new Date(iso).toLocaleString('en-IN', { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' }) : '—';
  const date = (iso) => iso ? new Date(iso).toLocaleDateString('en-IN', { day: 'numeric', month: 'short', year: 'numeric' }) : '—';
  const km = (n) => `${Number(n).toFixed(1)} km`;
  const stars = (n) => n == null ? '<span class="text-muted">—</span>'
    : `<span class="stars-static">${'★'.repeat(Math.round(n))}${'☆'.repeat(5 - Math.round(n))}</span> <small class="text-muted">${Number(n).toFixed(1)}</small>`;
  const empty = (icon, text) => `<div class="rx-empty"><i class="bi ${icon}"></i>${esc(text)}</div>`;
  const loadingRows = (cols, rows = 4) => Array.from({ length: rows }, () =>
    `<tr>${'<td><div class="skeleton"></div></td>'.repeat(cols)}</tr>`).join('');

  /** Simple SVG bar chart: labels[i] under a bar of height values[i]. No chart library needed. */
  function barChart(element, labels, values, format = (v) => v) {
    const w = 600, h = 220, pad = { top: 18, bottom: 28, side: 8 };
    const max = Math.max(...values, 1);
    const slot = (w - pad.side * 2) / values.length;
    const barW = Math.max(slot * 0.6, 4);
    const bars = values.map((v, i) => {
      const bh = (v / max) * (h - pad.top - pad.bottom);
      const x = pad.side + i * slot + (slot - barW) / 2;
      const y = h - pad.bottom - bh;
      return `<rect class="bar" x="${x}" y="${y}" width="${barW}" height="${Math.max(bh, v > 0 ? 2 : 0)}" rx="5">
          <title>${esc(labels[i])}: ${esc(format(v))}</title></rect>
        ${v > 0 ? `<text x="${x + barW / 2}" y="${y - 5}" text-anchor="middle">${esc(format(v))}</text>` : ''}
        <text x="${x + barW / 2}" y="${h - 9}" text-anchor="middle">${esc(labels[i])}</text>`;
    }).join('');
    element.innerHTML = `<svg class="rx-chart" viewBox="0 0 ${w} ${h}" preserveAspectRatio="none" role="img">
      <line x1="0" x2="${w}" y1="${h - pad.bottom}" y2="${h - pad.bottom}" stroke="#e5e7eb"/>${bars}</svg>`;
  }

  const shortDay = (isoDate) => new Date(isoDate + 'T00:00:00').toLocaleDateString('en-IN', { day: 'numeric', month: 'short' });

  return { esc, nav, brand, toast, busy, fieldErrors, poll, badge, money, dateTime, date, km, stars, empty, loadingRows, barChart, shortDay };
})();
