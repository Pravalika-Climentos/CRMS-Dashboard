(() => {
  'use strict';

  const currentPage = (location.pathname.split('/').pop() || 'index.html').toLowerCase();
  const persistentPages = new Set(['index.html', 'dashboard-data.html', 'calls.html', 'calendar.html', 'email.html', 'chat.html']);
  const embeddedView = new URLSearchParams(location.search).get('crmsEmbedded') === '1';

  function pageFromUrl(url) {
    return (new URL(url, location.href).pathname.split('/').pop() || 'index.html').toLowerCase();
  }

  function cleanViewUrl(value) {
    const url = new URL(value, location.href);
    url.searchParams.delete('crmsEmbedded');
    return `${url.pathname}${url.search}${url.hash}`;
  }

  function installEmbeddedViewBridge() {
    if (!embeddedView) return false;
    document.documentElement.classList.add('crms-embedded-view');
    const style = document.createElement('style');
    style.textContent = `
      .crms-embedded-view .navbar-header,
      .crms-embedded-view .header,
      .crms-embedded-view #sidebar { display: none !important; }
      .crms-embedded-view .page-wrapper { margin: 0 !important; min-height: 100vh !important; }
      .crms-embedded-view .content { padding-top: 18px !important; }
    `;
    document.head.appendChild(style);
    document.addEventListener('click', event => {
      const link = event.target.closest('a[href]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const href = link.getAttribute('href');
      if (!href || href.startsWith('#') || href.startsWith('javascript:') || link.target || link.hasAttribute('download')) return;
      const target = new URL(href, location.href);
      if (target.origin !== location.origin) return;
      event.preventDefault();
      if (persistentPages.has(pageFromUrl(target.href))) window.parent.__crmsNavigate?.(cleanViewUrl(target.href));
      else window.parent.location.assign(cleanViewUrl(target.href));
    }, true);
    return true;
  }

  function installPersistentNavigation() {
    const initialWrapper = document.querySelector('.page-wrapper');
    const main = document.querySelector('.main-wrapper');
    if (!initialWrapper || !main) return;

    const initialPage = currentPage;
    const initialTitle = document.title;
    const views = new Map();
    const host = document.createElement('div');
    host.className = 'page-wrapper crms-view-host';
    host.hidden = true;
    host.style.cssText = 'padding:0;overflow:hidden;background:var(--bs-body-bg,#f5f7fb);';
    main.appendChild(host);

    const setActiveNavigation = page => {
      document.querySelectorAll('#sidebar-menu a[href]').forEach(link => {
        const active = pageFromUrl(link.href) === page;
        link.classList.toggle('active', active);
        link.closest('li')?.classList.toggle('active', active);
      });
    };

    const show = (value, push = true) => {
      const cleanUrl = cleanViewUrl(value);
      const page = pageFromUrl(cleanUrl);
      if (!persistentPages.has(page)) {
        location.assign(cleanUrl);
        return;
      }

      if (push && cleanUrl !== `${location.pathname}${location.search}${location.hash}`) {
        history.pushState({ crmsView: page }, '', cleanUrl);
      }
      initialWrapper.style.display = page === initialPage ? '' : 'none';
      host.hidden = page === initialPage;
      if (page === initialPage) document.title = initialTitle;
      views.forEach((frame, key) => { frame.style.display = key === page ? 'block' : 'none'; });

      if (page !== initialPage && !views.has(page)) {
        const target = new URL(cleanUrl, location.origin);
        target.searchParams.set('crmsEmbedded', '1');
        const frame = document.createElement('iframe');
        frame.className = 'crms-persistent-view';
        frame.title = `${page.replace('.html', '')} view`;
        frame.style.cssText = 'display:block;width:100%;height:calc(100vh - 65px);border:0;background:#fff;';
        frame.src = `${target.pathname}${target.search}${target.hash}`;
        frame.dataset.viewUrl = cleanUrl;
        frame.addEventListener('load', () => {
          const title = frame.contentDocument?.title;
          if (title && pageFromUrl(location.href) === page) document.title = title;
        });
        views.set(page, frame);
        host.appendChild(frame);
      } else if (page !== initialPage) {
        const frame = views.get(page);
        const target = new URL(cleanUrl, location.origin);
        if ((target.search || target.hash) && frame.dataset.viewUrl !== cleanUrl) {
          target.searchParams.set('crmsEmbedded', '1');
          frame.dataset.viewUrl = cleanUrl;
          frame.src = `${target.pathname}${target.search}${target.hash}`;
        }
      }
      setActiveNavigation(page);
      window.scrollTo({ top: 0, behavior: 'auto' });
    };

    window.__crmsNavigate = show;
    window.addEventListener('crms:dashboard-data-change', event => {
      window.dispatchEvent(new CustomEvent('crms:dashboard-refresh'));
      views.forEach(frame => frame.contentWindow?.dispatchEvent(new CustomEvent('crms:dashboard-refresh')));
    });
    document.addEventListener('click', event => {
      const link = event.target.closest('a[href]');
      if (!link || event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const href = link.getAttribute('href');
      if (!href || href.startsWith('#') || href.startsWith('javascript:') || link.target || link.hasAttribute('download')) return;
      const target = new URL(href, location.href);
      if (target.origin !== location.origin || !persistentPages.has(pageFromUrl(target.href))) return;
      event.preventDefault();
      show(target.href);
    }, true);
    window.addEventListener('popstate', () => show(location.href, false));
  }

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
    const displayName = user.fullName || user.name || user.email || 'User';
    const initials = displayName.split(/\s+/).filter(Boolean).slice(0, 2)
      .map(part => part.charAt(0)).join('').toUpperCase() || 'U';
    const name = document.querySelector('.profile-dropdown .dropdown-menu .fw-medium');
    const role = name?.parentElement?.querySelector('.fs-13');
    if (name) name.textContent = displayName;
    if (role) role.textContent = user.designation || String(user.role || '').replaceAll('_', ' ') || 'CRM User';
    document.querySelectorAll('.profile-dropdown img').forEach(image => {
      const badge = document.createElement('span');
      badge.className = `avatar-title rounded-circle bg-primary text-white d-inline-flex align-items-center justify-content-center fw-semibold ${image.classList.contains('rounded-1') ? 'rounded-1' : ''}`;
      badge.style.width = `${image.getAttribute('width') || 42}px`;
      badge.style.height = `${image.getAttribute('height') || image.getAttribute('width') || 42}px`;
      badge.textContent = initials;
      badge.setAttribute('aria-label', displayName);
      image.replaceWith(badge);
    });
  }

  function initialsFor(value) {
    return String(value || 'User').trim().split(/\s+/).filter(Boolean).slice(0, 2)
      .map(part => part.charAt(0)).join('').toUpperCase() || 'U';
  }

  function avatarName(element) {
    const idTargets = {
      personAvatar: 'personName', detailAvatar: 'detailName', callAvatar: 'callName',
      audioAvatar: 'audioName', incomingAvatar: 'incomingName',
      'active-chat-avatar': 'active-chat-name', 'current-user-avatar': 'current-user-name'
    };
    const target = idTargets[element.id];
    if (target) return document.getElementById(target)?.textContent?.trim() || 'User';
    if (element.closest('.profile-dropdown')) {
      const user = window.CrmsAuth?.getCurrentUser?.() || {};
      return user.fullName || user.name || user.email || 'User';
    }
    const container = element.closest('.contact-item,.user-list,.chat-list,.profile-card,.call-person,.incoming-head,.history-item,.participant-item,.d-flex');
    const label = container?.querySelector('[data-user-name],.user-name,.contact-name,h4,h5,h6,strong,.fw-semibold,.fw-medium,.fs-14,a:not(.avatar)');
    const alt = element.getAttribute('alt')?.trim();
    return label?.textContent?.trim() || (alt && !/^(img|image|user|avatar)$/i.test(alt) ? alt : '') || 'User';
  }

  function installInitialAvatars() {
    if (document.getElementById('crms-initial-avatar-style')) return;
    const style = document.createElement('style');
    style.id = 'crms-initial-avatar-style';
    style.textContent = `.crms-initial-avatar{display:inline-flex!important;align-items:center;justify-content:center;flex-shrink:0;background:var(--primary,#e41f07)!important;color:#fff!important;font-weight:700;line-height:1;text-transform:uppercase;object-fit:unset!important;border-radius:50%}.avatar-wrap>.crms-initial-avatar{width:44px!important;height:44px!important;font-size:14px}.chat-avatar>.crms-initial-avatar{width:40px!important;height:40px!important}.profile-avatar.crms-initial-avatar{width:86px!important;height:86px!important;font-size:24px}.audio-avatar.crms-initial-avatar{width:120px!important;height:120px!important;font-size:34px}.mini-avatar.crms-initial-avatar{width:38px!important;height:38px!important}.profile-avatar.crms-initial-avatar,.audio-avatar.crms-initial-avatar{display:flex!important}`;
    document.head.appendChild(style);
    const selector = [
      '.profile-dropdown img', '.avatar-wrap img', '.avatar img:not([src*="/company/"]):not(.img-flag)', 'img.profile-avatar', 'img.mini-avatar',
      'img.audio-avatar', '.chat-avatar img', '#current-user-avatar', '#active-chat-avatar',
      '.conversation-open .avatar img', '#current-participants-list img', '#add-participant-results img',
      '#new-chat-user-results img', 'img[src*="assets/img/profiles/avatar-"]',
      'img[src*="assets/img/users/user-"]'
    ].join(',');
    let scheduled = false;
    const refresh = () => {
      scheduled = false;
      document.querySelectorAll(selector).forEach(image => {
        if (image.closest('.email-detail-body,.message-text,.attachment-preview')) return;
        const badge = document.createElement('span');
        for (const attribute of image.attributes) {
          if (!['src','srcset','alt','loading','decoding'].includes(attribute.name)) badge.setAttribute(attribute.name, attribute.value);
        }
        badge.classList.add('crms-initial-avatar');
        if (!badge.style.width && image.getAttribute('width')) badge.style.width = `${image.getAttribute('width')}px`;
        if (!badge.style.height && image.getAttribute('height')) badge.style.height = `${image.getAttribute('height')}px`;
        badge.textContent = initialsFor(avatarName(image));
        badge.setAttribute('aria-label', avatarName(image));
        image.replaceWith(badge);
      });
      document.querySelectorAll('.crms-initial-avatar').forEach(badge => {
        const value = initialsFor(avatarName(badge));
        if (badge.textContent !== value) badge.textContent = value;
      });
    };
    const schedule = () => {
      if (scheduled) return;
      scheduled = true;
      queueMicrotask(refresh);
    };
    refresh();
    new MutationObserver(schedule).observe(document.body, { childList: true, subtree: true, characterData: true });
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

  function installWorkInProgressLinks(root) {
    root.querySelectorAll('a[href]').forEach(link => {
      const href = link.getAttribute('href');
      if (!href || href.startsWith('#') || href.startsWith('javascript:')) return;
      let target;
      try { target = new URL(href, location.href); } catch (_) { return; }
      if (target.origin !== location.origin || !target.pathname.toLowerCase().endsWith('.html')) return;
      if (persistentPages.has(pageFromUrl(target.href)) || pageFromUrl(target.href) === 'login.html') return;
      link.dataset.crmsWorkInProgress = 'true';
      link.addEventListener('click', event => {
        event.preventDefault();
        event.stopImmediatePropagation();
        const label = link.querySelector('span')?.textContent?.trim() || link.textContent.trim() || 'This page';
        document.getElementById('crmsWorkInProgressModal')?.remove();
        const shell = document.createElement('div');
        shell.innerHTML = `<div class="modal fade" id="crmsWorkInProgressModal" tabindex="-1" aria-labelledby="crmsWorkInProgressTitle" aria-hidden="true"><div class="modal-dialog modal-dialog-centered"><div class="modal-content"><div class="modal-body text-center p-5"><span class="avatar avatar-xl rounded-circle bg-light text-primary d-inline-flex align-items-center justify-content-center mb-3"><i class="ti ti-tools fs-32"></i></span><h4 id="crmsWorkInProgressTitle">Work Under Progress</h4><p class="text-muted mb-4">${escapeNotificationText(label)} is currently being developed and will be available soon.</p><button type="button" class="btn btn-primary px-4" data-bs-dismiss="modal">Okay</button></div></div></div></div>`;
        const element = shell.firstElementChild;
        document.body.appendChild(element);
        const modal = new bootstrap.Modal(element);
        element.addEventListener('hidden.bs.modal', () => element.remove(), { once: true });
        modal.show();
      }, true);
    });
  }

  function enhanceCallsPage() {
    if (currentPage !== 'calls.html') return;
    document.body.classList.add('calls-page');
    document.querySelector('.comm-view-switcher')?.remove();
    document.querySelector('.comm-page > .alert')?.remove();

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

  function notificationPreferenceKey() {
    const user = window.CrmsAuth?.getCurrentUser?.() || {};
    return `crms.notifications.enabled.${user.userId || user.id || user.email || 'anonymous'}`;
  }

  function notificationsEnabled() {
    return localStorage.getItem(notificationPreferenceKey()) !== 'false';
  }

  function clearNotificationIndicators() {
    document.querySelectorAll('.navbar-header .badge').forEach(badge => {
      if (badge.closest('button') || badge.closest('a[href="chat.html"]')) setBadge(badge, 0);
    });
  }

  function installNotificationPreference() {
    const control = document.getElementById('notify');
    if (!control) return;
    control.checked = notificationsEnabled();
    control.addEventListener('change', () => {
      localStorage.setItem(notificationPreferenceKey(), String(control.checked));
      if (!control.checked) clearNotificationIndicators();
      window.dispatchEvent(new CustomEvent('crms:notification-preference-change', {
        detail: { enabled: control.checked }
      }));
    });
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

  function escapeNotificationText(value) {
    return String(value ?? '').replace(/[&<>'"]/g, character => ({
      '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;'
    })[character]);
  }

  function localNotificationTime(value) {
    if (!value) return '';
    const text = String(value);
    const normalized = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)
      && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(text) ? `${text}Z` : text;
    const date = new Date(normalized);
    return Number.isNaN(date.getTime()) ? '' : date.toLocaleString([], {
      month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit'
    });
  }

  async function refreshBellNotifications() {
    const bellButton = document.querySelector('.navbar-header button .ti-bell-check')?.closest('button');
    const menu = bellButton?.parentElement?.querySelector('.dropdown-menu');
    const body = menu?.querySelector('.notification-body');
    if (!bellButton || !body) return;

    body.innerHTML = '<div class="p-4 text-center text-muted">Loading notifications…</div>';
    try {
      const [invitationResponse, timeChangeResponse] = await Promise.all([
        fetch('/api/calendar/invitations?status=PENDING&page=0&size=5', { cache: 'no-store' }),
        fetch('/api/calendar/time-change-requests?scope=received&status=PENDING&page=0&size=5', { cache: 'no-store' })
      ]);
      if (!invitationResponse.ok || !timeChangeResponse.ok) {
        throw new Error('Notification data could not be loaded.');
      }
      const invitationsPage = await invitationResponse.json();
      const timeChangesPage = await timeChangeResponse.json();
      const invitations = invitationsPage.items || [];
      const timeChanges = timeChangesPage.items || [];
      const rows = [
        ...invitations.map(item => ({
          icon: 'ti-calendar-event',
          title: item.title || 'Meeting invitation',
          detail: `Invitation from ${item.invitedBy?.fullName || item.organizer?.fullName || 'an organizer'}`,
          time: item.startAt || item.startDate
        })),
        ...timeChanges.map(item => ({
          icon: 'ti-clock-edit',
          title: item.eventTitle || 'Meeting time-change request',
          detail: `${item.requester?.fullName || 'A participant'} requested a different time`,
          time: item.proposedStartAt || item.proposedStartDate
        }))
      ];
      body.innerHTML = rows.length ? rows.map((item, index) => `
        <a class="dropdown-item notification-item py-3 text-wrap border-bottom" href="calendar.html" id="live-notification-${index}">
          <div class="d-flex gap-2">
            <span class="avatar avatar-md rounded-circle bg-light text-primary d-inline-flex align-items-center justify-content-center flex-shrink-0"><i class="ti ${item.icon} fs-20"></i></span>
            <span class="flex-grow-1 min-width-0">
              <strong class="d-block text-dark text-truncate">${escapeNotificationText(item.title)}</strong>
              <span class="d-block text-muted text-wrap">${escapeNotificationText(item.detail)}</span>
              <small class="text-muted"><i class="ti ti-clock me-1"></i>${escapeNotificationText(localNotificationTime(item.time))}</small>
            </span>
          </div>
        </a>`).join('') : '<div class="p-4 text-center text-muted">No new notifications.</div>';
      const total = Number(invitationsPage.totalElements || invitations.length)
        + Number(timeChangesPage.totalElements || timeChanges.length);
      setBadge(notificationBadge(bellButton), total);
      const footerLink = menu.querySelector('.border-top a');
      if (footerLink) {
        footerLink.href = 'calendar.html';
        footerLink.textContent = 'Open Calendar';
      }
    } catch (error) {
      body.innerHTML = '<div class="p-4 text-center text-muted">Notifications are temporarily unavailable.</div>';
      console.warn('Header notifications could not be loaded.', error);
    }
  }

  function installNotifications() {
    const refresh = () => {
      if (!notificationsEnabled()) {
        clearNotificationIndicators();
        return;
      }
      refreshChatNotification();
      refreshCalendarNotifications();
      refreshBellNotifications();
    };
    refresh();
    const timer = window.setInterval(refresh, 15000);
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden) refresh();
    });
    window.addEventListener('focus', refresh);
    window.addEventListener('crms:chat-notification-change', refreshChatNotification);
    window.addEventListener('crms:notification-preference-change', refresh);
    window.addEventListener('beforeunload', () => window.clearInterval(timer), { once: true });
  }

  document.addEventListener('DOMContentLoaded', async () => {
    if (installEmbeddedViewBridge()) return;
    await installSharedShell();
    const sidebar = document.getElementById('sidebar');
    const menu = document.getElementById('sidebar-menu');
    if (!sidebar || !menu) return;
    normalizeCallLinks(menu);
    markCurrent(menu);
    populateHeaderUser();
    installInitialAvatars();
    installNotificationPreference();
    installMobileNavigation(sidebar);
    if (currentPage !== 'index.html') installSubmenus(menu);
    installWorkInProgressLinks(document);
    enhanceCallsPage();
    installPersistentNavigation();
    installNotifications();
    document.querySelectorAll('[data-crms-logout], #signOutLink, .profile-dropdown a[href="login.html"]').forEach(link => {
      link.addEventListener('click', event => {
        event.preventDefault();
        window.CrmsAuth?.logout();
      });
    });
  });
})();
