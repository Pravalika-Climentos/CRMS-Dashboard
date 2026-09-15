/* Calendar frontend integration for the Climentos CRM backend. */
(() => {
    'use strict';

    const API_ROOT = '/api';
    const CALENDAR_API = `${API_ROOT}/calendar`;
    const TEAM_API = `${API_ROOT}/teams`;
    const DEFAULT_ZONE = 'Asia/Kolkata';
    const currentUserId = new URLSearchParams(location.search).get('devUserId')
        || sessionStorage.getItem('devUserId') || '1';
    sessionStorage.setItem('devUserId', currentUserId);

    let calendar;
    let editingEvent = null;
    let currentEvent = null;
    let currentInvitation = null;
    let currentTimeChangeRequest = null;
    let selectedTeam = null;
    let categories = [];
    let externalDraggable = null;
    let teamWizard = { step: 1, userIds: [], teamIds: [], chosen: null };

    async function request(url, options = {}) {
        const response = await fetch(url, {
            ...options,
            headers: {
                'Content-Type': 'application/json',
                'X-Dev-User-Id': currentUserId,
                ...(options.headers || {})
            }
        });
        if (response.status === 204) return null;
        const body = await response.json().catch(() => null);
        if (!response.ok) {
            const error = new Error(body?.message || `Request failed (${response.status})`);
            error.status = response.status;
            error.body = body;
            throw error;
        }
        return body;
    }
    const calApi = (path, options) => request(`${CALENDAR_API}${path}`, options);
    const teamApi = (path, options) => request(`${TEAM_API}${path}`, options);
    const esc = value => $('<div>').text(value == null ? '' : String(value)).html();
    const fmt = value => value ? new Date(value).toLocaleString() : '';
    const fmtTime = value => value ? new Date(value).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';
    const modal = id => bootstrap.Modal.getOrCreateInstance(document.getElementById(id));
    const ids = selector => {
        const selected = $(selector).val() || [];
        return {
            teamIds: selected.filter(x => x.startsWith('team:')).map(x => Number(x.slice(5))),
            userIds: selected.filter(x => x.startsWith('user:')).map(x => Number(x.slice(5)))
        };
    };
    function showError(error, fallback) {
        const validation = error.body?.validationErrors;
        alert(validation ? Object.values(validation).join('\n') : (error.body?.message || fallback));
    }
    function getTodayLocalDate() {
        const now = new Date();

        const year = now.getFullYear();
        const month = String(now.getMonth() + 1).padStart(2, '0');
        const day = String(now.getDate()).padStart(2, '0');

        return `${year}-${month}-${day}`;
    }

    function isPastDate(dateValue) {
        if (!dateValue) return false;

        const selectedDate = String(dateValue).slice(0, 10);
        const today = getTodayLocalDate();

        return selectedDate < today;
    }

    function statusBadgeClass(status) {

        switch (String(status || '').toUpperCase()) {

            case 'ACCEPTED':
            case 'APPROVED':
                return 'bg-success text-white';

            case 'DECLINED':
            case 'REJECTED':
                return 'bg-danger text-white';

            case 'TENTATIVE':
                return 'bg-warning text-dark';

            case 'PENDING':
                return 'bg-primary text-white';

            case 'WITHDRAWN':
                return 'bg-secondary text-white';

            case 'CANCELLED':
            case 'CANCELED':
                return 'bg-dark text-white';

            default:
                return 'bg-secondary text-white';
        }
    }

    function initPicker($element, parent, teams = true) {
        if (!$element.length) return;
        $element.select2({
            dropdownParent: parent,
            placeholder: teams
                ? 'Search and add people or teams...'
                : 'Search and add people...',
            minimumInputLength: 0,
            ajax: {
                delay: 250,
                transport: (params, success, failure) => {
                    const search = encodeURIComponent(params.data.search || '');
                    const calls = [calApi(`/users?search=${search}&page=0&size=20`)];
                    if (teams) calls.unshift(teamApi(`?search=${search}&scope=available&page=0&size=20`));
                    Promise.all(calls).then(values => {
                        const users = teams ? values[1].items : values[0].items;
                        success({ teams: teams ? values[0].items : [], users });
                    }).catch(failure);
                },
                data: params => ({ search: params.term || '' }),
                processResults: data => ({
                    results: [
                        ...(teams ? [{ text: 'Teams', children: data.teams.map(t => ({ id: `team:${t.teamId}`, text: t.name })) }] : []),
                        { text: 'People', children: data.users.map(u => ({ id: teams ? `user:${u.userId}` : u.userId, text: u.fullName })) }
                    ]
                })
            }
        });
    }

    function getContrastTextColor(hex) {

        if (!hex) return '#ffffff';

        const color = hex.replace('#', '');

        if (color.length !== 6) {
            return '#ffffff';
        }

        const r = parseInt(color.substring(0, 2), 16);
        const g = parseInt(color.substring(2, 4), 16);
        const b = parseInt(color.substring(4, 6), 16);

        const brightness =
            (0.299 * r) +
            (0.587 * g) +
            (0.114 * b);

        return brightness > 160 ? '#111827' : '#ffffff';
    }


    function eventInput(event) {

        const categoryColor =
            event.category?.color || null;

        return {

            id: event.eventId,

            title: event.title,

            start: event.allDay
                ? event.startDate
                : event.startAt,

            end: event.allDay
                ? event.endDate
                : event.endAt,

            allDay: !!event.allDay,

            // Force normal event block
            display: 'block',

            // Category color
            backgroundColor:
                categoryColor || undefined,

            borderColor:
                categoryColor || undefined,

            // Automatically choose black/white text
            textColor:
                getContrastTextColor(categoryColor),

            extendedProps: {

                version: event.version,

                permissions: event.permissions,

                categoryId:
                    event.category?.categoryId,

                categoryName:
                    event.category?.name,

                categoryColor:
                    categoryColor
            }
        };
    }

    async function loadCategories() {
        try {
            const data = await calApi('/categories?page=0&size=100');

            categories = data.items || [];

            $('#categoryList').html(
                categories.length
                    ? categories.map(c => `
                    <div
                        class="calendar-category-item mb-2"
                        data-category-id="${c.categoryId}"
                        data-category-name="${esc(c.name)}"
                        data-category-color="${esc(c.color || '#6c757d')}"
                        style="cursor: grab;"
                    >
                        <i
                            class="ti ti-square-rounded me-2"
                            style="color:${esc(c.color || '#6c757d')}"
                        ></i>

                        <span>${esc(c.name)}</span>
                    </div>
                `).join('')
                    : '<p class="text-muted fs-12">No categories yet.</p>'
            );

            // Populate category dropdown
            $('#ev_category').html(
                '<option value="">No category</option>' +
                categories.map(c => `
                <option value="${c.categoryId}">
                    ${esc(c.name)}
                </option>
                `).join('')
            );

            $('#te_category').html(
                '<option value="">No category</option>' +
                categories.map(c => `
                <option value="${c.categoryId}">
                    ${esc(c.name)}
                </option>
            `).join('')
            );

            // IMPORTANT:
            // Make categories draggable into FullCalendar
            initializeCategoryDragging();

        } catch (error) {

            console.error('Could not load categories:', error);

            $('#categoryList').html(
                '<p class="text-muted fs-12">Categories will be available after the category backend is added.</p>'
            );

            $('#add_category')
                .find('button, input, select')
                .prop('disabled', true);
        }
    }

    function initializeCategoryDragging() {

        // Prevent creating multiple Draggable instances
        if (externalDraggable) {
            externalDraggable.destroy();
            externalDraggable = null;
        }

        const containerEl = document.getElementById('categoryList');

        if (!containerEl) {
            console.warn('Category container #categoryList not found.');
            return;
        }

        externalDraggable = new FullCalendar.Draggable(containerEl, {

            itemSelector: '.calendar-category-item',

            eventData: function (eventEl) {

                const categoryId = Number(
                    eventEl.getAttribute('data-category-id')
                );

                const categoryName =
                    eventEl.getAttribute('data-category-name') || 'New Event';

                const categoryColor =
                    eventEl.getAttribute('data-category-color') || '#6c757d';

                return {
                    title: categoryName,

                    allDay: false,

                    backgroundColor: categoryColor,
                    borderColor: categoryColor,

                    extendedProps: {
                        categoryId: categoryId,
                        categoryName: categoryName,
                        categoryColor: categoryColor
                    }
                };
            }
        });
    }
    function openEventForCategory(categoryId, categoryName) {

        resetEventForm();

        /*
         * Select the category automatically
         */
        $('#ev_category')
            .val(String(categoryId))
            .trigger('change');

        /*
         * Use category name as the default event title
         */
        if (categoryName) {
            $('#ev_title').val(categoryName);
        }

        /*
         * Open Add Event modal
         */
        modal('add_event').show();
    }
    $(document).on('click', '.calendar-category-item', function (e) {

        e.preventDefault();

        const categoryId = Number(
            $(this).attr('data-category-id')
        );

        const categoryName =
            $(this).attr('data-category-name');

        if (!categoryId) {
            console.warn('Category ID is missing.');
            return;
        }

        console.log(
            'Category clicked:',
            categoryId,
            categoryName
        );

        openEventForCategory(
            categoryId,
            categoryName
        );
    });
    function setEventDateTimeFromDrop(date) {

        if (!date) return;

        const localDate = new Date(date);

        const year = localDate.getFullYear();
        const month = String(localDate.getMonth() + 1).padStart(2, '0');
        const day = String(localDate.getDate()).padStart(2, '0');

        const hours = String(localDate.getHours()).padStart(2, '0');
        const minutes = String(localDate.getMinutes()).padStart(2, '0');

        $('#ev_date').val(`${year}-${month}-${day}`);

        /*
         * If dropped from Month view, the time will normally be 00:00.
         * Give the new event a default time.
         */
        if (hours === '00' && minutes === '00') {

            $('#ev_start_time').val('09:00');
            $('#ev_end_time').val('09:30');

        } else {

            $('#ev_start_time').val(`${hours}:${minutes}`);

            // Default duration = 30 minutes
            const endDate = new Date(
                localDate.getTime() + 30 * 60 * 1000
            );

            const endHours = String(endDate.getHours()).padStart(2, '0');
            const endMinutes = String(endDate.getMinutes()).padStart(2, '0');

            $('#ev_end_time').val(`${endHours}:${endMinutes}`);
        }
    }
    $(document).on('click', '#save_category_btn', async function () {
        try {
            const name = $('#cat_name').val().trim();
            const color = $('#cat_color').val();

            if (!name) {
                alert('Please enter a category name.');
                return;
            }

            await calApi('/categories', {
                method: 'POST',
                body: JSON.stringify({
                    name: name,
                    color: color
                })
            });

            // Refresh category list
            await loadCategories();

            // Close category modal
            modal('add_category').hide();

            // Clear form for next time
            $('#cat_name').val('');
            $('#cat_color').val('#6c757d');

        } catch (e) {
            console.error('Could not create category:', e);
            showError(e, 'Could not create category.');
        }
    });

    async function loadTeams(search = '') {
        try {
            const data = await teamApi(`?search=${encodeURIComponent(search)}&scope=available&page=0&size=20`);
            $('#teamList').html((data.items || []).map(t =>
                `<a href="#" class="team-list-item d-block border-bottom py-2" data-id="${t.teamId}"><strong>${esc(t.name)}</strong><div class="fs-12 text-muted">${t.memberCount} member(s)</div></a>`
            ).join('') || '<p class="text-muted">No teams found.</p>');
        } catch (e) { showError(e, 'Could not load teams.'); }
    }
    async function loadTeam(id) {
        try {
            const [team, members] = await Promise.all([teamApi(`/${id}`), teamApi(`/${id}/members?page=0&size=100`)]);
            selectedTeam = team;
            const can = team.permissions?.canManage;
            $('#teamDetail').html(`
                <input id="teamRenameInput" class="form-control form-control-sm mb-2" value="${esc(team.name)}" ${can ? '' : 'disabled'}>
                ${can ? '<button id="teamRenameBtn" class="btn btn-sm btn-primary me-1">Save</button><button id="teamDeleteBtn" class="btn btn-sm btn-danger">Retire</button>' : ''}
                <hr><strong>Members</strong><div class="mt-2">${(members.items || []).map(m => `<div class="d-flex justify-content-between py-1"><span>${esc(m.user.fullName)}</span>${can && m.user.userId !== team.owner.userId ? `<button class="remove-member-btn btn btn-sm btn-outline-danger" data-id="${m.user.userId}">Remove</button>` : ''}</div>`).join('')}</div>
                ${can ? '<select id="addMemberSelect" style="width:100%"></select><button id="addMemberBtn" class="btn btn-sm btn-outline-primary mt-2">Add member</button>' : ''}`);
            if (can) initPicker($('#addMemberSelect'), $('#teams_modal'), false);
        } catch (e) { showError(e, 'Could not load team.'); }
    }
    $(document).on('click', '.team-list-item', e => { e.preventDefault(); loadTeam($(e.currentTarget).data('id')); });
    $(document).on('input', '#teamSearchInput', e => loadTeams(e.target.value));
    $(document).on('click', '#createTeamBtn', async () => {
        try {
            await teamApi('', { method: 'POST', body: JSON.stringify({ name: $('#newTeamName').val().trim(), description: null, memberIds: ($('#newTeamMembers').val() || []).map(Number) }) });
            await loadTeams();
        } catch (e) { showError(e, 'Could not create team.'); }
    });
    $(document).on('click', '#teamRenameBtn', async () => {
        try { await teamApi(`/${selectedTeam.teamId}`, { method: 'PATCH', body: JSON.stringify({ expectedVersion: selectedTeam.version, name: $('#teamRenameInput').val().trim() }) }); await loadTeam(selectedTeam.teamId); await loadTeams(); }
        catch (e) { showError(e, 'Could not update team.'); }
    });
    $(document).on('click', '#teamDeleteBtn', async () => {
        if (!confirm('Retire this team?')) return;
        try { await teamApi(`/${selectedTeam.teamId}?expectedVersion=${selectedTeam.version}`, { method: 'DELETE' }); selectedTeam = null; $('#teamDetail').html('Select a team.'); await loadTeams(); }
        catch (e) { showError(e, 'Could not retire team.'); }
    });
    $(document).on('click', '#addMemberBtn', async () => {
        try { await teamApi(`/${selectedTeam.teamId}/members`, { method: 'POST', body: JSON.stringify({ expectedVersion: selectedTeam.version, userIds: [Number($('#addMemberSelect').val())] }) }); await loadTeam(selectedTeam.teamId); }
        catch (e) { showError(e, 'Could not add member.'); }
    });
    $(document).on('click', '.remove-member-btn', async e => {
        try { await teamApi(`/${selectedTeam.teamId}/members/${$(e.currentTarget).data('id')}?expectedVersion=${selectedTeam.version}`, { method: 'DELETE' }); await loadTeam(selectedTeam.teamId); }
        catch (error) { showError(error, 'Could not remove member.'); }
    });

    function resetEventForm() {
        editingEvent = null;
        $('#eventModalHeading').text('Add New Event'); $('#save_event_btn').text('Add Event');
        $('#ev_title,#ev_date,#ev_start_time,#ev_end_time,#ev_location,#ev_link').val('');
        $('#ev_category').val(''); $('#ev_invitees').val(null).trigger('change'); $('#evValidationErrors').hide();
    }
    function eventPayload() {
        const date = $('#ev_date').val();
        const start = String($('#ev_start_time').val() || '').trim().replace('.', ':');
        const end = String($('#ev_end_time').val() || '').trim().replace('.', ':');

        if (!/^\d{2}:\d{2}$/.test(start) || !/^\d{2}:\d{2}$/.test(end)) {
            throw new Error('Enter time in HH:mm format, for example 16:30.');
        }

        const startAt = zonedLocalToIso(date, start, DEFAULT_ZONE);
        const endAt = zonedLocalToIso(date, end, DEFAULT_ZONE);

        if (new Date(endAt) <= new Date(startAt)) {
            throw new Error('Event end time must be later than start time.');
        }

        const selected = ids('#ev_invitees');
        const result = {
            title: $('#ev_title').val().trim(), description: null,
            location: $('#ev_location').val().trim() || null,
            meetingUrl: $('#ev_link').val().trim() || null,
            allDay: false, startAt, endAt,
            startDate: null, endDate: null, timezone: DEFAULT_ZONE,
            visibility: 'PRIVATE', blocksTime: true,
            inviteeUserIds: selected.userIds, teamIds: selected.teamIds
        };
        if ($('#ev_category').val()) result.categoryId = Number($('#ev_category').val());
        return result;
    }
    $(document).on('click', '#save_event_btn', async () => {
        try {
            const selectedDate = $('#ev_date').val();

            if (
                !$('#ev_title').val().trim() ||
                !selectedDate ||
                !$('#ev_start_time').val() ||
                !$('#ev_end_time').val()
            ) {
                throw new Error(
                    'Fill event name, date, start and end time.'
                );
            }

            // Do not allow new events on previous dates
            if (!editingEvent && isPastDate(selectedDate)) {
                throw new Error(
                    'You cannot create an event on a previous date.'
                );
            }

            const payload = eventPayload();

            let saved;

            if (editingEvent) {

                saved = await calApi(
                    `/events/${editingEvent.eventId}`,
                    {
                        method: 'PATCH',
                        body: JSON.stringify({
                            ...payload,
                            expectedVersion: editingEvent.version,
                            inviteeUserIds: undefined,
                            teamIds: undefined
                        })
                    }
                );

            } else {

                saved = await calApi(
                    '/events',
                    {
                        method: 'POST',
                        body: JSON.stringify(payload)
                    }
                );
            }

            console.log('Event saved:', saved);

            modal('add_event').hide();

            resetEventForm();

            calendar.refetchEvents();

            loadUpcoming();

        } catch (e) {

            console.error('Could not save event:', e);

            $('#evValidationErrors')
                .text(e.body?.message || e.message || 'Could not save event.')
                .show();
        }
    });
    async function findMyInvitation(eventId) {
        const data = await calApi(
            '/invitations?status=ALL&page=0&size=100'
        );

        return (data.items || []).find(
            x => String(x.eventId) === String(eventId)
        ) || null;
    }

    async function findMyPendingTimeChangeRequest(eventId) {
        const data = await calApi(
            '/time-change-requests?scope=sent&status=PENDING&page=0&size=100'
        );

        return (data.items || []).find(
            request => String(request.eventId) === String(eventId)
        ) || null;
    }


    async function openEvent(eventId) {
        try {
            const event = await calApi(`/events/${eventId}`);

            const [participants, invitation, pendingTimeChangeRequest] = await Promise.all([
                calApi(`/events/${eventId}/participants?page=0&size=100`)
                    .catch(() => ({ items: [] })),

                findMyInvitation(eventId)
                    .catch(() => null),

                findMyPendingTimeChangeRequest(eventId)
                    .catch(() => null)
            ]);

            currentEvent = event;
            currentInvitation = invitation;
            currentTimeChangeRequest = pendingTimeChangeRequest;

            // -----------------------------
            // Event information
            // -----------------------------

            $('#eventTitle').text(event.title);

            $('#eventDateRange').text(
                event.allDay
                    ? `${event.startDate} to ${event.endDate}`
                    : new Date(event.startAt).toDateString()
            );

            $('#eventTimeRange').text(
                event.allDay
                    ? 'All day'
                    : `${fmtTime(event.startAt)} to ${fmtTime(event.endAt)}`
            );

            $('#eventLocation').text(
                event.location || 'No location set'
            );

            $('#eventLinkRow').toggle(!!event.meetingUrl);

            $('#eventLink')
                .attr('href', event.meetingUrl || '#')
                .text(event.meetingUrl || '');

            // -----------------------------
            // Participants
            // -----------------------------

            $('#eventParticipantsList').html(
                (participants.items || []).map(p => {

                    const status =
                        String(p.status || '').toUpperCase();

                    const badgeClass =
                        statusBadgeClass(status);

                    return `
                    <div class="d-flex justify-content-between align-items-center py-1">

                        <span>
                            ${esc(p.user.fullName)}

                            <span class="badge ${badgeClass} ms-1">
                                ${esc(status)}
                            </span>
                        </span>

                        ${event.permissions?.canManageParticipants &&
                            p.user.userId !== event.organizer.userId
                            ? `
                                    <button
                                        class="remove-participant-btn btn btn-sm btn-outline-danger"
                                        data-id="${p.user.userId}">
                                        Remove
                                    </button>
                                `
                            : ''
                        }

                    </div>
                `;

                }).join('')
            );

            $('#eventParticipantsSection').show();

            // -----------------------------
            // Event management buttons
            // -----------------------------

            $('#addParticipantRow').toggle(
                !!event.permissions?.canManageParticipants
            );

            $('#editEventBtn').toggle(
                !!event.permissions?.canEdit
            );

            $('#deleteEventBtn').toggle(
                !!event.permissions?.canCancel
            );

            // -----------------------------
            // Invitation actions
            // -----------------------------

            const invitationStatus =
                String(invitation?.invitationStatus || '').toUpperCase();

            const canRespond =
                !!invitation?.canRespond;

            const invitationExpired =
                invitation?.expired === true;

            const hasPendingTimeChangeRequest =
                !!pendingTimeChangeRequest;

            // Hide everything first
            $('#acceptInviteBtn').hide();
            $('#declineInviteBtn').hide();
            $('#requestTimeChangeBtn').hide();

            if (
                invitation &&
                canRespond &&
                !invitationExpired &&
                invitationStatus === 'PENDING'
            ) {
                $('#acceptInviteBtn').show();
                $('#declineInviteBtn').show();
                $('#requestTimeChangeBtn').show();
            }

            if (invitationStatus === 'ACCEPTED') {
                $('#requestTimeChangeBtn').show();
            }

            $('#requestTimeChangeBtn')
                .prop('disabled', false)
                .text('Request different time');

            if (
                hasPendingTimeChangeRequest &&
                (invitationStatus === 'PENDING' || invitationStatus === 'ACCEPTED')
            ) {
                $('#requestTimeChangeBtn')
                    .show()
                    .prop('disabled', true)
                    .text('Time change requested');
            }

            // -----------------------------
            // My response
            // -----------------------------

            $('#eventMyStatusRow').toggle(
                !!invitation
            );

            if (invitation) {

                const displayedStatus =
                    invitationStatus === 'PENDING' && invitationExpired
                        ? 'EXPIRED'
                        : invitationStatus;

                $('#eventMyStatus')
                    .text(displayedStatus)
                    .removeClass(
                        'bg-success bg-danger bg-warning bg-primary bg-secondary bg-dark text-white text-dark'
                    )
                    .addClass(
                        statusBadgeClass(displayedStatus)
                    );
            }

            modal('event_modal').show();

        } catch (e) {

            console.error(
                'Could not load event:',
                e
            );

            showError(
                e,
                'Could not load event.'
            );
        }
    }
    document.addEventListener('DOMContentLoaded', () => {

        const today = getTodayLocalDate();

        $('#ev_date').attr('min', today);
        $('#pt_date').attr('min', today);

        const element =
            document.getElementById('calendar');

        if (!element) return;

        calendar = new FullCalendar.Calendar(element, {

            initialView: 'dayGridMonth',

            droppable: true,

            headerToolbar: {
                left: 'prev,next today',
                center: 'title',
                right: 'dayGridMonth,timeGridWeek,timeGridDay'
            },

            events: async (info, success, failure) => {

                try {

                    const data = await calApi(
                        `/events?from=${encodeURIComponent(
                            info.start.toISOString()
                        )}&to=${encodeURIComponent(
                            info.end.toISOString()
                        )}`
                    );

                    success(
                        data.map(eventInput)
                    );

                } catch (e) {

                    console.error(
                        'Could not load calendar events:',
                        e
                    );

                    failure(e);
                }
            },

            eventClick: info => {

                openEvent(
                    info.event.id
                );
            },

            dateClick: info => {

                const selectedDate =
                    info.dateStr.slice(0, 10);

                if (isPastDate(selectedDate)) {

                    alert(
                        'You cannot create an event on a previous date.'
                    );

                    return;
                }

                resetEventForm();

                $('#ev_date').val(
                    selectedDate
                );

                modal('add_event').show();
            },

            eventReceive: info => {

                const droppedEvent =
                    info.event;

                const categoryId =
                    droppedEvent.extendedProps.categoryId;

                const categoryName =
                    droppedEvent.extendedProps.categoryName;

                const droppedStart =
                    droppedEvent.start;

                const droppedDate =
                    moment(droppedStart)
                        .format('YYYY-MM-DD');

                if (isPastDate(droppedDate)) {

                    alert(
                        'You cannot create an event on a previous date.'
                    );

                    droppedEvent.remove();

                    return;
                }

                droppedEvent.remove();

                resetEventForm();

                setEventDateTimeFromDrop(
                    droppedStart
                );

                if (categoryId) {

                    $('#ev_category')
                        .val(String(categoryId))
                        .trigger('change');
                }

                if (categoryName) {

                    $('#ev_title')
                        .val(categoryName);
                }

                modal('add_event').show();
            }

        });

        calendar.render();

        window.calendar = calendar;

        initPicker(
            $('#ev_invitees'),
            $('#add_event')
        );

        initPicker(
            $('#ev_add_invitees'),
            $('#event_modal')
        );

        initPicker(
            $('#te_invitees'),
            $('#team_event')
        );

        initPicker(
            $('#newTeamMembers'),
            $('#teams_modal'),
            false
        );

        loadCategories();

        loadUpcoming();

        refreshInvitationBadge();
        refreshTimeChangeBadge();
    });
    $(document).on('click', '#editEventBtn', () => {

        editingEvent = currentEvent;

        const start = new Date(currentEvent.startAt);
        const end = new Date(currentEvent.endAt);

        $('#eventModalHeading').text('Edit Event');
        $('#save_event_btn').text('Save Changes');

        // Event title
        $('#ev_title').val(currentEvent.title || '');

        // Date and time
        $('#ev_date').val(
            moment(start).format('YYYY-MM-DD')
        );

        $('#ev_start_time').val(
            moment(start).format('HH:mm')
        );

        $('#ev_end_time').val(
            moment(end).format('HH:mm')
        );

        // Location and meeting link
        $('#ev_location').val(
            currentEvent.location || ''
        );

        $('#ev_link').val(
            currentEvent.meetingUrl || ''
        );

        // IMPORTANT:
        // Restore the event's existing category
        const categoryId =
            currentEvent.category?.categoryId;

        if (categoryId) {

            $('#ev_category')
                .val(String(categoryId))
                .trigger('change');

        } else {

            $('#ev_category')
                .val('')
                .trigger('change');
        }

        console.log('Editing event category:', {
            categoryId: categoryId,
            category: currentEvent.category
        });

        modal('event_modal').hide();
        modal('add_event').show();
    });
    $(document).on('click', '#deleteEventBtn', async () => {
        if (!confirm('Cancel this event?')) return;
        try { await calApi(`/events/${currentEvent.eventId}?expectedVersion=${currentEvent.version}`, { method: 'DELETE' }); modal('event_modal').hide(); calendar.refetchEvents(); loadUpcoming(); }
        catch (e) { showError(e, 'Could not cancel event.'); }
    });
    $(document).on('click', '#addParticipantsBtn', async () => {
        const selected = ids('#ev_add_invitees');
        try { await calApi(`/events/${currentEvent.eventId}/participants`, { method: 'POST', body: JSON.stringify({ expectedVersion: currentEvent.version, userIds: selected.userIds, teamIds: selected.teamIds }) }); await openEvent(currentEvent.eventId); }
        catch (e) { showError(e, 'Could not add participants.'); }
    });
    $(document).on('click', '.remove-participant-btn', async e => {
        try { await calApi(`/events/${currentEvent.eventId}/participants/${$(e.currentTarget).data('id')}?expectedVersion=${currentEvent.version}`, { method: 'DELETE' }); await openEvent(currentEvent.eventId); }
        catch (error) { showError(error, 'Could not remove participant.'); }
    });

    async function respondInvitation(response, acknowledge = false) {
        if (!currentInvitation) return;
        try {
            await calApi(`/invitations/${currentInvitation.invitationId}/response`, { method: 'PATCH', body: JSON.stringify({ expectedVersion: currentInvitation.version, response, acknowledgeConflicts: acknowledge }) });
            refreshInvitationBadge(); calendar.refetchEvents(); await openEvent(currentInvitation.eventId);
        } catch (e) {
            if (response === 'ACCEPTED' && e.body?.code === 'CALENDAR_CONFLICT_ACKNOWLEDGEMENT_REQUIRED' && confirm(`${e.body.message}\nAccept anyway?`)) return respondInvitation(response, true);
            showError(e, 'Could not respond to invitation.');
        }
    }
    $('#acceptInviteBtn').on('click', () => respondInvitation('ACCEPTED'));
    $('#declineInviteBtn').on('click', () => respondInvitation('DECLINED'));
    async function loadInvitations() {
        try {
            const data = await calApi('/invitations?status=PENDING&page=0&size=20');
            $('#invitationsList').html((data.items || []).map(i => `<div class="d-flex justify-content-between border-bottom py-2"><div><strong>${esc(i.title)}</strong><div class="fs-12">${fmt(i.startAt || i.startDate)} · expires ${fmt(i.expiresAt)}</div></div><div><button class="quick-invite btn btn-sm btn-success" data-id="${i.invitationId}" data-version="${i.version}" data-response="ACCEPTED">Accept</button> <button class="quick-invite btn btn-sm btn-danger" data-id="${i.invitationId}" data-version="${i.version}" data-response="DECLINED">Decline</button></div></div>`).join('') || 'No pending invitations.');
        } catch (e) { showError(e, 'Could not load invitations.'); }
    }
    $(document).on('click', '.quick-invite', async e => {
        const b = $(e.currentTarget);
        try { await calApi(`/invitations/${b.data('id')}/response`, { method: 'PATCH', body: JSON.stringify({ expectedVersion: Number(b.data('version')), response: b.data('response'), acknowledgeConflicts: false }) }); loadInvitations(); refreshInvitationBadge(); calendar.refetchEvents(); }
        catch (error) { showError(error, 'Could not respond. Open the event to acknowledge conflicts.'); }
    });
    async function refreshInvitationBadge() { try { const s = await calApi('/invitations/summary'); $('#invitationBadge').text(s.pending).toggle(s.pending > 0); } catch (_) { } }

    async function refreshTimeChangeBadge() {
        try {
            const data = await calApi(
                '/time-change-requests?scope=received&status=PENDING&page=0&size=1'
            );
            const count = Number(data.totalElements || 0);
            $('#tcrBadge').text(count).toggle(count > 0);
        } catch (_) {
            $('#tcrBadge').hide();
        }
    }

    async function loadTimeRequests(scope = 'received') {
        try {
            const data = await calApi(`/time-change-requests?scope=${scope}&status=${scope === 'received' ? 'PENDING' : 'ALL'}&page=0&size=20`);
            $('#tcrList').html((data.items || []).map(r => `<div class="d-flex justify-content-between border-bottom py-2"><div><strong>${esc(r.eventTitle)}</strong><div class="fs-12">${fmt(r.proposedStartAt || r.proposedStartDate)} – ${fmtTime(r.proposedEndAt)}</div><div>${esc(r.reason)}</div></div>${scope === 'received' && r.status === 'PENDING' ? `<div><button class="tcr-decision btn btn-sm btn-success" data-id="${r.requestId}" data-rv="${r.version}" data-event="${r.eventId}" data-decision="APPROVED">Approve</button> <button class="tcr-decision btn btn-sm btn-danger" data-id="${r.requestId}" data-rv="${r.version}" data-event="${r.eventId}" data-decision="REJECTED">Reject</button></div>` : r.canWithdraw ? `<button class="tcr-withdraw btn btn-sm btn-secondary" data-id="${r.requestId}" data-version="${r.version}">Withdraw</button>` : `<span>${r.status}</span>`}</div>`).join('') || 'Nothing here.');
        } catch (e) { showError(e, 'Could not load requests.'); }
    }
    $(document).on('click', '.tcr-decision', async e => {
        const b = $(e.currentTarget);
        try { const event = await calApi(`/events/${b.data('event')}`); await calApi(`/time-change-requests/${b.data('id')}/decision`, { method: 'PATCH', body: JSON.stringify({ expectedRequestVersion: Number(b.data('rv')), expectedEventVersion: event.version, decision: b.data('decision'), decisionNote: null }) }); loadTimeRequests('received'); calendar.refetchEvents(); }
        catch (error) { showError(error, 'Could not decide request.'); }
    });
    $(document).on('click', '.tcr-withdraw', async e => { const b = $(e.currentTarget); try { await calApi(`/time-change-requests/${b.data('id')}?expectedVersion=${b.data('version')}`, { method: 'DELETE' }); loadTimeRequests('sent'); } catch (error) { showError(error, 'Could not withdraw.'); } });
    $(document).on('click', '#pt_submit', async function () {
        try {
            if (!currentEvent || !currentEvent.eventId) {
                throw new Error('Event information is not available.');
            }

            const date = $('#pt_date').val();
            const start = String($('#pt_start').val() || '').trim().replace('.', ':');
            const end = String($('#pt_end').val() || '').trim().replace('.', ':');
            const reason = $('#pt_reason').val().trim();

            if (!date || !start || !end) {
                throw new Error('Please select the proposed date, start time and end time.');
            }

            if (!/^\d{2}:\d{2}$/.test(start) || !/^\d{2}:\d{2}$/.test(end)) {
                throw new Error('Enter time in HH:mm format, for example 16:30.');
            }

            if (!reason) {
                throw new Error('Please explain why you need a different time.');
            }

            const proposedTimezone =
                currentEvent.timezone || currentEvent.timeZone || DEFAULT_ZONE;

            const startAt = zonedLocalToIso(date, start, proposedTimezone);
            const endAt = zonedLocalToIso(date, end, proposedTimezone);

            if (new Date(endAt) <= new Date(startAt)) {
                throw new Error('End time must be later than start time.');
            }

            const expectedScheduleRevision = Number(
                currentInvitation?.scheduleRevision
                    ?? currentEvent.scheduleRevision
            );

            if (!Number.isInteger(expectedScheduleRevision) || expectedScheduleRevision <= 0) {
                throw new Error('Event schedule version is unavailable. Refresh the event and try again.');
            }

            const payload = {
                expectedScheduleRevision,
                proposedAllDay: false,
                proposedStartAt: startAt,
                proposedEndAt: endAt,
                proposedStartDate: null,
                proposedEndDate: null,
                proposedTimezone,
                reason
            };

            const createdRequest = await calApi(
                `/events/${currentEvent.eventId}/time-change-requests`,
                {
                    method: 'POST',
                    body: JSON.stringify(payload)
                }
            );

            currentTimeChangeRequest = createdRequest;
            modal('propose_time_modal').hide();

            $('#requestTimeChangeBtn')
                .show()
                .prop('disabled', true)
                .text('Time change requested');

            $('#pt_date,#pt_start,#pt_end,#pt_reason').val('');

            alert('Your request for a different time has been sent to the organizer.');
            calendar.refetchEvents();
            await refreshInvitationBadge();
            await refreshTimeChangeBadge();
        } catch (error) {
            console.error('Could not request different time:', error);
            showError(error, 'Could not send the time change request.');
        }
    });
    $('#requestTimeChangeBtn').on('click', function () {

        if (currentTimeChangeRequest) {
            alert('You already have a pending time-change request for this event.');
            return;
        }

        if (!currentInvitation) {
            alert('Invitation information is unavailable.');
            return;
        }

        const invitationStatus = String(
            currentInvitation.invitationStatus || ''
        ).toUpperCase();

        if (invitationStatus !== 'PENDING' && invitationStatus !== 'ACCEPTED') {
            alert('You cannot request a different time for this invitation.');
            return;
        }

        if (
            invitationStatus === 'PENDING' &&
            (currentInvitation.expired || !currentInvitation.canRespond)
        ) {
            alert('This invitation has expired.');
            return;
        }

        $('#pt_date').val('');
        $('#pt_start').val('');
        $('#pt_end').val('');
        $('#pt_reason').val('');

        // Close the event details modal first
        const eventModalElement = document.getElementById('event_modal');
        const proposeModalElement = document.getElementById('propose_time_modal');

        const eventModal = bootstrap.Modal.getInstance(eventModalElement)
            || bootstrap.Modal.getOrCreateInstance(eventModalElement);

        const proposeModal = bootstrap.Modal.getOrCreateInstance(proposeModalElement);

        // When the first modal is completely closed,
        // open the "Request a different time" modal
        $(eventModalElement).one('hidden.bs.modal', function () {
            proposeModal.show();
        });

        eventModal.hide();
    });
    async function loadUpcoming() {
        try { const events = await calApi('/events/upcoming?limit=5'); $('#upcomingEventCount').text(events.length); $('#upcomingEventsList').html(events.map(e => `<a href="#" class="upcoming-event d-block mb-3" data-id="${e.eventId}"><strong>${esc(e.title)}</strong><div>${fmt(e.startAt || e.startDate)}</div></a>`).join('') || 'No upcoming events.'); }
        catch (e) { $('#upcomingEventsList').text('Unable to load upcoming events.'); }
    }
    $(document).on('click', '.upcoming-event', e => { e.preventDefault(); openEvent($(e.currentTarget).data('id')); });

    function getTeamDurationMinutes() {
        const selected = $('#te_duration').val();
        if (selected !== 'custom') return Number(selected);
        return (Number($('#te_duration_hours').val()) || 0) * 60
            + (Number($('#te_duration_minutes').val()) || 0);
    }

    function teamTimezone() {
        return $('#te_timezone').val() || DEFAULT_ZONE;
    }

    function formatInTeamZone(value, includeDate = true) {
        if (!value) return '';
        return new Intl.DateTimeFormat(undefined, {
            timeZone: teamTimezone(),
            ...(includeDate ? { dateStyle: 'medium' } : {}),
            timeStyle: 'short'
        }).format(new Date(value));
    }

    function selectedParticipantLabels() {
        return ($('#te_invitees').select2('data') || []).map(item => item.text);
    }

    function zonedLocalToIso(dateValue, timeValue, timeZone) {
        if (!dateValue || !/^\d{2}:\d{2}$/.test(timeValue || '')) {
            throw new Error('Enter a valid date and time.');
        }
        const [year, month, day] = dateValue.split('-').map(Number);
        const [hour, minute] = timeValue.split(':').map(Number);
        const formatter = new Intl.DateTimeFormat('en-CA', {
            timeZone, year: 'numeric', month: '2-digit', day: '2-digit',
            hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23'
        });
        let guess = Date.UTC(year, month - 1, day, hour, minute, 0);
        for (let i = 0; i < 3; i += 1) {
            const parts = Object.fromEntries(
                formatter.formatToParts(new Date(guess))
                    .filter(part => part.type !== 'literal')
                    .map(part => [part.type, Number(part.value)])
            );
            const represented = Date.UTC(
                parts.year, parts.month - 1, parts.day,
                parts.hour, parts.minute, parts.second
            );
            guess += Date.UTC(year, month - 1, day, hour, minute, 0) - represented;
        }
        return new Date(guess).toISOString();
    }

    function renderSuggestions(response) {
        const days = response.days || [];
        const total = days.reduce((count, day) => count + (day.suggestedSlots || []).length, 0);
        $('#teNoAvailability').toggle(total === 0);
        $('#teSuggestions').html(days.map(day => {
            const slots = day.suggestedSlots || [];
            if (!slots.length) return '';
            return `<div class="mb-3">
                <div class="fw-semibold mb-2">${esc(moment(day.date).format('ddd, DD MMM YYYY'))}</div>
                <div class="d-flex flex-wrap gap-2">${slots.map(slot => `
                    <button type="button" class="choose-slot btn btn-sm btn-outline-primary"
                        data-start="${esc(slot.startAt)}" data-end="${esc(slot.endAt)}">
                        ${esc(formatInTeamZone(slot.startAt, false))}–${esc(formatInTeamZone(slot.endAt, false))}
                        ${slot.tentativeConflictCount ? `<span class="badge bg-warning text-dark ms-1">${slot.tentativeConflictCount} tentative</span>` : ''}
                    </button>`).join('')}</div>
            </div>`;
        }).join(''));
    }

    function renderExactAvailability(result) {
        const participants = result.participants || [];
        const hasConflicts = participants.some(item => item.availability !== 'AVAILABLE');
        teamWizard.exactResult = result;
        teamWizard.hasConflicts = hasConflicts;
        $('#teConflictWarning').toggle(hasConflicts);
        if (!hasConflicts) $('#teConflictAcknowledged').prop('checked', false);
        $('#teParticipantAvailability').html(participants.map(item => {
            const tone = item.availability === 'BUSY' ? 'danger'
                : item.availability === 'TENTATIVE' ? 'warning' : 'success';
            const conflicts = (item.conflicts || []).map(conflict =>
                `<div class="small text-muted mt-1">${esc(conflict.title)} · ${esc(fmt(conflict.startAt || conflict.startDate))} · ${esc(conflict.status)}</div>`
            ).join('');
            return `<div class="border rounded p-2 mb-2 d-flex justify-content-between align-items-start">
                <div><strong>${esc(item.user?.fullName || 'User')}</strong>${conflicts}</div>
                <span class="badge bg-${tone}${tone === 'warning' ? ' text-dark' : ''}">${esc(item.availability)}</span>
            </div>`;
        }).join(''));
        return hasConflicts;
    }

    async function checkChosenTime(startAt, endAt) {
        const result = await calApi('/availability/exact-check', {
            method: 'POST',
            body: JSON.stringify({
                userIds: teamWizard.userIds,
                teamIds: teamWizard.teamIds,
                startAt,
                endAt,
                timezone: teamTimezone(),
                excludeEventId: null
            })
        });
        renderExactAvailability(result);
        return result;
    }

    async function loadTeamAvailability() {
        teamWizard.chosen = null;
        teamWizard.exactResult = null;
        teamWizard.hasConflicts = false;
        $('#teSelectedSlot,#teConflictWarning').hide();
        $('#teParticipantAvailability').empty();
        $('#teConflictAcknowledged').prop('checked', false);
        $('#teAvailabilityLoading').show();
        $('#teAvailabilityError,#teNoAvailability').hide();
        $('#teSuggestions').empty();
        try {
            const response = await calApi('/availability/suggestions', {
                method: 'POST',
                body: JSON.stringify({
                    userIds: teamWizard.userIds,
                    teamIds: teamWizard.teamIds,
                    fromDate: $('#te_start_date').val() || getTodayLocalDate(),
                    workingDays: 7,
                    durationMinutes: teamWizard.durationMinutes,
                    timezone: teamTimezone(),
                    workDayStart: ($('#te_work_start').val() || '09:00') + ':00',
                    workDayEnd: ($('#te_work_end').val() || '18:00') + ':00',
                    maximumSlotsPerDay: 3
                })
            });
            renderSuggestions(response);
        } catch (error) {
            $('#teAvailabilityError').text(error.body?.message || error.message || 'Could not find available times.').show();
            throw error;
        } finally {
            $('#teAvailabilityLoading').hide();
        }
    }

    function goTeamStep(step) {
        teamWizard.step = step;
        $('#teStep1,#teStep2,#teStep3,#teStep4').hide();
        $(`#teStep${step}`).show();
        $('#teBack').toggle(step > 1);
        $('#teNext').text(step === 4 ? 'Create event' : 'Next');
        const titles = ['Select people or teams', 'Event details', 'Choose a time', 'Review and create'];
        $('#teamEventTitle').text(`Step ${step}: ${titles[step - 1]}`);
        for (let i = 1; i <= 4; i += 1) {
            $(`#teProgress${i}`).toggleClass('fw-semibold text-primary', i === step);
        }
    }

    function resetTeamWizard() {
        teamWizard = { step: 1, userIds: [], teamIds: [], chosen: null, exactResult: null, hasConflicts: false, durationMinutes: 30 };
        $('#te_invitees').val(null).trigger('change');
        $('#te_title,#te_location,#te_link,#te_custom_date,#te_custom_start,#te_custom_end').val('');
        $('#te_category').val('');
        $('#te_duration').val('30');
        $('#te_duration_hours').val('0');
        $('#te_duration_minutes').val('0');
        $('#teCustomDuration,#teCustomTimePanel,#teSelectedSlot,#teConflictWarning,#teCustomAvailability').hide();
        $('#teSuggestions,#teParticipantAvailability').empty();
        $('#teConflictAcknowledged').prop('checked', false);
        $('#te_start_date').val(getTodayLocalDate());
        goTeamStep(1);
    }

    function populateTeamReview() {
        const category = $('#te_category option:selected').text().trim() || 'No category';
        $('#teReviewTitle').text($('#te_title').val().trim());
        $('#teReviewCategory').text(category);
        $('#teReviewParticipants').text(selectedParticipantLabels().join(', ') || '-');
        $('#teReviewDateTime').text(`${formatInTeamZone(teamWizard.chosen.startAt)} – ${formatInTeamZone(teamWizard.chosen.endAt)}`);
        $('#teReviewDuration').text(`${teamWizard.durationMinutes} minutes`);
        $('#teReviewTimezone').text(teamTimezone());
    }

    $(document).on('change', '#te_duration', function () {
        $('#teCustomDuration').toggle($(this).val() === 'custom');
    });

    $(document).on('click', '.choose-slot', async function () {
        teamWizard.chosen = { startAt: $(this).data('start'), endAt: $(this).data('end'), mode: 'recommended' };
        $('.choose-slot').removeClass('active btn-primary').addClass('btn-outline-primary');
        $(this).removeClass('btn-outline-primary').addClass('active btn-primary');
        $('#teSelectedSlotText').text(`${formatInTeamZone(teamWizard.chosen.startAt)} – ${formatInTeamZone(teamWizard.chosen.endAt)}`);
        $('#teSelectedSlot').show();
        $('#teParticipantAvailability').empty();
        $('#teConflictWarning').hide();
        teamWizard.exactResult = null;
    });

    $(document).on('click', '#teCustomTimeBtn', () => $('#teCustomTimePanel').slideToggle(150));

    $(document).on('click', '#teCheckCustomBtn', async () => {
        try {
            const startAt = zonedLocalToIso($('#te_custom_date').val(), $('#te_custom_start').val(), teamTimezone());
            const endAt = zonedLocalToIso($('#te_custom_date').val(), $('#te_custom_end').val(), teamTimezone());
            if (new Date(endAt) <= new Date(startAt)) throw new Error('Custom end time must be after start time.');
            const duration = (new Date(endAt) - new Date(startAt)) / 60000;
            if (duration !== teamWizard.durationMinutes) throw new Error(`Custom time must be exactly ${teamWizard.durationMinutes} minutes.`);
            const result = await checkChosenTime(startAt, endAt);
            teamWizard.chosen = { startAt, endAt, mode: 'custom' };
            $('#teCustomAvailability').removeClass('alert-danger alert-success')
                .addClass(`alert ${result.participants?.some(p => p.availability !== 'AVAILABLE') ? 'alert-warning' : 'alert-success'}`)
                .text(result.participants?.some(p => p.availability !== 'AVAILABLE')
                    ? 'This time has conflicts. You may continue after acknowledging them.' : 'Everyone is available at this time.')
                .show();
            $('#teSelectedSlotText').text(`${formatInTeamZone(startAt)} – ${formatInTeamZone(endAt)}`);
            $('#teSelectedSlot').show();
        } catch (error) {
            $('#teCustomAvailability').removeClass('alert-success alert-warning').addClass('alert alert-danger')
                .text(error.body?.message || error.message || 'Could not check this time.').show();
        }
    });

    $('#teNext').on('click', async () => {
        try {
            if (teamWizard.step === 1) {
                const selected = ids('#te_invitees');
                if (!selected.userIds.length && !selected.teamIds.length) throw new Error('Select people or teams.');
                teamWizard.userIds = selected.userIds;
                teamWizard.teamIds = selected.teamIds;
                goTeamStep(2);
                return;
            }
            if (teamWizard.step === 2) {
                if (!$('#te_title').val().trim()) throw new Error('Enter event title.');
                teamWizard.durationMinutes = getTeamDurationMinutes();
                if (teamWizard.durationMinutes < 15 || teamWizard.durationMinutes > 480 || teamWizard.durationMinutes % 15 !== 0) {
                    throw new Error('Duration must be 15–480 minutes in 15-minute increments.');
                }
                if (!$('#te_start_date').val()) $('#te_start_date').val(getTodayLocalDate());
                goTeamStep(3);
                await loadTeamAvailability();
                return;
            }
            if (teamWizard.step === 3) {
                if (!teamWizard.chosen) throw new Error('Select a recommended time or check a custom time.');
                if (!teamWizard.exactResult) await checkChosenTime(teamWizard.chosen.startAt, teamWizard.chosen.endAt);
                if (teamWizard.hasConflicts && !$('#teConflictAcknowledged').is(':checked')) {
                    throw new Error('Acknowledge the scheduling conflicts before continuing.');
                }
                populateTeamReview();
                goTeamStep(4);
                return;
            }
            const payload = {
                title: $('#te_title').val().trim(), description: null,
                location: $('#te_location').val().trim() || null,
                meetingUrl: $('#te_link').val().trim() || null,
                allDay: false, startAt: teamWizard.chosen.startAt, endAt: teamWizard.chosen.endAt,
                startDate: null, endDate: null, timezone: teamTimezone(),
                visibility: 'PRIVATE', blocksTime: true,
                inviteeUserIds: teamWizard.userIds, teamIds: teamWizard.teamIds
            };
            if ($('#te_category').val()) payload.categoryId = Number($('#te_category').val());
            await calApi('/events', { method: 'POST', body: JSON.stringify(payload) });
            modal('team_event').hide();
            calendar.refetchEvents();
            loadUpcoming();
        } catch (error) {
            showError(error, error.message || 'Team event operation failed.');
        }
    });

    $('#teBack').on('click', () => goTeamStep(Math.max(1, teamWizard.step - 1)));
    $('#team_event').on('show.bs.modal', resetTeamWizard);

    $('#teams_modal').on('shown.bs.modal', () => loadTeams());
    $('#invitations_modal').on('shown.bs.modal', loadInvitations);
    $('#tcr_modal').on('shown.bs.modal', () => loadTimeRequests('received'));
    $(document).on('click', '#tcr_modal [data-tcr-scope]', e => loadTimeRequests($(e.currentTarget).data('tcr-scope')));
    $('#refreshCalendarBtn').on('click', () => { calendar?.refetchEvents(); loadUpcoming(); refreshInvitationBadge(); refreshTimeChangeBadge(); });
    $('#add_event').on('hidden.bs.modal', () => { if (!editingEvent) resetEventForm(); });
})();

