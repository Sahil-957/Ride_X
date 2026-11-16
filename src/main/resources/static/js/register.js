const registerForm = document.getElementById('registerForm');
const licenseInput = document.getElementById('licenseNumber');
let role = new URLSearchParams(location.search).get('role') === 'driver' ? 'driver' : 'passenger';

function setRole(next) {
  role = next;
  document.querySelectorAll('[data-role]').forEach((btn) => {
    btn.classList.toggle('btn-dark', btn.dataset.role === role);
    btn.classList.toggle('btn-outline-dark', btn.dataset.role !== role);
  });
  document.getElementById('licenseGroup').classList.toggle('d-none', role !== 'driver');
  licenseInput.required = role === 'driver';
  document.getElementById('sidePassenger').classList.toggle('d-none', role === 'driver');
  document.getElementById('sideDriver').classList.toggle('d-none', role !== 'driver');
}

document.querySelectorAll('[data-role]').forEach((btn) => btn.addEventListener('click', () => setRole(btn.dataset.role)));
setRole(role);

registerForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!registerForm.checkValidity()) {
    registerForm.classList.add('was-validated');
    return;
  }
  const body = Object.fromEntries(new FormData(registerForm));
  if (role !== 'driver') delete body.licenseNumber;
  const button = registerForm.querySelector('button[type=submit]');
  try {
    const result = await UI.busy(button, async () => {
      await Api.post(`/api/auth/register/${role}`, body);
      // Log straight in with the same credentials
      return Api.post('/api/auth/login', new URLSearchParams({ email: body.email, password: body.password }));
    });
    location.href = result.redirectUrl;
  } catch (e) {
    registerForm.classList.remove('was-validated');
    UI.fieldErrors(registerForm, e.errors);
  }
});
