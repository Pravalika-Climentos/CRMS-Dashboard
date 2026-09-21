/*
 * Chat frontend integration for the Spring Boot Chat API.
 * Temporary current user: userId 1 (replace when authentication is added).
 */
(function () {
    'use strict';

    const CURRENT_USER_ID = Number(
        window.CrmsAuth?.getCurrentUser()?.userId
    );

    if (!Number.isInteger(CURRENT_USER_ID) || CURRENT_USER_ID <= 0) {
        window.CrmsAuth?.goToLogin();
        return;
    }
    const API = '/api/chat';
    const DEFAULT_AVATAR = 'assets/img/profiles/avatar-01.jpg';
    const state = {
        conversations: [],
        activeConversationId: null,
        messages: [],
        replyToMessageId: null,
        selectedFile: null,
        selectedUserIds: new Set(),
        selectedAddParticipantIds: new Set(),
        userSearchTimer: null,
        participantSearchTimer: null,
        requestSequence: 0,
        stompClient: null,
        socketConnected: false,
        conversationSubscriptions: new Map()
    };

    const $ = (selector, root = document) => root.querySelector(selector);
    const $$ = (selector, root = document) => Array.from(root.querySelectorAll(selector));

    document.addEventListener('DOMContentLoaded', init);

    async function init() {
        installChatUiEnhancements();
        bindEvents();
        initializeTemplatePlugins();
        await loadConversations();
        connectChatSocket();
        window.addEventListener('beforeunload', disconnectChatSocket, { once: true });
    }

    function bindEvents() {
        $('#conversation-search')?.addEventListener('input', renderConversations);
        $('#send-message-btn')?.addEventListener('click', sendMessage);
        $('#message-input')?.addEventListener('keydown', event => {
            if (event.key === 'Enter' && !event.shiftKey) {
                event.preventDefault();
                sendMessage();
            }
        });
        $('#attachment-btn')?.addEventListener('click', () => $('#message-file-input')?.click());
        $('#message-file-input')?.addEventListener('change', onFileSelected);
        $('#new-chat-btn')?.addEventListener('click', openNewChat);
        $('#user-search, #new-chat-user-search')?.addEventListener('input', scheduleUserSearch);
        $('#create-conversation-btn')?.addEventListener('click', createConversation);
        $('#conversation-list')?.addEventListener('click', onConversationAction);
        $('#message-list')?.addEventListener('click', onMessageAction);
        $('#user-search-results, #new-chat-user-results')?.addEventListener('change', onUserSelection);
        $('#cancel-reply-btn')?.addEventListener('click', clearReply);
        $('#clear-attachment-btn')?.addEventListener('click', clearSelectedFile);

        // Group participant management
        $('#manage-participants-btn')?.addEventListener('click', openManageParticipants);
        $('#add-participant-search')?.addEventListener('input', scheduleParticipantSearch);
        $('#add-participant-results')?.addEventListener('change', onAddParticipantSelection);
        $('#add-participants-btn')?.addEventListener('click', addParticipants);
        $('#current-participants-list')?.addEventListener('click', onRemoveParticipantClick);
    }

    async function request(path, options = {}) {
        const config = {
            method: 'GET',
            ...options,
            headers: {
                Accept: 'application/json',
                ...(options.headers || {})
            }
        };        
        if (config.body && !(config.body instanceof FormData) && typeof config.body !== 'string') {
            config.headers['Content-Type'] = 'application/json';
            config.body = JSON.stringify(config.body);
        }

        const response = await fetch(`${API}${path}`, config);
        if (!response.ok) {
            let message = `Request failed (${response.status})`;
            try {
                const error = await response.json();
                message = error.message || error.error || error.detail || message;
            } catch (_) {
                const text = await response.text();
                if (text) message = text;
            }
            throw new Error(message);
        }
        if (response.status === 204) return null;
        const contentType = response.headers.get('content-type') || '';
        return contentType.includes('application/json') ? response.json() : response.text();
    }

    async function loadConversations(preferredId) {
        setConversationLoading(true);
        try {
            const data = await request('/conversations');
            state.conversations = asArray(data);
            renderConversations();
            syncConversationSubscriptions();
            const wanted = Number(preferredId || state.activeConversationId);
            const next = state.conversations.find(c => conversationId(c) === wanted)
                || state.conversations.find(c => !c.archived)
                || state.conversations[0];
            if (next) await selectConversation(conversationId(next));
            else showNoConversation();
        } catch (error) {
            showError(error, 'Unable to load conversations.');
            renderConversationNotice('Unable to load conversations.');
        } finally {
            setConversationLoading(false);
        }
    }

    function renderConversations() {
        const list = $('#conversation-list');
        if (!list) return;
        const query = ($('#conversation-search')?.value || '').trim().toLowerCase();
        const conversations = state.conversations.filter(c => {
            const label = conversationLabel(c).toLowerCase();
            const preview = c.lastMessage?.content?.toLowerCase() || '';
            return !query || label.includes(query) || preview.includes(query);
        });

        if (!conversations.length) {
            renderConversationNotice(query ? 'No matching conversations.' : 'No conversations yet.');
            return;
        }

        const row = c => {
            const id = conversationId(c);
            const person = otherParticipant(c);
            const active = id === state.activeConversationId ? 'active' : '';
            const preview = c.lastMessage?.content || 'No messages yet';
            const time = c.lastMessage?.createdAt ? formatTime(c.lastMessage.createdAt) : '';
            const unread = Number(c.unreadCount || 0);
            return `
                <div class="user-list d-flex align-items-center gap-2 rounded-2 ${active}" data-conversation-id="${id}">
                    <a href="javascript:void(0);" class="d-flex align-items-center flex-grow-1 overflow-hidden conversation-open">
                        <span class="avatar avatar-lg me-2 flex-shrink-0">
                            <img src="${escapeAttr(avatarUrl(person?.avatar))}" class="rounded-circle" alt="">
                        </span>
                        <span class="flex-grow-1 overflow-hidden">
                            <span class="d-flex align-items-center justify-content-between mb-1">
                                <span class="fs-14 fw-semibold text-truncate">${escapeHtml(conversationLabel(c))}</span>
                                <small class="text-muted">${escapeHtml(time)}</small>
                            </span>
                            <span class="d-flex align-items-center justify-content-between">
                                <span class="text-truncate text-muted">${escapeHtml(preview)}</span>
                                <span class="d-flex align-items-center gap-1 ms-2">
                                    ${c.pinned ? '<i class="ti ti-pin-filled text-primary" title="Pinned"></i>' : ''}
                                    ${c.archived ? '<i class="ti ti-archive text-muted" title="Archived"></i>' : ''}
                                    ${unread ? `<span class="badge badge-sm rounded-pill bg-primary">${unread}</span>` : ''}
                                </span>
                            </span>
                        </span>
                    </a>
                    <div class="dropdown flex-shrink-0">
                        <a href="javascript:void(0);" data-bs-toggle="dropdown"><i class="ti ti-dots-vertical"></i></a>
                        <ul class="dropdown-menu dropdown-menu-end">
                            <li><button class="dropdown-item conversation-pin" type="button">${c.pinned ? 'Unpin' : 'Pin'} chat</button></li>
                            <li><button class="dropdown-item conversation-unread" type="button">Mark as unread</button></li>
                            <li><button class="dropdown-item conversation-archive" type="button">${c.archived ? 'Unarchive' : 'Archive'} chat</button></li>
                        </ul>
                    </div>
                </div>`;
        };
        const pinned = conversations.filter(c => c.pinned && !c.archived);
        const regular = conversations.filter(c => !c.pinned && !c.archived);
        const archived = conversations.filter(c => c.archived);
        list.innerHTML = [
            pinned.length ? `<div class="chat-section-label">Pinned</div>${pinned.map(row).join('')}` : '',
            regular.length ? `<div class="chat-section-label">Messages</div>${regular.map(row).join('')}` : '',
            archived.length ? `<details class="chat-archived mt-2"><summary class="chat-section-label">Archived (${archived.length})</summary>${archived.map(row).join('')}</details>` : ''
        ].join('');
        populateCurrentUser();
        initializeTemplatePlugins(list);
    }

    async function onConversationAction(event) {
        const row = event.target.closest('[data-conversation-id]');
        if (!row) return;
        const id = Number(row.dataset.conversationId);
        if (event.target.closest('.conversation-pin')) return toggleConversation(id, 'pin');
        if (event.target.closest('.conversation-unread')) return markConversationUnread(id);
        if (event.target.closest('.conversation-archive')) return toggleConversation(id, 'archive');
        if (event.target.closest('.conversation-open')) await selectConversation(id);
    }

    async function selectConversation(id) {
        if (!id) return;
        state.activeConversationId = Number(id);
        clearReply();
        renderConversations();
        renderActiveHeader();
        setComposerEnabled(true);
        await Promise.all([loadMessages(id), loadActiveUserStatus()]);
    }

    function renderActiveHeader() {
        const conversation = activeConversation();
        if (!conversation) return;
        const participant = otherParticipant(conversation);
        const isGroup = conversation.conversationType === 'GROUP';
        setText('#active-chat-name', conversationLabel(conversation));
        const avatar = $('#active-chat-avatar');
        if (avatar) avatar.src = avatarUrl(participant?.avatar);
        setText('#active-chat-status', isGroup ? `${(conversation.participants || []).length} participants` : '');

        const groupDropdown = $('#group-manage-dropdown');
        if (groupDropdown) groupDropdown.style.display = isGroup ? '' : 'none';
    }

    async function loadActiveUserStatus() {
        const conversation = activeConversation();
        if (!conversation || conversation.conversationType === 'GROUP') return;
        const participant = otherParticipant(conversation);
        if (!participant?.userId) return;
        try {
            const status = await request(`/users/${participant.userId}/status`);
            setText('#active-chat-status', status.active ? 'Active' : 'Inactive');
            $('#active-chat-status')?.classList.toggle('text-success', Boolean(status.active));
        } catch (_) {
            setText('#active-chat-status', '');
        }
    }

    async function loadMessages(conversationIdValue) {
        const sequence = ++state.requestSequence;
        renderMessageNotice('Loading messages…');
        try {
            const data = await request(`/conversations/${conversationIdValue}/messages?page=0&size=50`);
            if (sequence !== state.requestSequence || Number(conversationIdValue) !== state.activeConversationId) return;
            state.messages = asArray(data).sort((a, b) => parseApiDate(a.createdAt) - parseApiDate(b.createdAt));
            renderMessages();
            const last = state.messages[state.messages.length - 1];
            if (last?.messageId) markRead(last.messageId, false);
        } catch (error) {
            showError(error, 'Unable to load messages.');
            renderMessageNotice('Unable to load messages.');
        }
    }

    function renderMessages() {
        const list = messageRenderHost();
        if (!list) return;
        if (!state.messages.length) {
            renderMessageNotice('No messages yet. Start the conversation.');
            return;
        }
        let previousDay = '';
        list.innerHTML = state.messages.map(message => {
            const day = formatDay(message.createdAt);
            const separator = day !== previousDay ? `<div class="chat-date-separator"><span>${escapeHtml(day)}</span></div>` : '';
            previousDay = day;
            return separator + renderMessage(message);
        }).join('');
        initializeTemplatePlugins(list);
        scrollMessagesToBottom();
    }

    function renderMessage(message) {
        const mine = Number(message.sender?.userId) === CURRENT_USER_ID;
        const deleted = Boolean(message.deleted);
        const body = deleted ? '<em class="text-muted">This message was deleted</em>' : linkify(message.content || '');
        const attachments = deleted ? '' : renderAttachments(message.attachments || []);
        const replyTarget = message.replyToMessageId
            ? state.messages.find(item => Number(item.messageId) === Number(message.replyToMessageId))
            : null;
        const reply = message.replyToMessageId ? `
            <div class="chat-reply-quote">
                <span class="chat-reply-author">${escapeHtml(replyTarget?.sender?.fullName || 'Original message')}</span>
                <span class="chat-reply-text">${escapeHtml(replyTarget?.content || `Message #${message.replyToMessageId}`)}</span>
            </div>` : '';
        const forwarded = message.forwardedFromMessageId ? '<div class="small text-muted mb-1"><i class="ti ti-forward me-1"></i>Forwarded</div>' : '';
        return `
            <div class="chat-list d-flex gap-2 ${mine ? 'justify-content-end ms-auto' : ''}" data-message-id="${message.messageId}">
                ${!mine ? `<div class="chat-avatar flex-shrink-0"><img src="${escapeAttr(avatarUrl(message.sender?.avatar))}" class="avatar avatar-md rounded-circle" alt=""></div>` : ''}
                <div class="chat-content">
                    ${!mine ? `<div class="small fw-semibold mb-1">${escapeHtml(message.sender?.fullName || 'User')}</div>` : ''}
                    <div class="message-content ${mine ? 'sent-message' : 'receive-message'}">
                        ${forwarded}${reply}<div class="message-text">${body}</div>${attachments}
                    </div>
                    <div class="chat-time d-flex align-items-center gap-1 ${mine ? 'justify-content-end' : ''}">
                        <small>${escapeHtml(formatMessageTime(message.createdAt))}${message.updatedAt && message.updatedAt !== message.createdAt ? ' · edited' : ''}</small>
                        ${message.favorite ? '<i class="ti ti-star-filled text-warning"></i>' : ''}
                    </div>
                </div>
                ${!deleted ? renderMessageMenu(message, mine) : ''}
            </div>`;
    }

    function renderMessageMenu(message, mine) {
        return `<div class="chat-actions dropdown">
            <a href="javascript:void(0);" data-bs-toggle="dropdown"><i class="ti ti-dots-vertical"></i></a>
            <ul class="dropdown-menu dropdown-menu-end">
                <li><button type="button" class="dropdown-item message-reply"><i class="ti ti-corner-up-left me-2"></i>Reply</button></li>
                <li><button type="button" class="dropdown-item message-forward"><i class="ti ti-forward me-2"></i>Forward</button></li>
                <li><button type="button" class="dropdown-item message-copy"><i class="ti ti-copy me-2"></i>Copy</button></li>
                <li><button type="button" class="dropdown-item message-favorite"><i class="ti ti-star me-2"></i>${message.favorite ? 'Remove favorite' : 'Favorite'}</button></li>
                ${mine ? '<li><button type="button" class="dropdown-item message-edit"><i class="ti ti-edit me-2"></i>Edit</button></li>' : ''}
                ${mine ? '<li><button type="button" class="dropdown-item text-danger message-delete"><i class="ti ti-trash me-2"></i>Delete</button></li>' : ''}
            </ul>
        </div>`;
    }

    async function onMessageAction(event) {
        const row = event.target.closest('[data-message-id]');
        if (!row) return;
        const message = state.messages.find(m => Number(m.messageId) === Number(row.dataset.messageId));
        if (!message) return;
        if (event.target.closest('.message-reply')) return setReply(message);
        if (event.target.closest('.message-copy')) return copyMessage(message);
        if (event.target.closest('.message-favorite')) return favoriteMessage(message);
        if (event.target.closest('.message-edit')) return editMessage(message);
        if (event.target.closest('.message-delete')) return deleteMessage(message);
        if (event.target.closest('.message-forward')) return forwardMessage(message);
    }

    async function sendMessage() {
        const input = $('#message-input');
        const content = (input?.value || '').trim();
        if (!state.activeConversationId) return notify('Select a conversation first.', 'warning');
        if (!content && !state.selectedFile) return;

        const button = $('#send-message-btn');
        setBusy(button, true);
        try {
            const outgoingContent = content || (state.selectedFile ? `Attachment: ${state.selectedFile.name}` : null);
            const message = await request('/messages', {
                method: 'POST',
                body: {
                    conversationId: state.activeConversationId,
                    content: outgoingContent,
                    replyToMessageId: state.replyToMessageId
                }
            });
            if (state.selectedFile) await uploadAttachment(message.messageId, state.selectedFile);
            if (input) input.value = '';
            clearReply();
            clearSelectedFile();
            await Promise.all([loadMessages(state.activeConversationId), refreshConversationListOnly()]);
        } catch (error) {
            showError(error, 'Message could not be sent.');
        } finally {
            setBusy(button, false);
            input?.focus();
        }
    }

    async function uploadAttachment(messageId, file) {
        const formData = new FormData();
        formData.append('file', file);
        return request(`/messages/${messageId}/attachments`, { method: 'POST', body: formData });
    }

    async function editMessage(message) {
        const content = await openTextModal('Edit message', 'Save changes', message.content || '');
        if (content === null || !content.trim() || content.trim() === message.content) return;
        try {
            await request(`/messages/${message.messageId}`, { method: 'PATCH', body: { content: content.trim() } });
            await loadMessages(state.activeConversationId);
        } catch (error) { showError(error, 'Message could not be edited.'); }
    }

    async function deleteMessage(message) {
        if (!window.confirm('Delete this message?')) return;
        try {
            await request(`/messages/${message.messageId}`, { method: 'DELETE' });
            await Promise.all([loadMessages(state.activeConversationId), refreshConversationListOnly()]);
        } catch (error) { showError(error, 'Message could not be deleted.'); }
    }

    async function favoriteMessage(message) {
        try {
            await request(`/messages/${message.messageId}/favorite`, { method: 'PATCH', body: { favorite: !message.favorite } });
            message.favorite = !message.favorite;
            renderMessages();
        } catch (error) { showError(error, 'Favorite status could not be changed.'); }
    }

    async function markRead(messageId, showConfirmation) {
        try {
            await request(`/messages/${messageId}/read`, { method: 'PATCH' });
            const conversation = activeConversation();
            if (conversation) conversation.unreadCount = 0;
            renderConversations();
            if (showConfirmation) notify('Marked as read.', 'success');
        } catch (error) { if (showConfirmation) showError(error, 'Read status could not be changed.'); }
    }

    async function markConversationUnread(conversationIdValue) {
        try {
            await request(`/conversations/${conversationIdValue}/unread`, { method: 'PATCH' });
            const conversation = state.conversations.find(c => conversationId(c) === Number(conversationIdValue));
            if (conversation) conversation.unreadCount = Math.max(1, Number(conversation.unreadCount || 0));
            renderConversations();
            notify('Conversation marked as unread.', 'success');
        } catch (error) { showError(error, 'Conversation could not be marked as unread.'); }
    }

    async function forwardMessage(message) {
        const choices = state.conversations.filter(c => conversationId(c) !== state.activeConversationId && !c.archived);
        if (!choices.length) return notify('There is no other conversation to forward to.', 'warning');
        const preview = `<div class="chat-forward-preview mb-3"><div class="small text-muted mb-1">Forwarding</div><div class="fw-medium">${escapeHtml(message.content || 'Attachment')}</div>${message.attachments?.length ? `<div class="small text-muted mt-1"><i class="ti ti-paperclip me-1"></i>${message.attachments.length} attachment${message.attachments.length === 1 ? '' : 's'}</div>` : ''}</div>`;
        const target = Number(await openSelectModal('Forward message', 'Forward', choices.map(c => ({ value: conversationId(c), label: conversationLabel(c) })), preview));
        if (!choices.some(c => conversationId(c) === target)) return;
        try {
            await request(`/messages/${message.messageId}/forward`, { method: 'POST', body: { targetConversationId: target } });
            notify('Message forwarded.', 'success');
        } catch (error) { showError(error, 'Message could not be forwarded.'); }
    }

    async function toggleConversation(id, action) {
        const conversation = state.conversations.find(c => conversationId(c) === id);
        if (!conversation) return;
        const property = action === 'pin' ? 'pinned' : 'archived';
        try {
            await request(`/conversations/${id}/${action}`, { method: 'PATCH', body: { [property]: !conversation[property] } });
            conversation[property] = !conversation[property];
            if (property === 'archived' && conversation.archived && id === state.activeConversationId) state.activeConversationId = null;
            await loadConversations();
        } catch (error) { showError(error, `Chat could not be ${action === 'pin' ? 'pinned' : 'archived'}.`); }
    }

//Development-only...user override
//     function resolveDevelopmentUserId() {
//     const parameters =
//         new URLSearchParams(
//             window.location.search
//         );

//     const urlUserId =
//         Number(
//             parameters.get('devUserId')
//         );

//     if (
//         Number.isInteger(urlUserId) &&
//         urlUserId > 0
//     ) {
//         sessionStorage.setItem(
//             'chatDevUserId',
//             String(urlUserId)
//         );

//         return urlUserId;
//     }

//     const storedUserId =
//         Number(
//             sessionStorage.getItem(
//                 'chatDevUserId'
//             )
//         );

//     if (
//         Number.isInteger(storedUserId) &&
//         storedUserId > 0
//     ) {
//         return storedUserId;
//     }

//     return 1;
// }

    function openNewChat(event) {
        const modalElement = $('#new-chat-modal, #newChatModal');
        if (!modalElement) return;
        event?.preventDefault();
        state.selectedUserIds.clear();
        updateNewChatUi();
        const search = $('#user-search, #new-chat-user-search');
        if (search) search.value = '';
        renderUserResults([]);
        bootstrap.Modal.getOrCreateInstance(modalElement).show();
        setTimeout(() => search?.focus(), 200);
    }

    function scheduleUserSearch(event) {
        clearTimeout(state.userSearchTimer);
        state.userSearchTimer = setTimeout(() => searchUsers(event.target.value), 300);
    }

    async function searchUsers(term) {
        const query = term.trim();
        if (!query) return renderUserResults([]);
        try { renderUserResults(asArray(await request(`/users?search=${encodeURIComponent(query)}`))); }
        catch (error) { showError(error, 'User search failed.'); }
    }

    function renderUserResults(users) {
        const root = $('#user-search-results, #new-chat-user-results');
        if (!root) return;
        root.innerHTML = users.length ? users.map(user => `
            <label class="d-flex align-items-center gap-2 border rounded p-2 mb-2">
                <input class="form-check-input chat-user-choice" type="checkbox" value="${user.userId}" ${state.selectedUserIds.has(Number(user.userId)) ? 'checked' : ''}>
                <img src="${escapeAttr(avatarUrl(user.avatar))}" class="avatar avatar-sm rounded-circle" alt="">
                <span><span class="d-block fw-semibold">${escapeHtml(user.fullName)}</span><small class="text-muted">${escapeHtml(user.designation || user.email || '')}</small></span>
            </label>`).join('') : '<div class="text-muted text-center py-3">Search for a user to start a chat.</div>';
    }

    function onUserSelection(event) {
        if (!event.target.matches('.chat-user-choice')) return;
        const id = Number(event.target.value);
        event.target.checked ? state.selectedUserIds.add(id) : state.selectedUserIds.delete(id);
        updateNewChatUi();
    }

    function updateNewChatUi() {
        const count = state.selectedUserIds.size;
        setText('#new-chat-selection-count', count ? `${count} contact${count === 1 ? '' : 's'} selected` : 'Select at least one contact');
        const titleGroup = $('#new-chat-group-title-wrap');
        titleGroup?.classList.toggle('d-none', count < 2);
        const button = $('#create-conversation-btn');
        if (button) {
            button.disabled = count === 0;
            button.textContent = count > 1 ? 'Create Group' : 'Start Chat';
        }
    }

    async function createConversation() {
        const participantUserIds = Array.from(state.selectedUserIds);
        if (!participantUserIds.length) return notify('Select at least one user.', 'warning');
        const conversationType = participantUserIds.length > 1 ? 'GROUP' : 'DIRECT';
        const title = ($('#conversation-title')?.value || '').trim();
        if (conversationType === 'GROUP' && !title) return notify('Enter a group title.', 'warning');
        const button = $('#create-conversation-btn');
        setBusy(button, true);
        try {
            const conversation = await request('/conversations', { method: 'POST', body: { conversationType, title: title || null, participantUserIds } });
            const modal = $('#new-chat-modal, #newChatModal');
            if (modal && window.bootstrap) bootstrap.Modal.getInstance(modal)?.hide();
            await loadConversations(conversationId(conversation));
        } catch (error) { showError(error, 'Conversation could not be created.'); }
        finally { setBusy(button, false); }
    }

    function onFileSelected(event) {
        state.selectedFile = event.target.files?.[0] || null;
        const label = $('#selected-file-name, #attachment-file-name');
        if (label) {
            label.textContent = state.selectedFile?.name || '';
            label.closest('.selected-attachment, #attachment-preview')?.classList.toggle('d-none', !state.selectedFile);
        } else if (state.selectedFile) notify(`Attached: ${state.selectedFile.name}`, 'info');
    }

    function clearSelectedFile() {
        state.selectedFile = null;
        const input = $('#message-file-input');
        if (input) input.value = '';
        setText('#selected-file-name, #attachment-file-name', '');
        $('#attachment-preview')?.classList.add('d-none');
    }

    function setReply(message) {
        state.replyToMessageId = message.messageId;
        const bar = $('#reply-preview');
        if (bar) {
            bar.classList.remove('d-none');
            const sender = message.sender?.fullName || 'User';
            setText('#reply-preview-text', `${sender}: ${message.content || 'Attachment'}`);
        }
        $('#message-input')?.focus();
    }

    function clearReply() {
        state.replyToMessageId = null;
        $('#reply-preview')?.classList.add('d-none');
        setText('#reply-preview-text', '');
    }

    async function copyMessage(message) {
        try { await navigator.clipboard.writeText(message.content || ''); notify('Message copied.', 'success'); }
        catch (_) { notify('Could not copy the message.', 'danger'); }
    }

    async function refreshConversationListOnly() {
        try { state.conversations = asArray(await request('/conversations')); renderConversations(); renderActiveHeader(); syncConversationSubscriptions(); }
        catch (_) { /* A sent message remains visible even if list refresh fails. */ }
    }

    // =========================================================
    // REAL-TIME WEBSOCKET / STOMP
    // =========================================================

    function connectChatSocket() {
        if (state.stompClient?.active || state.stompClient?.connected) return;
        if (!window.StompJs?.Client) {
            console.warn('STOMP client is not loaded. Chat will continue using REST only.');
            setRealtimeStatus(false);
            return;
        }

        const protocol = window.location.protocol === 'https:' ? 'wss' : 'ws';
        state.stompClient = new StompJs.Client({
            brokerURL: `${protocol}://${window.location.host}/ws-chat`,
            connectHeaders: {
                Authorization: `Bearer ${window.CrmsAuth.getAccessToken()}`
            },
            reconnectDelay: 5000,
            heartbeatIncoming: 10000,
            heartbeatOutgoing: 10000,
            debug: () => {},
            onConnect: () => {
                state.socketConnected = true;
                state.conversationSubscriptions.clear();
                syncConversationSubscriptions();
                setRealtimeStatus(true);
                refreshConversationListOnly();
                if (state.activeConversationId) loadMessages(state.activeConversationId);
            },
            onDisconnect: () => {
                state.socketConnected = false;
                setRealtimeStatus(false);
            },
            onWebSocketClose: () => {
                state.socketConnected = false;
                state.conversationSubscriptions.clear();
                setRealtimeStatus(false);
            },
            onStompError: frame => {
                console.error('Chat WebSocket broker error:', frame.headers?.message || frame.body);
                setRealtimeStatus(false);
            },
            onWebSocketError: error => {
                console.error('Chat WebSocket connection error:', error);
                setRealtimeStatus(false);
            }
        });

        state.stompClient.activate();
    }

    function disconnectChatSocket() {
        state.conversationSubscriptions.forEach(subscription => {
            try { subscription.unsubscribe(); } catch (_) {}
        });
        state.conversationSubscriptions.clear();
        if (state.stompClient?.active) state.stompClient.deactivate();
        state.socketConnected = false;
    }

    function syncConversationSubscriptions() {
        if (!state.socketConnected || !state.stompClient?.connected) return;

        const desiredIds = new Set(state.conversations.map(conversationId).filter(Boolean));
        state.conversationSubscriptions.forEach((subscription, id) => {
            if (!desiredIds.has(id)) {
                try { subscription.unsubscribe(); } catch (_) {}
                state.conversationSubscriptions.delete(id);
            }
        });

        desiredIds.forEach(id => {
            if (state.conversationSubscriptions.has(id)) return;
            const subscription = state.stompClient.subscribe(
                `/topic/chat/conversations/${id}`,
                frame => {
                    try { handleRealtimeEvent(JSON.parse(frame.body)); }
                    catch (error) { console.error('Invalid chat WebSocket event:', error); }
                }
            );
            state.conversationSubscriptions.set(id, subscription);
        });
    }

    function handleRealtimeEvent(event) {
        if (!event?.eventType || !event.conversationId) return;
        const conversationIdValue = Number(event.conversationId);

        switch (event.eventType) {
            case 'MESSAGE_CREATED':
                upsertRealtimeMessage(event.payload, conversationIdValue);
                refreshConversationListOnly();
                break;
            case 'MESSAGE_UPDATED':
            case 'ATTACHMENT_ADDED':
                upsertRealtimeMessage(event.payload, conversationIdValue);
                refreshConversationListOnly();
                break;
            case 'MESSAGE_DELETED':
                markRealtimeMessageDeleted(event.messageId, conversationIdValue);
                refreshConversationListOnly();
                break;
            case 'PARTICIPANT_ADDED':
            case 'PARTICIPANT_REMOVED':
                loadConversations(state.activeConversationId);
                break;
            case 'CONVERSATION_READ':
            case 'CONVERSATION_UNREAD':
                refreshConversationListOnly();
                break;
            default:
                break;
        }
        window.dispatchEvent(new CustomEvent('crms:chat-notification-change'));
    }

    function upsertRealtimeMessage(message, conversationIdValue) {
        if (!message || conversationIdValue !== state.activeConversationId) return;
        const index = state.messages.findIndex(item => Number(item.messageId) === Number(message.messageId));
        if (index >= 0) state.messages[index] = message;
        else state.messages.push(message);
        state.messages.sort((a, b) => parseApiDate(a.createdAt) - parseApiDate(b.createdAt));
        renderMessages();

        if (Number(message.sender?.userId) !== CURRENT_USER_ID) {
            markRead(message.messageId, false);
        }
    }

    function markRealtimeMessageDeleted(messageId, conversationIdValue) {
        if (conversationIdValue !== state.activeConversationId) return;
        const message = state.messages.find(item => Number(item.messageId) === Number(messageId));
        if (!message) return;
        message.deleted = true;
        message.content = null;
        message.attachments = [];
        renderMessages();
    }

    function setRealtimeStatus(connected) {
        const status = $('#chat-realtime-status');
        if (!status) return;
        status.classList.toggle('text-success', connected);
        status.classList.toggle('text-muted', !connected);
        status.title = connected ? 'Real-time chat connected' : 'Real-time chat reconnecting';
        status.innerHTML = `<i class="ti ${connected ? 'ti-wifi' : 'ti-wifi-off'}"></i>`;
    }

    // =========================================================
    // GROUP PARTICIPANT MANAGEMENT
    // =========================================================

    function openManageParticipants() {
        const modalElement = $('#manageParticipantsModal');
        const conversation = activeConversation();
        if (!modalElement || !conversation) return;
        state.selectedAddParticipantIds = new Set();
        renderCurrentParticipants();
        updateAddParticipantsUi();
        const search = $('#add-participant-search');
        if (search) search.value = '';
        renderAddParticipantResults([]);
        bootstrap.Modal.getOrCreateInstance(modalElement).show();
    }

    function renderCurrentParticipants() {
        const conversation = activeConversation();
        const host = $('#current-participants-list');
        if (!host || !conversation) return;
        const participants = conversation.participants || [];
        host.innerHTML = participants.length ? participants.map(p => `
            <div class="d-flex align-items-center justify-content-between border rounded p-2 mb-2" data-user-id="${p.userId}">
                <div class="d-flex align-items-center gap-2 overflow-hidden">
                    <img src="${escapeAttr(avatarUrl(p.avatar))}" class="avatar avatar-sm rounded-circle" alt="">
                    <span class="text-truncate">${escapeHtml(p.fullName || 'User')}${Number(p.userId) === CURRENT_USER_ID ? ' (You)' : ''}</span>
                </div>
                ${Number(p.userId) === CURRENT_USER_ID ? '' : `
                    <button type="button" class="btn btn-sm btn-icon btn-light text-danger remove-participant-btn flex-shrink-0" title="Remove participant">
                        <i class="ti ti-x"></i>
                    </button>`}
            </div>`).join('') : '<div class="text-muted small">No participants.</div>';
    }

    async function onRemoveParticipantClick(event) {
        const button = event.target.closest('.remove-participant-btn');
        if (!button) return;
        const row = button.closest('[data-user-id]');
        const userId = Number(row?.dataset.userId);
        const conversation = activeConversation();
        if (!conversation || !userId) return;
        if (!window.confirm('Remove this participant from the group?')) return;
        try {
            await request(`/conversations/${conversation.conversationId}/participants/${userId}`, { method: 'DELETE' });
            conversation.participants = (conversation.participants || []).filter(p => Number(p.userId) !== userId);
            renderCurrentParticipants();
            renderActiveHeader();
            notify('Participant removed.', 'success');
        } catch (error) { showError(error, 'Could not remove participant.'); }
    }

    function scheduleParticipantSearch(event) {
        clearTimeout(state.participantSearchTimer);
        state.participantSearchTimer = setTimeout(() => searchParticipantsToAdd(event.target.value), 300);
    }

    async function searchParticipantsToAdd(term) {
        const query = term.trim();
        if (!query) return renderAddParticipantResults([]);
        try {
            const users = asArray(await request(`/users?search=${encodeURIComponent(query)}`));
            const conversation = activeConversation();
            const existingIds = new Set((conversation?.participants || []).map(p => Number(p.userId)));
            renderAddParticipantResults(users.filter(u => !existingIds.has(Number(u.userId))));
        } catch (error) { showError(error, 'User search failed.'); }
    }

    function renderAddParticipantResults(users) {
        const root = $('#add-participant-results');
        if (!root) return;
        root.innerHTML = users.length ? users.map(user => `
            <label class="d-flex align-items-center gap-2 border rounded p-2 mb-2">
                <input class="form-check-input add-participant-choice" type="checkbox" value="${user.userId}" ${state.selectedAddParticipantIds.has(Number(user.userId)) ? 'checked' : ''}>
                <img src="${escapeAttr(avatarUrl(user.avatar))}" class="avatar avatar-sm rounded-circle" alt="">
                <span><span class="d-block fw-semibold">${escapeHtml(user.fullName)}</span><small class="text-muted">${escapeHtml(user.designation || user.email || '')}</small></span>
            </label>`).join('') : '<div class="text-muted text-center py-3">Search for a user to add.</div>';
    }

    function onAddParticipantSelection(event) {
        if (!event.target.matches('.add-participant-choice')) return;
        const id = Number(event.target.value);
        event.target.checked ? state.selectedAddParticipantIds.add(id) : state.selectedAddParticipantIds.delete(id);
        updateAddParticipantsUi();
    }

    function updateAddParticipantsUi() {
        const count = state.selectedAddParticipantIds.size;
        setText('#add-participant-selection-count', count ? `${count} contact${count === 1 ? '' : 's'} selected` : 'Select at least one contact');
        const button = $('#add-participants-btn');
        if (button) button.disabled = count === 0;
    }

    async function addParticipants() {
        const conversation = activeConversation();
        const participantUserIds = Array.from(state.selectedAddParticipantIds);
        if (!conversation || !participantUserIds.length) return;
        const button = $('#add-participants-btn');
        setBusy(button, true);
        try {
            const updated = await request(`/conversations/${conversation.conversationId}/participants`, {
                method: 'POST',
                body: { participantUserIds }
            });
            Object.assign(conversation, updated);
            state.selectedAddParticipantIds.clear();
            renderCurrentParticipants();
            renderAddParticipantResults([]);
            updateAddParticipantsUi();
            const search = $('#add-participant-search');
            if (search) search.value = '';
            renderActiveHeader();
            notify('Participants added.', 'success');
        } catch (error) { showError(error, 'Could not add participants.'); }
        finally { setBusy(button, false); }
    }

    function messageRenderHost() {
        const root = $('#message-list');
        if (!root) return null;
        const simplebarContent = $('.simplebar-content', root);
        if (simplebarContent) {
            let host = $('#message-render-content', simplebarContent);
            if (!host) {
                host = document.createElement('div');
                host.id = 'message-render-content';
                simplebarContent.replaceChildren(host);
            }
            return host;
        }
        return root;
    }

    function populateCurrentUser() {
        const current = state.conversations.flatMap(c => c.participants || []).find(p => Number(p.userId) === CURRENT_USER_ID);
        if (!current) return;
        setText('#current-user-name', current.fullName || 'Current User');
        setText('#current-user-role', current.designation || '');
        const avatar = $('#current-user-avatar');
        if (avatar) avatar.src = avatarUrl(current.avatar);
    }

    function openTextModal(title, action, value) {
        return openActionModal({ title, action, control: `<textarea class="form-control" id="chat-action-value" rows="4" maxlength="10000">${escapeHtml(value)}</textarea>` });
    }

    function openSelectModal(title, action, choices, preview = '') {
        const options = choices.map(c => `<option value="${escapeAttr(c.value)}">${escapeHtml(c.label)}</option>`).join('');
        return openActionModal({ title, action, control: `${preview}<select class="form-select" id="chat-action-value"><option value="">Select a conversation</option>${options}</select>` });
    }

    function openActionModal({ title, action, control }) {
        const element = $('#chat-action-modal');
        if (!element || !window.bootstrap) return Promise.resolve(null);
        setText('#chat-action-title', title);
        const body = $('#chat-action-control');
        body.innerHTML = control;
        const confirm = $('#chat-action-confirm');
        confirm.textContent = action;
        const valueControl = $('#chat-action-value');
        const requiresChoice = valueControl?.tagName === 'SELECT';
        confirm.disabled = requiresChoice && !valueControl.value;
        if (requiresChoice) valueControl.addEventListener('change', () => { confirm.disabled = !valueControl.value; });
        const modal = bootstrap.Modal.getOrCreateInstance(element);
        return new Promise(resolve => {
            let settled = false;
            const finish = value => { if (settled) return; settled = true; resolve(value); };
            confirm.onclick = () => { const value = $('#chat-action-value')?.value ?? null; if (!value.trim()) return; finish(value); modal.hide(); };
            element.addEventListener('hidden.bs.modal', () => finish(null), { once: true });
            modal.show();
            setTimeout(() => $('#chat-action-value')?.focus(), 200);
        });
    }

    function installChatUiEnhancements() {
        const newChatModal = $('#newChatModal, #new-chat-modal');
        if (newChatModal && !$('#create-conversation-btn', newChatModal)) {
            const body = $('.modal-body', newChatModal);
            body?.insertAdjacentHTML('beforeend', `
                <div id="new-chat-group-title-wrap" class="d-none mt-3">
                    <label for="conversation-title" class="form-label">Group name</label>
                    <input type="text" id="conversation-title" class="form-control" maxlength="150" placeholder="Enter a group name">
                </div>`);
            $('.modal-content', newChatModal)?.insertAdjacentHTML('beforeend', `
                <div class="modal-footer justify-content-between">
                    <span id="new-chat-selection-count" class="small text-muted">Select at least one contact</span>
                    <div class="d-flex gap-2">
                        <button type="button" class="btn btn-light" data-bs-dismiss="modal">Cancel</button>
                        <button type="button" id="create-conversation-btn" class="btn btn-primary" disabled>Start Chat</button>
                    </div>
                </div>`);
        }
        if (!$('#chat-action-modal')) document.body.insertAdjacentHTML('beforeend', `
            <div class="modal fade" id="chat-action-modal" tabindex="-1" aria-hidden="true">
                <div class="modal-dialog modal-dialog-centered"><div class="modal-content">
                    <div class="modal-header"><h5 class="modal-title" id="chat-action-title"></h5><button type="button" class="btn-close" data-bs-dismiss="modal" aria-label="Close"></button></div>
                    <div class="modal-body" id="chat-action-control"></div>
                    <div class="modal-footer"><button type="button" class="btn btn-light" data-bs-dismiss="modal">Cancel</button><button type="button" class="btn btn-primary" id="chat-action-confirm"></button></div>
                </div></div>
            </div>`);
        const messageFooter = $('.message-footer');
        if (messageFooter && !$('#attachment-preview')) {
            messageFooter.insertAdjacentHTML('beforebegin', `
                <div id="attachment-preview" class="d-none chat-attachment-preview border-top px-3 py-2">
                    <div class="d-flex align-items-center justify-content-between rounded bg-light px-3 py-2">
                        <div class="d-flex align-items-center gap-2 overflow-hidden">
                            <i class="ti ti-paperclip text-primary fs-5"></i>
                            <span id="selected-file-name" class="text-truncate"></span>
                        </div>
                        <button type="button" id="clear-attachment-btn" class="btn btn-sm btn-icon btn-light" aria-label="Remove attachment"><i class="ti ti-x"></i></button>
                    </div>
                </div>`);
        }
        if (!$('#chat-ui-enhancements')) {
            const style = document.createElement('style');
            style.id = 'chat-ui-enhancements';
            style.textContent = `
                .chat-wrapper{height:calc(100vh - 170px);min-height:560px}.chat-wrapper>.card,.chat-wrapper>.card>.card-body,.chat-wrapper .d-lg-flex{height:100%}
                .chat-user-nav{display:flex;flex-direction:column;height:100%;min-width:330px;overflow:hidden}.chat-user-nav>div{display:flex;flex-direction:column;height:100%;min-height:0;overflow:hidden}
                .chat-user-nav>div>div:last-child{display:flex;flex:1 1 auto;flex-direction:column;min-height:0;overflow:hidden}.chat-user-nav>div>div:last-child>.input-group{flex:0 0 auto}
                .chat-users{display:block;flex:1 1 0;height:auto!important;max-height:none!important;min-height:0;padding:0!important;overflow:hidden}
                .chat-users>.simplebar-wrapper{height:100%!important;margin:0!important}.chat-users .simplebar-content-wrapper{height:100%!important;overflow-x:hidden!important;overflow-y:auto!important;overscroll-behavior:contain}.chat-users .simplebar-content{padding:16px 24px!important}
                .chat-main,.chat-messages{min-width:0;height:100%}.message-body{flex:1;max-height:none!important;min-height:0;overflow:auto}
                #message-list{height:calc(100vh - 310px)!important;max-height:none!important;min-height:400px}
                #message-render-content{padding-bottom:12px}.chat-list{width:fit-content;max-width:78%;margin-bottom:18px;background:transparent!important;border-radius:0!important}
                .chat-list .chat-avatar,.chat-list .chat-avatar img{width:40px!important;height:40px!important}.chat-list .chat-content{min-width:0;max-width:100%}
                .chat-list .message-content{padding:10px 14px;line-height:1.5;box-shadow:0 1px 2px rgba(0,0,0,.04);overflow-wrap:anywhere}
                .chat-list .sent-message{background:var(--primary);color:#fff}.chat-list .sent-message .text-muted{color:rgba(255,255,255,.72)!important}
                .chat-list .receive-message{background:var(--light-200)}.chat-actions{opacity:0;transition:opacity .15s}.chat-list:hover .chat-actions{opacity:1}
                .chat-reply-quote{display:flex;flex-direction:column;gap:2px;margin-bottom:8px;padding:7px 10px;border-left:3px solid currentColor;border-radius:4px;background:rgba(255,255,255,.18);opacity:.9}.receive-message .chat-reply-quote{background:rgba(0,0,0,.04)}.chat-reply-author{font-size:11px;font-weight:700}.chat-reply-text{max-width:320px;overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.chat-forward-preview{padding:12px;border:1px solid var(--border-color);border-radius:8px;background:var(--light-100)}
                .user-list{width:100%;max-width:100%;padding:9px 8px;margin:0 0 3px;box-sizing:border-box}.user-list .conversation-open{min-width:0}.user-list.active{background:rgba(var(--primary-rgb),.1)}
                .chat-section-label{padding:12px 0 6px;font-size:11px;font-weight:700;letter-spacing:.06em;text-transform:uppercase;color:var(--gray-500)}
                .chat-archived{width:100%;max-width:100%;overflow:hidden}.chat-archived summary{cursor:pointer;list-style:none;position:sticky;bottom:0;background:var(--white);z-index:2}.chat-date-separator{display:flex;align-items:center;gap:12px;margin:20px 0;color:var(--gray-500);font-size:12px}
                .chat-date-separator:before,.chat-date-separator:after{content:'';height:1px;background:var(--border-color);flex:1}.chat-empty-state{min-height:300px;display:flex;flex-direction:column;align-items:center;justify-content:center;gap:10px;color:var(--gray-500)}
                .chat-empty-state i{font-size:42px}.message-footer{flex-shrink:0}.chat-wrapper [title="Voice Call"],.chat-wrapper [title="Video Call"]{pointer-events:none;opacity:.45}
                #new-chat-user-results,#add-participant-results{max-height:320px;overflow-y:auto}.chat-user-choice:checked+img,.add-participant-choice:checked+img{outline:2px solid var(--primary);outline-offset:2px}
                #current-participants-list{max-height:220px;overflow-y:auto}
                .chat-attachment-preview{background:var(--white);flex-shrink:0}.chat-attachment-preview>div{max-width:520px}
                @media(max-width:991.98px){.chat-wrapper{height:auto;min-height:0}.chat-user-nav{height:420px}.chat-main,.chat-messages{height:650px}#message-list{height:510px!important;min-height:0}}
                @media(max-width:575.98px){.chat-user-nav{min-width:100%}.chat-list{max-width:92%}.chat-wrapper{margin-left:-12px;margin-right:-12px}}
            `;
            document.head.appendChild(style);
        }
        ['#active-chat-call, [data-bs-title="Voice Call"]', '#active-chat-video, [data-bs-title="Video Call"]'].forEach(selector => {
            $$(selector).forEach(button => { button.setAttribute('aria-disabled', 'true'); button.setAttribute('title', 'Coming soon'); });
        });
    }

    function activeConversation() { return state.conversations.find(c => conversationId(c) === state.activeConversationId); }
    function conversationId(c) { return Number(c?.conversationId); }
    function otherParticipant(c) { return (c?.participants || []).find(p => Number(p.userId) !== CURRENT_USER_ID) || c?.participants?.[0]; }
    function conversationLabel(c) { return c?.conversationType === 'GROUP' ? (c.title || 'Group chat') : (otherParticipant(c)?.fullName || c?.title || 'Conversation'); }
    function avatarUrl(path) {
        const value = String(path ?? '').trim();
        if (!value || value.toLowerCase() === 'null' || value.toLowerCase() === 'undefined') return DEFAULT_AVATAR;
        return value;
    }
    function asArray(data) { if (Array.isArray(data)) return data; return data?.content || data?.items || data?.data || []; }

    function renderAttachments(attachments) {
        return attachments.map(file => {
            const rawPath = String(file.filePath ?? '').trim();
            const hasPath = rawPath && !['null', 'undefined'].includes(rawPath.toLowerCase());
            const href = hasPath ? (rawPath.startsWith('/') ? rawPath : `/${rawPath}`) : '';
            const image = (file.mimeType || '').startsWith('image/');
            if (!hasPath) return `<div class="d-flex align-items-center gap-2 border rounded p-2 mt-2 text-muted"><i class="ti ti-file fs-4"></i><span>${escapeHtml(file.fileName || 'Attachment unavailable')}</span></div>`;
            return image
                ? `<a href="${escapeAttr(href)}" target="_blank" rel="noopener"><img src="${escapeAttr(href)}" class="img-fluid rounded mt-2" alt="${escapeAttr(file.fileName || 'Attachment')}"></a>`
                : `<a href="${escapeAttr(href)}" target="_blank" rel="noopener" class="d-flex align-items-center gap-2 border rounded p-2 mt-2"><i class="ti ti-file fs-4"></i><span>${escapeHtml(file.fileName || 'Attachment')}<small class="d-block text-muted">${formatBytes(file.fileSize)}</small></span></a>`;
        }).join('');
    }

    function showNoConversation() {
        state.activeConversationId = null;
        state.messages = [];
        setText('#active-chat-name', 'Select a conversation');
        setText('#active-chat-status', '');
        renderMessageNotice('Select a conversation to view messages.');
        setComposerEnabled(false);
        const groupDropdown = $('#group-manage-dropdown');
        if (groupDropdown) groupDropdown.style.display = 'none';
    }

    function renderConversationNotice(text) { const list = $('#conversation-list'); if (list) list.innerHTML = `<div class="text-center text-muted py-4">${escapeHtml(text)}</div>`; }
    function renderMessageNotice(text) { const list = messageRenderHost(); if (list) list.innerHTML = `<div id="message-empty-state" class="chat-empty-state"><i class="ti ti-messages"></i><span>${escapeHtml(text)}</span></div>`; }
    function setConversationLoading(loading) { $('#conversation-list')?.setAttribute('aria-busy', String(loading)); }
    function setComposerEnabled(enabled) { ['#message-input', '#message-file-input', '#attachment-btn', '#send-message-btn'].forEach(s => { const el = $(s); if (el) el.disabled = !enabled; }); }
    function setBusy(element, busy) { if (!element) return; element.disabled = busy; element.classList.toggle('disabled', busy); }
    function setText(selector, text) { const element = $(selector); if (element) element.textContent = text; }
    function scrollMessagesToBottom() { const list = $('#message-list'); if (!list) return; const simplebar = window.SimpleBar?.instances?.get?.(list); const target = simplebar?.getScrollElement?.() || list; target.scrollTop = target.scrollHeight; }

    function initializeTemplatePlugins(root = document) {
        if (window.bootstrap?.Tooltip) $$('[data-bs-toggle="tooltip"]', root).forEach(el => bootstrap.Tooltip.getOrCreateInstance(el));
        if (window.SimpleBar) $$('[data-simplebar]', root).forEach(el => { if (!SimpleBar.instances?.has?.(el)) new SimpleBar(el); });
    }

    function notify(message, type = 'info') {
        if (window.Swal?.fire) return Swal.fire({ text: message, icon: type === 'danger' ? 'error' : type, timer: 2200, showConfirmButton: false });
        const toastRoot = $('#chat-toast');
        if (toastRoot && window.bootstrap?.Toast) { $('.toast-body', toastRoot).textContent = message; bootstrap.Toast.getOrCreateInstance(toastRoot).show(); return; }
        if (type === 'danger') window.alert(message);
    }

    function showError(error, fallback) {
        console.error(error);
        notify(error?.message || fallback, 'danger');
    }

    function formatTime(value) {
        if (!value) return '';
        const date = parseApiDate(value);
        if (Number.isNaN(date.getTime())) return '';
        const today = new Date();
        return date.toDateString() === today.toDateString()
            ? date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
            : date.toLocaleDateString([], { month: 'short', day: 'numeric' });
    }

    function formatMessageTime(value) {
        const date = parseApiDate(value);
        return Number.isNaN(date.getTime()) ? '' : date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }



    function formatDay(value) {
        const date = parseApiDate(value);
        if (Number.isNaN(date.getTime())) return '';
        const today = new Date();
        const yesterday = new Date(today); yesterday.setDate(today.getDate() - 1);
        if (date.toDateString() === today.toDateString()) return 'Today';
        if (date.toDateString() === yesterday.toDateString()) return 'Yesterday';
        return date.toLocaleDateString([], { day: 'numeric', month: 'short', year: date.getFullYear() === today.getFullYear() ? undefined : 'numeric' });
    }

    function parseApiDate(value) {
        if (!value) return new Date(NaN);
        const text = String(value).trim();
        // Chat timestamps are stored in UTC, but LocalDateTime JSON values do
        // not include a zone suffix. Explicitly mark them as UTC so the
        // browser converts them to the user's real local time.
        const normalized = /^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}/.test(text)
            && !/(?:Z|[+-]\d{2}:?\d{2})$/i.test(text)
            ? `${text}Z`
            : text;
        return new Date(normalized);
    }

    function formatBytes(bytes) {
        const value = Number(bytes || 0);
        if (!value) return '';
        const units = ['B', 'KB', 'MB', 'GB'];
        const index = Math.min(Math.floor(Math.log(value) / Math.log(1024)), units.length - 1);
        return `${(value / Math.pow(1024, index)).toFixed(index ? 1 : 0)} ${units[index]}`;
    }

    function escapeHtml(value) {
        return String(value ?? '').replace(/[&<>'"]/g, char => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', "'": '&#39;', '"': '&quot;' })[char]);
    }
    function escapeAttr(value) { return escapeHtml(value); }
    function linkify(value) {
        const escaped = escapeHtml(value).replace(/\n/g, '<br>');
        return escaped.replace(/(https?:\/\/[^\s<]+)/g, '<a href="$1" target="_blank" rel="noopener">$1</a>');
    }
    // =====================================================
