const DOCS = [
  ['LICENSE', 'Driving licence', 'bi-person-vcard'],
  ['RC', 'Vehicle RC (registration certificate)', 'bi-file-earmark-text'],
  ['ID_PROOF', 'ID proof (Aadhaar / PAN / Voter ID)', 'bi-fingerprint'],
];
const profileForm = document.getElementById('profileForm');
const vehicleForm = document.getElementById('vehicleForm');
let profile = null;

const done = (ok) => ok ? '<span class="rx-badge APPROVED"><i class="bi bi-check2"></i>Done</span>'
  : '<span class="rx-badge PENDING">To do</span>';

function render(p) {
  profile = p;
  const locked = p.approvalStatus === 'APPROVED';
  const banners = {
    PENDING: ['warning', 'bi-hourglass-split', 'Under review',
      'Complete all steps below. An admin will review your details. You can go online once you are approved.'],
    REJECTED: ['danger', 'bi-x-octagon', 'Changes needed',
      `Reason: ${UI.esc(p.rejectionReason || 'not given')}. Update your details below to resubmit.`],
    APPROVED: ['success', 'bi-patch-check', 'You are approved!',
      'Your details are locked. Contact the admin to change them. <a class="alert-link" href="/driver/home.html">Go online now →</a>'],
  };
  const [type, icon, heading, text] = banners[p.approvalStatus];
  document.getElementById('banner').innerHTML = `
    <div class="alert alert-${type} d-flex gap-3 align-items-start border-0 shadow-sm rounded-4">
      <i class="bi ${icon} fs-4"></i><div><div class="fw-bold">${heading}</div><div class="small">${text}</div></div>
    </div>`;

  profileForm.licenseNumber.value = p.licenseNumber || '';
  profileForm.licenseExpiry.value = p.licenseExpiry || '';
  const v = p.vehicle || {};
  ['registrationNumber', 'make', 'model', 'color', 'manufactureYear'].forEach((f) => { vehicleForm[f].value = v[f] ?? ''; });
  [profileForm, vehicleForm].forEach((form) => form.querySelectorAll('input, button').forEach((el) => { el.disabled = locked; }));

  document.getElementById('step1').innerHTML = done(p.licenseExpiry);
  document.getElementById('step2').innerHTML = done(p.vehicle);
  const uploaded = Object.fromEntries(p.documents.map((d) => [d.docType, d]));
  document.getElementById('step3').innerHTML = done(DOCS.every(([t]) => uploaded[t]));
  document.getElementById('documents').innerHTML = DOCS.map(([type, label, glyph]) => {
    const doc = uploaded[type];
    return `
      <div class="d-flex flex-wrap align-items-center gap-3 border rounded-4 p-3">
        <span class="stat-icon ${doc ? 'bg-soft-green' : 'bg-soft-gray'}"><i class="bi ${glyph}"></i></span>
        <div class="flex-grow-1 min-w-0">
          <div class="fw-semibold">${label}</div>
          <div class="small text-muted text-truncate">${doc ? `${UI.esc(doc.fileName)} · uploaded ${UI.dateTime(doc.uploadedAt)}` : 'Not uploaded yet'}</div>
        </div>
        ${locked ? '' : `<label class="btn btn-sm ${doc ? 'btn-outline-dark' : 'btn-dark'} mb-0">
          <i class="bi bi-upload me-1"></i>${doc ? 'Replace' : 'Upload'}
          <input type="file" class="d-none" accept=".pdf,.jpg,.jpeg,.png,application/pdf,image/jpeg,image/png" data-type="${type}"></label>`}
      </div>`;
  }).join('');
  document.querySelectorAll('input[type=file][data-type]').forEach((input) => input.addEventListener('change', () => upload(input)));
}

async function upload(input) {
  const file = input.files[0];
  if (!file) return;
  if (file.size > 2 * 1024 * 1024) {
    UI.toast('That file is larger than 2 MB.');
    input.value = '';
    return;
  }
  const label = input.closest('label');
  label.classList.add('disabled');
  label.innerHTML = '<span class="spinner-border spinner-border-sm me-1"></span>Uploading…';
  const form = new FormData();
  form.append('type', input.dataset.type);
  form.append('file', file);
  try {
    await Api.post('/api/driver/documents', form);
    UI.toast('Document uploaded.', 'success');
  } catch (e) {
    UI.toast(e.message);
  }
  render(await Api.get('/api/driver/profile'));
}

function bindForm(form, url, toBody, successText) {
  form.addEventListener('submit', async (event) => {
    event.preventDefault();
    if (!form.checkValidity()) {
      form.classList.add('was-validated');
      return;
    }
    try {
      const updated = await UI.busy(form.querySelector('button[type=submit]'), () => Api.put(url, toBody()));
      form.classList.remove('was-validated');
      UI.fieldErrors(form, {});
      render(updated);
      UI.toast(successText, 'success');
    } catch (e) {
      form.classList.remove('was-validated');
      UI.fieldErrors(form, e.errors);
    }
  });
}

bindForm(profileForm, '/api/driver/profile', () => ({
  licenseNumber: profileForm.licenseNumber.value,
  licenseExpiry: profileForm.licenseExpiry.value,
}), 'Licence details saved.');

bindForm(vehicleForm, '/api/driver/vehicle', () => ({
  registrationNumber: vehicleForm.registrationNumber.value,
  make: vehicleForm.make.value,
  model: vehicleForm.model.value,
  color: vehicleForm.color.value,
  manufactureYear: Number(vehicleForm.manufactureYear.value),
}), 'Vehicle saved.');

UI.nav()
  .then(() => Api.get('/api/driver/profile'))
  .then(render)
  .catch((e) => { if (e.status !== 401) UI.toast(e.message); });
