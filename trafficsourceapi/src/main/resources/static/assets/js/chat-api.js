(function () {
    'use strict';

    const API = '/api/chat';
    const CURRENT_USER_ID = Number(document.querySelector('meta[name="chat-current-user-id"]')?.content || 1);
    const FALLBACK_AVATAR = 'assets/img/users/user-01.jpg';
    const state = { conversations: [], active: null, messages: [], replyTo: null, attachment: null };

    const $ = (selector, root = document) => root.querySelector(selector);
    const escapeHtml = (value) => String(value ?? '').replace(/[&<>'"]/g, c => ({'&':'&amp;','<':'&lt;','>':'&gt;',"'":'&#39;','"':'&quot;'}[c]));
    const avatar = (value) => value && !value.startsWith('http') && !value.startsWith('/') ? value : (value || FALLBACK_AVATAR);
    const date = (value) => value ? new Date(value).toLocaleString([], { month:'short', day:'numeric', hour:'2-digit', minute:'2-digit' }) : '';

    async function request(path, options = {}) {
        const response = await fetch(API + path, { credentials: 'same-origin', ...options,
            headers: options.body instanceof FormData ? options.headers : { 'Content-Type':'application/json', ...(options.headers || {}) }
        });
        if (!response.ok) {
            let message = `Request failed (${response.status})`;
            try { message = (await response.json()).message || message; } catch (_) {}
            throw new Error(message);
        }
        return response.status === 204 ? null : response.json();
    }

    function peer(conversation) {
        return conversation.participants?.find(p => Number(p.userId) !== CURRENT_USER_ID) || conversation.participants?.[0] || {};
    }

    function conversationName(conversation) {
        return conversation.title || peer(conversation).fullName || 'Conversation';
    }

    function renderConversations(filter = '') {
        const host = $('.chat-users');
        const query = filter.trim().toLowerCase();
        const rows = state.conversations.filter(c => !c.archived && conversationName(c).toLowerCase().includes(query));
        host.innerHTML = `<h6 class="mb-3">All Messages</h6>${rows.map(c => {
            const person = peer(c);
            return `<button type="button" class="w-100 border-0 d-flex align-items-center justify-content-between rounded p-3 user-list mb-1 ${state.active?.conversationId === c.conversationId ? 'active' : ''}" data-conversation-id="${c.conversationId}">
                <span class="d-flex align-items-center text-start overflow-hidden"><span class="avatar me-2 flex-shrink-0"><img src="${escapeHtml(avatar(person.avatar))}" class="rounded-circle" alt=""></span><span class="overflow-hidden"><strong class="fs-14 d-block text-truncate">${escapeHtml(conversationName(c))}</strong><span class="d-block text-truncate">${escapeHtml(c.lastMessage?.content || 'No messages yet')}</span></span></span>
                <span class="text-end ms-2 flex-shrink-0"><span class="text-dark d-block">${escapeHtml(date(c.lastMessage?.createdAt))}</span>${c.unreadCount ? `<span class="badge bg-danger rounded-circle message-count">${c.unreadCount}</span>` : (c.pinned ? '<i class="ti ti-pinned text-primary"></i>' : '')}</span>
            </button>`;
        }).join('') || '<p class="text-muted p-3">No conversations found.</p>'}`;
        host.querySelectorAll('[data-conversation-id]').forEach(el => el.addEventListener('click', () => openConversation(Number(el.dataset.conversationId))));
    }

    function messageMenu(message, mine) {
        return `<div class="${mine ? 'me-2' : 'ms-2'}"><a href="#" data-bs-toggle="dropdown"><i class="ti ti-dots-vertical"></i></a><ul class="dropdown-menu p-2">
            <li><button class="dropdown-item" data-action="reply" data-id="${message.messageId}"><i class="ti ti-arrow-back-up me-1"></i>Reply</button></li>
            <li><button class="dropdown-item" data-action="forward" data-id="${message.messageId}"><i class="ti ti-arrow-forward-up me-1"></i>Forward</button></li>
            <li><button class="dropdown-item" data-action="copy" data-id="${message.messageId}"><i class="ti ti-copy me-1"></i>Copy</button></li>
            <li><button class="dropdown-item" data-action="favorite" data-id="${message.messageId}"><i class="ti ti-heart me-1"></i>${message.favorite ? 'Unfavorite' : 'Favorite'}</button></li>
            <li><button class="dropdown-item" data-action="archive" data-id="${message.messageId}"><i class="ti ti-archive me-1"></i>Archive chat</button></li>
            <li><button class="dropdown-item" data-action="pin" data-id="${message.messageId}"><i class="ti ti-pinned me-1"></i>${state.active?.pinned ? 'Unpin' : 'Pin'} chat</button></li>
            ${mine ? `<li><button class="dropdown-item" data-action="edit" data-id="${message.messageId}"><i class="ti ti-edit me-1"></i>Edit</button></li><li><button class="dropdown-item text-danger" data-action="delete" data-id="${message.messageId}"><i class="ti ti-trash me-1"></i>Delete</button></li>` : ''}
        </ul></div>`;
    }

    function renderMessages() {
        const host = $('.message-body');
        host.innerHTML = state.messages.map(m => {
            const mine = Number(m.sender?.userId) === CURRENT_USER_ID;
            const files = (m.attachments || []).map(a => `<a class="d-block mt-2" href="${escapeHtml(a.filePath)}" target="_blank" rel="noopener"><i class="ti ti-paperclip"></i> ${escapeHtml(a.fileName)}</a>`).join('');
            const reply = m.replyToMessageId ? `<small class="d-block opacity-75 mb-1">Reply to #${m.replyToMessageId}</small>` : '';
            const box = `<div class="message-box ${mine ? 'sent-message' : 'receive-message'} p-3"><p class="mb-0 fs-14">${reply}${escapeHtml(m.deleted ? 'Message deleted' : m.content || '')}${files}</p></div>`;
            return `<div class="chat-list ${mine ? 'ms-auto' : ''} mb-3" data-message-id="${m.messageId}"><div class="d-flex align-items-start ${mine ? 'justify-content-end' : ''}">
                ${mine ? '' : `<span class="avatar me-2 flex-shrink-0"><img src="${escapeHtml(avatar(m.sender?.avatar))}" class="rounded-circle" alt=""></span>`}
                <div><div class="d-flex align-items-center ${mine ? 'justify-content-end' : ''} mb-1">${mine ? `<span class="me-2">${escapeHtml(date(m.createdAt))}</span><strong>You</strong>` : `<strong>${escapeHtml(m.sender?.fullName || 'User')}</strong><span class="ms-2">${escapeHtml(date(m.createdAt))}</span>`}</div><div class="d-flex align-items-center">${mine ? messageMenu(m, true) + box : box + messageMenu(m, false)}</div></div>
                ${mine ? `<span class="avatar ms-2 flex-shrink-0"><img src="${escapeHtml(avatar(m.sender?.avatar))}" class="rounded-circle" alt=""></span>` : ''}
            </div></div>`;
        }).join('') || '<div class="text-center text-muted py-5">No messages yet. Start the conversation.</div>';
        host.scrollTop = host.scrollHeight;
        host.querySelectorAll('[data-action]').forEach(el => el.addEventListener('click', handleMessageAction));
    }

    async function openConversation(id) {
        state.active = state.conversations.find(c => Number(c.conversationId) === id);
        if (!state.active) return;
        renderConversations($('.chat-user-nav input[placeholder="Search Keyword"]')?.value || '');
        const person = peer(state.active);
        const header = $('.chat-messages .card-header');
        $('img', header).src = avatar(person.avatar);
        $('h6', header).textContent = conversationName(state.active);
        const status = await request(`/users/${person.userId}/status`).catch(() => ({ active:false }));
        $('p', header).innerHTML = `<i class="ti ti-point-filled ${status.active ? 'text-success' : 'text-secondary'}"></i>${status.active ? 'Online' : 'Offline'}`;
        state.messages = await request(`/conversations/${id}/messages?page=0&size=50`);
        renderMessages();
        const unread = [...state.messages].reverse().find(m => Number(m.sender?.userId) !== CURRENT_USER_ID);
        if (unread) await request(`/messages/${unread.messageId}/read`, { method:'PATCH' }).catch(() => {});
    }

    async function sendMessage() {
        if (!state.active) return;
        const input = $('.message-footer input');
        const content = input.value.trim();
        if (!content && !state.attachment) return;
        const sent = await request('/messages', { method:'POST', body:JSON.stringify({ conversationId:state.active.conversationId, content, replyToMessageId:state.replyTo }) });
        if (state.attachment) {
            const body = new FormData(); body.append('file', state.attachment);
            await request(`/messages/${sent.messageId}/attachments`, { method:'POST', body });
        }
        input.value = ''; state.replyTo = null; state.attachment = null;
        await loadConversations(state.active.conversationId);
    }

    async function handleMessageAction(event) {
        event.preventDefault();
        const id = Number(event.currentTarget.dataset.id);
        const message = state.messages.find(m => Number(m.messageId) === id);
        switch (event.currentTarget.dataset.action) {
            case 'reply': state.replyTo = id; $('.message-footer input').placeholder = `Replying to #${id}`; $('.message-footer input').focus(); break;
            case 'forward': {
                const targets = state.conversations.filter(c => c.conversationId !== state.active.conversationId && !c.archived);
                const choice = Number(prompt(`Forward to:\n${targets.map((c, i) => `${i + 1}. ${conversationName(c)}`).join('\n')}`));
                if (targets[choice - 1]) await request(`/messages/${id}/forward`, { method:'POST', body:JSON.stringify({ targetConversationId:targets[choice - 1].conversationId }) });
                break;
            }
            case 'copy': await navigator.clipboard.writeText(message.content || ''); break;
            case 'favorite': await request(`/messages/${id}/favorite`, { method:'PATCH', body:JSON.stringify({ favorite:!message.favorite }) }); await openConversation(state.active.conversationId); break;
            case 'archive': await request(`/conversations/${state.active.conversationId}/archive`, { method:'PATCH', body:JSON.stringify({ archived:true }) }); state.active=null; await loadConversations(); break;
            case 'pin': await request(`/conversations/${state.active.conversationId}/pin`, { method:'PATCH', body:JSON.stringify({ pinned:!state.active.pinned }) }); await loadConversations(state.active.conversationId); break;
            case 'edit': { const content = prompt('Edit message', message.content || ''); if (content?.trim()) { await request(`/messages/${id}`, { method:'PATCH', body:JSON.stringify({ content:content.trim() }) }); await openConversation(state.active.conversationId); } break; }
            case 'delete': if (confirm('Delete this message?')) { await request(`/messages/${id}`, { method:'DELETE' }); await openConversation(state.active.conversationId); } break;
        }
    }

    async function loadConversations(preferredId) {
        state.conversations = await request('/conversations');
        renderConversations();
        const id = preferredId || state.active?.conversationId || state.conversations.find(c => !c.archived)?.conversationId;
        if (id) await openConversation(Number(id)); else { $('.message-body').innerHTML = '<div class="text-center text-muted py-5">Create a conversation to begin.</div>'; }
    }

    async function newConversation() {
        const search = prompt('Search for a user by name or email');
        if (search === null) return;
        const users = await request(`/users?search=${encodeURIComponent(search)}`);
        if (!users.length) return alert('No users found.');
        const choices = users.slice(0, 10).map((u, i) => `${i + 1}. ${u.fullName} (${u.email || ''})`).join('\n');
        const selected = Number(prompt(`Choose a user:\n${choices}`));
        const user = users[selected - 1]; if (!user) return;
        const created = await request('/conversations', { method:'POST', body:JSON.stringify({ conversationType:'DIRECT', title:null, participantUserIds:[user.userId] }) });
        await loadConversations(created.conversationId);
    }

    function bind() {
        const search = $('.chat-user-nav input[placeholder="Search Keyword"]');
        search.addEventListener('input', () => renderConversations(search.value));
        $('.chat-user-nav .btn-primary').addEventListener('click', e => { e.preventDefault(); newConversation().catch(showError); });
        const footer = $('.message-footer');
        const input = $('input', footer); const send = $('.btn-primary', footer); const attach = $('.ti-photo-plus', footer)?.closest('a');
        send.addEventListener('click', e => { e.preventDefault(); sendMessage().catch(showError); });
        input.addEventListener('keydown', e => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); sendMessage().catch(showError); } });
        if (attach) attach.addEventListener('click', e => { e.preventDefault(); const picker=document.createElement('input'); picker.type='file'; picker.onchange=()=>{ state.attachment=picker.files[0]; input.placeholder=`Attached: ${state.attachment.name}`; }; picker.click(); });
        document.querySelector('[data-bs-original-title="Refresh"]')?.addEventListener('click', e => { e.preventDefault(); loadConversations(state.active?.conversationId).catch(showError); });
    }

    function showError(error) { console.error(error); alert(error.message || 'Chat request failed.'); }
    document.addEventListener('DOMContentLoaded', () => { bind(); loadConversations().catch(showError); });
})();
