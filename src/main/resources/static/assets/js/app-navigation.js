(() => {
  'use strict';

  const currentPage = (location.pathname.split('/').pop() || 'index.html').toLowerCase();

  async function installSharedShell() {
    if (currentPage === 'index.html') return;
    try {
      const response = await fetch('index.html', { cache: 'no-store' });
      if (!response.ok) throw new Error(`HTTP_${response.status}`);
      const source = new DOMParser().parseFromString(await response.text(), 'text/html');
      const sharedHeader = source.querySelector('.navbar-header');
      const sharedSidebar = source.getElementById('sidebar');
      const currentHeader = document.querySelector('.navbar-header, .header');
      const currentSidebar = document.getElementById('sidebar');
      if (sharedHeader) {
        const header = document.importNode(sharedHeader, true);
        header.style.removeProperty('display');
        if (currentHeader) currentHeader.replaceWith(header);
        else document.querySelector('.main-wrapper')?.prepend(header);
      }
      if (sharedSidebar) {
        const sidebar = document.importNode(sharedSidebar, true);
        if (currentSidebar) currentSidebar.replaceWith(sidebar);
        else document.querySelector('.main-wrapper')?.prepend(sidebar);
      }
    } catch (error) {
      console.warn('Shared CRM navigation could not be loaded.', error);
    }
  }

  function normalizeCallLinks(root) {
    root.querySelectorAll('a[href="video-call.html"],a[href="audio-call.html"],a[href="call-history.html"]')
      .forEach(link => { link.href = 'calls.html'; });
  }

  function markCurrent(menu) {
    menu.querySelectorAll('a[href]').forEach(link => {
      const target = link.getAttribute('href')?.split('?')[0].toLowerCase();
      if (target === currentPage) {
        link.classList.add('active');
        link.closest('li')?.classList.add('active');
        const parentSubmenu = link.closest('.submenu');
        parentSubmenu?.querySelector(':scope > a')?.classList.add('active', 'subdrop');
        const childList = link.parentElement?.parentElement;
        if (childList && parentSubmenu) childList.style.display = 'block';
      }
    });
  }

  function populateHeaderUser() {
    const user = window.CrmsAuth?.getCurrentUser?.();
    if (!user) return;
    const name = document.querySelector('.profile-dropdown .dropdown-menu .fw-medium');
    const role = name?.parentElement?.querySelector('.fs-13');
    if (name) name.textContent = user.fullName || user.email || 'User';
    if (role) role.textContent = user.designation || String(user.role || '').replaceAll('_', ' ') || 'CRM User';
    if (user.avatar) {
      document.querySelectorAll('.profile-dropdown img').forEach(image => { image.src = user.avatar; });
    }
  }

  function installMobileNavigation(sidebar) {
    const mobileButton = document.getElementById('mobile_btn');
    if (!mobileButton) return;
    mobileButton.addEventListener('click', event => {
      event.preventDefault();
      sidebar.classList.toggle('slide-nav');
      document.body.classList.toggle('menu-opened');
    });
    sidebar.querySelector('.sidebar-close')?.addEventListener('click', () => {
      sidebar.classList.remove('slide-nav');
      document.body.classList.remove('menu-opened');
    });
  }

  function installSubmenus(menu) {
    menu.querySelectorAll('.submenu > a').forEach(toggle => {
      toggle.addEventListener('click', event => {
        const child = toggle.parentElement?.querySelector(':scope > ul');
        if (!child || toggle.getAttribute('href') !== 'javascript:void(0);') return;
        event.preventDefault();
        const opening = child.style.display !== 'block';
        child.style.display = opening ? 'block' : 'none';
        toggle.classList.toggle('subdrop', opening);
      });
    });
  }

  function enhanceCallsPage() {
    if (currentPage !== 'calls.html') return;
    document.body.classList.add('calls-page');
    document.querySelector('.comm-view-switcher')?.remove();

    const params = new URLSearchParams(location.search);
    const mode = params.get('mode');
    if (mode === 'audio' || mode === 'video') {
      document.getElementById(mode === 'audio' ? 'modeAudio' : 'modeVideo')?.classList.add('selected');
      document.getElementById('modeChat')?.classList.remove('selected');
    }

    const panelHead = document.querySelector('.comm-panel-head');
    if (panelHead && !document.getElementById('mobileContactsClose')) {
      const close = document.createElement('button');
      close.id = 'mobileContactsClose';
      close.className = 'icon-btn mobile-contacts-close';
      close.setAttribute('aria-label', 'Close contacts');
      close.innerHTML = '<i class="ti ti-x"></i>';
      close.addEventListener('click', () => document.querySelector('.comm-sidebar')?.classList.remove('mobile-show'));
      panelHead.appendChild(close);
    }

    const actions = document.querySelector('.comm-actions');
    if (actions && !document.getElementById('mobileContactsBtn')) {
      const button = document.createElement('button');
      button.id = 'mobileContactsBtn';
      button.className = 'btn btn-outline-primary mobile-contacts-button';
      button.innerHTML = '<i class="ti ti-users me-1"></i>Contacts';
      button.addEventListener('click', () => document.querySelector('.comm-sidebar')?.classList.add('mobile-show'));
      actions.prepend(button);
    }
  }

  document.addEventListener('DOMContentLoaded', async () => {
    await installSharedShell();
    const sidebar = document.getElementById('sidebar');
    const menu = document.getElementById('sidebar-menu');
    if (!sidebar || !menu) return;
    normalizeCallLinks(menu);
    markCurrent(menu);
    populateHeaderUser();
    installMobileNavigation(sidebar);
    if (currentPage !== 'index.html') installSubmenus(menu);
    enhanceCallsPage();
    document.querySelectorAll('[data-crms-logout], #signOutLink, .profile-dropdown a[href="login.html"]').forEach(link => {
      link.addEventListener('click', event => {
        event.preventDefault();
        window.CrmsAuth?.logout();
      });
    });
  });
})();
