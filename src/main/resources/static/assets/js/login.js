(() => {
  const form = document.getElementById('loginForm');
  const message = document.getElementById('authMessage');
  const button = document.getElementById('loginBtn');
  const modeButton = document.getElementById('authModeBtn');
  let registering = false;
  const PASSWORD_HELP = 'Password must be 8–72 characters and include at least one letter, one number, and one special character. Spaces are not allowed.';
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
      const fullName = document.getElementById('fullName').value.trim();
      const email = document.getElementById('email').value.trim();
      const password = document.getElementById('password').value;
      if (registering && !fullName) throw new Error('Full name is required.');
      if (!email) throw new Error('Email is required.');
      if (registering && !/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,72}$/.test(password)) {
        throw new Error(PASSWORD_HELP);
      }
      if (!password) throw new Error('Password is required.');
      const response = await fetch(registering ? '/api/auth/register' : '/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(registering ? {
          fullName,
          email,
          password
        } : {
          email,
          password
        })
      });
      const body = await response.json().catch(() => ({}));
      if (!response.ok) {
        const fieldMessage = body.validationErrors && Object.values(body.validationErrors).filter(Boolean).join(' ');
        throw new Error(fieldMessage || body.message || (registering
          ? 'Unable to create the account. Please check the information and try again.'
          : 'Unable to sign in. Please check your email and password.'));
      }
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
