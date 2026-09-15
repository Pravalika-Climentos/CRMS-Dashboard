(() => {
  const form = document.getElementById('loginForm');
  const message = document.getElementById('authMessage');
  const button = document.getElementById('loginBtn');
  const modeButton = document.getElementById('authModeBtn');
  let registering = false;
  document.getElementById('year').textContent = new Date().getFullYear();

  document.getElementById('togglePassword').addEventListener('click', () => {
    const password = document.getElementById('password');
    password.type = password.type === 'password' ? 'text' : 'password';
  });

  modeButton.addEventListener('click', () => {
    registering = !registering;
    document.getElementById('nameGroup').hidden = !registering;
    document.getElementById('fullName').required = registering;
    document.getElementById('authTitle').textContent = registering ? 'Create account' : 'Sign in';
    document.getElementById('authSubtitle').textContent = registering
      ? 'Create your CRM account to continue.'
      : 'Enter your account details to continue.';
    document.getElementById('authSwitchText').textContent = registering
      ? 'Already have an account?'
      : "Don't have an account?";
    modeButton.textContent = registering ? 'Sign in' : 'Create account';
    button.textContent = registering ? 'Create account' : 'Sign in';
    document.getElementById('password').autocomplete = registering ? 'new-password' : 'current-password';
    message.textContent = '';
    message.classList.remove('show');
  });

  form.addEventListener('submit', async event => {
    event.preventDefault();
    message.textContent = '';
    button.disabled = true;
    try {
      const response = await fetch(registering ? '/api/auth/register' : '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(registering ? {
          fullName: document.getElementById('fullName').value.trim(),
          email: document.getElementById('email').value.trim(),
          password: document.getElementById('password').value
        } : {
          email: document.getElementById('email').value.trim(),
          password: document.getElementById('password').value
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) throw new Error(body.message || (registering ? 'Account creation failed.' : 'Sign in failed.'));
      window.CrmsAuth.saveSession(body);
      const requested = new URLSearchParams(location.search).get('returnTo');
      const safeTarget = requested && /^[a-zA-Z0-9._-]+\.html$/.test(requested) ? requested : 'index.html';
      location.replace(safeTarget);
    } catch (error) {
      message.textContent = error.message;
      message.classList.add('show');
      button.disabled = false;
    }
  });
})();
