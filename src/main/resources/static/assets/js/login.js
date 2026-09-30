(() => {
  const form = document.getElementById('loginForm');
  const message = document.getElementById('authMessage');
  const button = document.getElementById('loginBtn');
  const modeButton = document.getElementById('authModeBtn');
  const loginForm = document.getElementById('loginForm');
  const forgotForm = document.getElementById('forgotPasswordForm');
  const forgotButton = document.getElementById('forgotPasswordBtn');
  let registering = false;
  let resetChallengeToken = '';
  let resetTimer = null;
  const PASSWORD_HELP = 'Password must be 8–72 characters and include at least one letter, one number, and one special character. Spaces are not allowed.';
  document.getElementById('year').textContent = new Date().getFullYear();

  function togglePassword(inputId, buttonId) {
    const input = document.getElementById(inputId);
    const toggle = document.getElementById(buttonId);
    const reveal = input.type === 'password';
    input.type = reveal ? 'text' : 'password';
    toggle.querySelector('i').className = `ti ${reveal ? 'ti-eye' : 'ti-eye-off'}`;
    toggle.setAttribute('aria-label', reveal ? 'Hide password' : 'Show password');
  }
  document.getElementById('togglePassword').addEventListener('click', () => togglePassword('password', 'togglePassword'));
  document.getElementById('toggleNewPassword').addEventListener('click', () => togglePassword('newPassword', 'toggleNewPassword'));
  document.getElementById('toggleConfirmPassword').addEventListener('click', () => togglePassword('confirmNewPassword', 'toggleConfirmPassword'));

  function showMessage(text, type = 'error') {
    message.textContent = text || '';
    message.className = `auth-message${text ? ' show' : ''}${type !== 'error' ? ` ${type}` : ''}`;
  }

  async function api(path, options) {
    const response = await fetch(path, options);
    const body = await response.json().catch(() => ({}));
    if (!response.ok) {
      const validation = body.validationErrors && Object.values(body.validationErrors).filter(Boolean).join(' ');
      throw new Error(validation || body.message || 'Unable to complete the request. Please try again.');
    }
    return body;
  }

  function showForgotPassword() {
    registering = false;
    loginForm.hidden = true;
    document.getElementById('authSwitch').hidden = true;
    forgotForm.hidden = false;
    document.getElementById('authTitle').textContent = 'Reset password';
    document.getElementById('authSubtitle').textContent = 'We will send a verification code to your registered email.';
    document.getElementById('resetEmail').value = document.getElementById('email').value.trim();
    showMessage('');
    document.getElementById('resetEmail').focus();
  }

  function showSignIn(successMessage = '') {
    clearInterval(resetTimer);
    resetChallengeToken = '';
    forgotForm.reset();
    document.getElementById('resetFields').hidden = true;
    forgotForm.hidden = true;
    loginForm.hidden = false;
    document.getElementById('authSwitch').hidden = false;
    forgotButton.hidden = false;
    document.getElementById('nameGroup').hidden = true;
    document.getElementById('fullName').required = false;
    document.getElementById('authTitle').textContent = 'Sign in';
    document.getElementById('authSubtitle').textContent = 'Enter your account details to continue.';
    document.getElementById('authSwitchText').textContent = "Don't have an account?";
    modeButton.textContent = 'Create account';
    button.textContent = 'Sign in';
    registering = false;
    showMessage(successMessage, successMessage ? 'success' : 'error');
  }

  function startResetTimer(seconds) {
    clearInterval(resetTimer);
    let remaining = Number(seconds) || 600;
    const timer = document.getElementById('resetTimer');
    const resend = document.getElementById('resendResetCode');
    resend.disabled = true;
    const tick = () => {
      const minutes = Math.floor(remaining / 60);
      const secs = String(remaining % 60).padStart(2, '0');
      timer.textContent = remaining > 0 ? `Code expires in ${minutes}:${secs}` : 'Code expired';
      if (remaining <= 0) {
        resend.disabled = false;
        clearInterval(resetTimer);
        return;
      }
      remaining -= 1;
    };
    tick();
    resetTimer = setInterval(tick, 1000);
    setTimeout(() => { resend.disabled = false; }, 60000);
  }

  async function requestResetCode() {
    const email = document.getElementById('resetEmail');
    if (!email.reportValidity()) return;
    const send = document.getElementById('sendResetCode');
    send.disabled = true;
    try {
      const result = await api('/api/auth/forgot-password/request', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email: email.value.trim() })
      });
      resetChallengeToken = result.challengeToken;
      document.getElementById('resetFields').hidden = false;
      document.getElementById('resetCode').focus();
      startResetTimer(result.expiresInSeconds);
      showMessage(result.message, 'info');
    } catch (error) {
      showMessage(error.message);
    } finally {
      send.disabled = false;
    }
  }

  forgotButton.addEventListener('click', showForgotPassword);
  document.getElementById('backToLogin').addEventListener('click', () => showSignIn());
  document.getElementById('sendResetCode').addEventListener('click', requestResetCode);
  document.getElementById('resendResetCode').addEventListener('click', requestResetCode);

  forgotForm.addEventListener('submit', async event => {
    event.preventDefault();
    const code = document.getElementById('resetCode').value.trim();
    const newPassword = document.getElementById('newPassword').value;
    const confirmation = document.getElementById('confirmNewPassword').value;
    if (!/^\d{6}$/.test(code)) return showMessage('Enter the 6-digit verification code.');
    if (!/^(?=.*[A-Za-z])(?=.*\d)(?=.*[^A-Za-z0-9\s])\S{8,72}$/.test(newPassword)) return showMessage(PASSWORD_HELP);
    if (newPassword !== confirmation) return showMessage('New password and confirmation do not match.');
    const reset = document.getElementById('resetPasswordBtn');
    reset.disabled = true;
    try {
      const result = await api('/api/auth/forgot-password/reset', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ challengeToken: resetChallengeToken, code, newPassword })
      });
      document.getElementById('email').value = document.getElementById('resetEmail').value.trim();
      showSignIn(result.message);
    } catch (error) {
      showMessage(error.message);
    } finally {
      reset.disabled = false;
    }
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
    forgotButton.hidden = registering;
    showMessage('');
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
