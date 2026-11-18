const rows = document.getElementById('rows');
const searchBox = document.getElementById('search');
const reviewModal = new bootstrap.Modal(document.getElementById('reviewModal'));
const DOC_LABELS = { LICENSE: 'Driving licence', RC: 'Vehicle RC', ID_PROOF: 'ID proof' };
let filter = 'PENDING';
let drivers = [];

function render() {
  const q = searchBox.value.trim().toLowerCase();
  const list = drivers.filter((d) => !q || `${d.fullName} ${d.phone} ${d.licenseNumber} ${d.vehicleNumber || ''}`.toLowerCase().includes(q));
  rows.innerHTML = list.length ? list.map((d) => `
    <tr>
      <td><div class="fw-semibold">${UI.esc(d.fullName)}</div><div class="small text-muted">${UI.esc(d.email)}</div></td>
      <td>${UI.esc(d.phone)}</td>
      <td class="small">${UI.esc(d.licenseNumber)}</td>
      <td>${d.vehicleNumber ? `<span class="plate">${UI.esc(d.vehicleNumber)}</span>` : '<span class="text-muted small">Not added</span>'}</td>
      <td>${UI.stars(d.rating)}</td>
      <td>${d.completedRides}</td>
      <td>${UI.badge(d.approvalStatus)} ${d.online ? UI.badge('ONLINE') : ''} ${d.accountStatus === 'BLOCKED' ? UI.badge('BLOCKED') : ''}</td>
      <td class="text-end"><button class="btn btn-sm btn-dark" data-review="${d.id}">Review</button></td>
    </tr>`).join('') : `<tr><td colspan="8">${UI.empty('bi-person-badge', q ? 'No captains match your search.' : 'No captains in this list.')}</td></tr>`;
  rows.querySelectorAll('[data-review]').forEach((btn) => btn.addEventListener('click', () => review(btn.dataset.review)));
}

async function load() {
  rows.innerHTML = UI.loadingRows(8);
  try {
    drivers = await Api.get(`/api/admin/drivers${filter ? `?approval=${filter}` : ''}`);
    render();
  } catch (e) {
    rows.innerHTML = `<tr><td colspan="8" class="text-danger">${UI.esc(e.message)}</td></tr>`;
  }
}

async function review(id) {
  document.getElementById('reviewBody').innerHTML = '<div class="skeleton mb-2" style="height: 120px"></div><div class="skeleton" style="height: 120px"></div>';
  document.getElementById('reviewFooter').innerHTML = '';
  reviewModal.show();
  try {
    showReview(await Api.get(`/api/admin/drivers/${id}`));
  } catch (e) {
    document.getElementById('reviewBody').innerHTML = `<div class="alert alert-danger">${UI.esc(e.message)}</div>`;
  }
}

