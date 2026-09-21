(() => {
  'use strict';

  const currentPage = (location.pathname.split('/').pop() || 'index.html').toLowerCase();

  function preservePageHeaderActions(currentHeader, sharedHeader) {
    if (!currentHeader || !sharedHeader || currentPage !== 'calendar.html') return;
    const destination = sharedHeader.querySelector('.page-container.topbar-menu > .d-flex.align-items-center:last-child');
    const separator = destination?.querySelector('.header-line');
    if (!destination) return;

    currentHeader.querySelectorAll(
      'button[data-bs-target="#invitations_modal"], button[data-bs-target="#tcr_modal"]'
    ).forEach(button => {
      const item = button.closest('.header-item');
      if (!item) return;
      if (separator) destination.insertBefore(item, separator);
      else destination.prepend(item);
    });
  }

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
        preservePageHeaderActions(currentHeader, header);
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

  function notificationBadge(link) {
    const badge = link?.querySelector('.badge');
    if (!badge) return null;
    badge.classList.add('bg-danger');
    badge.style.display = 'none';
    badge.textContent = '';
    return badge;
  }

  function setBadge(badge, count) {
    if (!badge) return;
    const value = Math.max(0, Number(count) || 0);
    badge.textContent = value > 99 ? '99+' : String(value);
    badge.style.display = value > 0 ? '' : 'none';
  }

  async function refreshChatNotification() {
    const links = Array.from(document.querySelectorAll('a[href="chat.html"]'))
      .filter(link => link.querySelector('.ti-message-circle-exclamation'));
    if (!links.length) return;
    try {
      const response = await fetch('/api/chat/conversations', { cache: 'no-store' });
      if (!response.ok) throw new Error(`Chat notifications failed (${response.status}).`);
      const conversations = await response.json();
      const unread = (Array.isArray(conversations) ? conversations : conversations.items || [])
        .reduce((total, conversation) => total + Math.max(0, Number(conversation.unreadCount) || 0), 0);
      links.forEach(link => {
        setBadge(notificationBadge(link), unread);
        link.title = unread ? `${unread} unread chat message${unread === 1 ? '' : 's'}` : 'No unread chat messages';
        link.setAttribute('aria-label', link.title);
      });
    } catch (error) {
      console.warn('Chat notification count could not be loaded.', error);
    }
  }

  async function refreshCalendarNotifications() {
    const invitationBadge = document.getElementById('invitationBadge');
    const timeChangeBadge = document.getElementById('tcrBadge');
    if (!invitationBadge && !timeChangeBadge) return;
    const requests = [];
    if (invitationBadge) {
      requests.push(fetch('/api/calendar/invitations/summary', { cache: 'no-store' })
        .then(response => response.ok ? response.json() : Promise.reject(new Error(`Invitations failed (${response.status}).`)))
        .then(summary => setBadge(invitationBadge, summary.pending)));
    }
    if (timeChangeBadge) {
      requests.push(fetch('/api/calendar/time-change-requests?scope=received&status=PENDING&page=0&size=1', { cache: 'no-store' })
        .then(response => response.ok ? response.json() : Promise.reject(new Error(`Time-change notifications failed (${response.status}).`)))
        .then(page => setBadge(timeChangeBadge, page.totalElements)));
    }
    await Promise.allSettled(requests);
  }

  function installNotifications() {
    const refresh = () => {
      refreshChatNotification();
      refreshCalendarNotifications();
    };
    refresh();
    const timer = window.setInterval(refresh, 15000);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) refresh();
    });
    window.addEventListener('focus', refresh);
    window.addEventListener('crms:chat-notification-change', refreshChatNotification);
    window.addEventListener('beforeunload', () => window.clearInterval(timer), { once: true });
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
    installNotifications();
    document.querySelectorAll('[data-crms-logout], #signOutLink, .profile-dropdown a[href="login.html"]').forEach(link => {
      link.addEventListener('click', event => {
        event.preventDefault();
        window.CrmsAuth?.logout();
      });
    });
  });
})();