// Chat Sidebar Resizer
// =====================================================

document.addEventListener("DOMContentLoaded", function () {

    const sidebar = document.querySelector(".chat-user-nav");
    const resizer = document.getElementById("chatResizer");

    // Check if both elements exist
    if (!sidebar || !resizer) {
        console.log("Chat resizer elements not found.");
        return;
    }

    let isResizing = false;

    // Start dragging
    resizer.addEventListener("mousedown", function (event) {

        isResizing = true;

        document.body.classList.add("chat-resizing");

        event.preventDefault();

    });

    // Move sidebar while dragging
    document.addEventListener("mousemove", function (event) {

        if (!isResizing) {
            return;
        }

        const chatWrapper = document.querySelector(".chat-wrapper");

        if (!chatWrapper) {
            return;
        }

        const wrapperRect = chatWrapper.getBoundingClientRect();

        // Calculate new sidebar width
        let newWidth = event.clientX - wrapperRect.left;

        // Minimum width
        if (newWidth < 100) {
            newWidth = 100;
        }

        // Maximum width
        if (newWidth > 600) {
            newWidth = 600;
        }

        sidebar.style.width = newWidth + "px";

    });

    // Stop dragging
    document.addEventListener("mouseup", function () {

        if (!isResizing) {
            return;
        }

        isResizing = false;

        document.body.classList.remove("chat-resizing");

    });

});

    window.ChatApp = {
        reload: loadConversations,
        selectConversation,
        connectSocket: connectChatSocket,
        disconnectSocket: disconnectChatSocket,
        currentUserId: CURRENT_USER_ID
    };
})();