function showReview(d) {
  const v = d.vehicle;
  const docs = Object.fromEntries(d.documents.map((doc) => [doc.docType, doc]));
  const row = (label, value) => `<div class="col-6 col-md-4"><div class="small text-muted">${label}</div><div class="fw-semibold">${value}</div></div>`;
  document.getElementById('reviewTitle').innerHTML = `${UI.esc(d.fullName)} ${UI.badge(d.approvalStatus)}`;
  document.getElementById('reviewBody').innerHTML = `
    ${d.rejectionReason ? `<div class="alert alert-danger small"><b>Rejected:</b> ${UI.esc(d.rejectionReason)}</div>` : ''}
    <h6 class="fw-bold">Captain</h6>
    <div class="row g-3 mb-4">
      ${row('Email', UI.esc(d.email))}${row('Phone', UI.esc(d.phone))}${row('Account', UI.badge(d.accountStatus))}
      ${row('Licence number', UI.esc(d.licenseNumber))}${row('Licence valid until', d.licenseExpiry ? UI.date(d.licenseExpiry) : '<span class="text-danger">Missing</span>')}
      ${row('Rating', UI.stars(d.rating))}
    </div>
    <h6 class="fw-bold">Vehicle</h6>
    <div class="row g-3 mb-4">${v
      ? row('Number', `<span class="plate">${UI.esc(v.registrationNumber)}</span>`) + row('Make & model', `${UI.esc(v.make)} ${UI.esc(v.model)}`)
        + row('Colour / year', `${UI.esc(v.color)} · ${v.manufactureYear}`)
      : '<div class="col-12 text-danger small">No vehicle added yet.</div>'}</div>
    <h6 class="fw-bold">Documents</h6>
    <div class="vstack gap-2">${Object.entries(DOC_LABELS).map(([type, label]) => {
      const doc = docs[type];
      return `<div class="d-flex align-items-center gap-3 border rounded-4 p-2 px-3">
        <i class="bi ${doc ? 'bi-file-earmark-check text-success' : 'bi-file-earmark-x text-danger'} fs-4"></i>
        <div class="flex-grow-1 min-w-0"><div class="fw-semibold">${label}</div>
          <div class="small text-muted text-truncate">${doc ? `${UI.esc(doc.fileName)} · ${UI.dateTime(doc.uploadedAt)}` : 'Not uploaded'}</div></div>
        ${doc ? `<a class="btn btn-sm btn-outline-dark" href="/api/admin/documents/${doc.id}" target="_blank" rel="noopener"><i class="bi bi-box-arrow-up-right me-1"></i>Open</a>` : ''}
      </div>`;
    }).join('')}</div>
    <div class="mt-4 d-none" id="rejectBox">
      <label class="form-label" for="rejectReason">Reason for rejection (shown to the captain)</label>
      <textarea class="form-control" id="rejectReason" rows="2" maxlength="255" placeholder="e.g. RC photo is blurry, please re-upload"></textarea>
    </div>`;

  const footer = document.getElementById('reviewFooter');
  const blocked = d.accountStatus === 'BLOCKED';
  footer.innerHTML = `
    <button class="btn ${blocked ? 'btn-outline-success' : 'btn-outline-secondary'} me-auto" id="blockBtn" data-busy-text="Saving…">
      <i class="bi ${blocked ? 'bi-check-circle' : 'bi-slash-circle'} me-1"></i>${blocked ? 'Unblock account' : 'Block account'}</button>
    ${d.approvalStatus !== 'REJECTED' ? '<button class="btn btn-outline-danger" id="rejectBtn" data-busy-text="Rejecting…"><i class="bi bi-x-lg me-1"></i>Reject</button>' : ''}
    ${d.approvalStatus !== 'APPROVED' ? '<button class="btn btn-success" id="approveBtn" data-busy-text="Approving…"><i class="bi bi-check-lg me-1"></i>Approve</button>' : ''}`;
  document.getElementById('blockBtn').addEventListener('click', async (e) => {
    if (!blocked && !confirm(`Block ${d.fullName}? They are logged out and cannot log in or go online.`)) return;
    try {
      await UI.busy(e.currentTarget, () => Api.patch(`/api/admin/users/${d.id}/status`, { status: blocked ? 'ACTIVE' : 'BLOCKED' }));
      UI.toast(blocked ? `${d.fullName} is unblocked.` : `${d.fullName} is blocked and logged out.`, 'success');
      reviewModal.hide();
      load();
    } catch {
      // error already shown
    }
  });
  document.getElementById('approveBtn')?.addEventListener('click', async (e) => {
    try {
      await UI.busy(e.currentTarget, () => Api.post(`/api/admin/drivers/${d.id}/approve`));
      UI.toast(`${d.fullName} is approved and can now go online.`, 'success');
      reviewModal.hide();
      load();
    } catch {
      // error already shown
    }
  });
  document.getElementById('rejectBtn')?.addEventListener('click', async (e) => {
    const box = document.getElementById('rejectBox');
    if (box.classList.contains('d-none')) {
      box.classList.remove('d-none');
      document.getElementById('rejectReason').focus();
      e.currentTarget.innerHTML = '<i class="bi bi-x-lg me-1"></i>Confirm rejection';
      return;
    }
    const reason = document.getElementById('rejectReason').value.trim();
    if (!reason) {
      UI.toast('Please give a reason so the captain can fix it.', 'warning');
      return;
    }
    try {
      await UI.busy(e.currentTarget, () => Api.post(`/api/admin/drivers/${d.id}/reject`, { reason }));
      UI.toast('Captain rejected. They have been told why.', 'info');
      reviewModal.hide();
      load();
    } catch {
      // error already shown
    }
  });
}

document.querySelectorAll('#tabs [data-filter]').forEach((tab) => tab.addEventListener('click', () => {
  document.querySelectorAll('#tabs .nav-link').forEach((t) => t.classList.toggle('active', t === tab));
  filter = tab.dataset.filter;
  load();
}));
searchBox.addEventListener('input', render);
UI.nav().then(load).catch(() => {});
