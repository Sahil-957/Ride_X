const loginForm = document.getElementById('loginForm');
const query = new URLSearchParams(location.search);

function notice(text, type) {
  document.getElementById('notice').innerHTML = `<div class="alert alert-${type} py-2">${UI.esc(text)}</div>`;
}
if (query.has('expired')) notice('Your session has expired. Please log in again.', 'warning');
if (query.has('loggedout')) notice('You have been logged out.', 'success');
if (query.has('blocked')) notice('Your account has been blocked. Contact support.', 'danger');

loginForm.addEventListener('submit', async (event) => {
  event.preventDefault();
  if (!loginForm.checkValidity()) {
    loginForm.classList.add('was-validated');
    return;
  }
  const button = loginForm.querySelector('button[type=submit]');
  try {
    const result = await UI.busy(button, () =>
      Api.post('/api/auth/login', new URLSearchParams(new FormData(loginForm))));
    location.href = result.redirectUrl;
  } catch {
    // UI.busy already showed the error
  }
});
