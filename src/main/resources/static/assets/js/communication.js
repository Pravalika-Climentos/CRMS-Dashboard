(() => {
  const $ = id => document.getElementById(id);
  const normalizeEmail = value => String(value || '').trim().toLowerCase();
  // Most platforms (desktop, iOS Safari) deliver the true, unflipped camera
  // image via getUserMedia, so we CSS-mirror the self-preview for a natural
  // "looking in a mirror" feel — same as Meet/Zoom. Many Android browsers,
  // however, already deliver the front camera pre-mirrored at the OS/driver
  // level, so adding our own flip on top would un-mirror it back to the true
  // (backwards-feeling) image. Skip our flip there.
  // Whether a device's front camera arrives pre-mirrored or not varies by
  // manufacturer/browser in ways JS can't reliably detect (confirmed on
  // real devices during testing — the UA-based heuristic alone wasn't
  // trustworthy). Default to mirroring everywhere except Android as a
  // starting guess, but let each person override it with a flip button —
  // same approach Meet/Zoom use — remembered per-device so they only set
  // it once.
  const defaultMirrorGuess = !/Android/i.test(navigator.userAgent);
  function getMirrorPref() {
    const stored = localStorage.getItem('selfViewMirror');
    if (stored === 'on') return true;
    if (stored === 'off') return false;
    return defaultMirrorGuess;
  }
  function setMirrorPref(value) {
    localStorage.setItem('selfViewMirror', value ? 'on' : 'off');
  }
  const escapeHtml = value => String(value ?? '').replace(/[&<>'"]/g, c => ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', "'":'&#39;', '"':'&quot;' }[c]));
  const formatDateTime = value => value ? new Date(value).toLocaleString([], { dateStyle: 'medium', timeStyle: 'short' }) : '—';
  const formatDuration = seconds => `${String(Math.floor((seconds || 0) / 60)).padStart(2,'0')}:${String((seconds || 0) % 60).padStart(2,'0')}`;

  // Never show the template's sample counters while authenticated data loads.
  ['statConversations', 'statVideo', 'statAudio', 'statMissed'].forEach(id => {
    const element = $(id);
    if (element) element.textContent = '—';
  });

  // The dashboard stores the JWT in `access_token`.  Normalize the older
  // `accessToken` spelling too, so opening this page never discards a valid
  // dashboard session just because it was created by an earlier build.
  let storedSession;
  try {
    const saved = JSON.parse(localStorage.getItem('crmsSession') || 'null');
    if (!saved) throw new Error('AUTH_REQUIRED');
    if (!saved.access_token && saved.accessToken) saved.access_token = saved.accessToken;
    if (!saved.access_token || !saved.user) throw new Error('AUTH_REQUIRED');
    storedSession = JSON.stringify(saved);
    localStorage.setItem('crmsSession', storedSession);
  } catch (_) {
    window.location.replace('login.html');
    return;
  }

  const authModal = new bootstrap.Modal($('authModal'), { backdrop:'static', keyboard:false });
  let session = null;
  let identity = '';
  let identityUserId = '';
  let socket = null;
  let contacts = [];
  let selected = null;
  let filter = 'all';
  let webrtcConfig = null;

  let localStream = null;
  let pc = null;
  let callId = null;
  let callType = null;
  let callTargetId = null;
  let callStart = null;
  let callTimer = null;
  let ringTimeout = null;
  let pendingOffer = null;
  let pendingIceCandidates = [];

  // ---- Call recording (client-side; mixes local + remote audio, and for
  // video calls composes both video feeds onto a canvas) ----
  let mediaRecorder = null;
  let recordedChunks = [];
  let recordAudioCtx = null;
  let recordCanvas = null;
  let recordCanvasRAF = null;
  let isRecording = false;

  function buildRecordingStream(type) {
    recordAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
    const dest = recordAudioCtx.createMediaStreamDestination();
    [localStream, $('remoteVideo').srcObject].forEach(s => {
      if (!s) return;
      const audioTracks = s.getAudioTracks();
      if (audioTracks.length) {
        const src = recordAudioCtx.createMediaStreamSource(new MediaStream([audioTracks[0]]));
        src.connect(dest);
      }
    });

    if (type !== 'video') {
      return new MediaStream([...dest.stream.getAudioTracks()]);
    }

    recordCanvas = document.createElement('canvas');
    recordCanvas.width = 1280; recordCanvas.height = 720;
    const ctx = recordCanvas.getContext('2d');
    const remoteEl = $('remoteVideo');
    const localEl = $('localVideo');
    const draw = () => {
      ctx.fillStyle = '#0b0f1a';
      ctx.fillRect(0, 0, recordCanvas.width, recordCanvas.height);
      if (remoteEl.readyState >= 2) ctx.drawImage(remoteEl, 0, 0, recordCanvas.width, recordCanvas.height);
      if (localEl.readyState >= 2) ctx.drawImage(localEl, recordCanvas.width - 220, recordCanvas.height - 140, 200, 130);
      recordCanvasRAF = requestAnimationFrame(draw);
    };
    draw();
    const canvasStream = recordCanvas.captureStream(25);
    return new MediaStream([...canvasStream.getVideoTracks(), ...dest.stream.getAudioTracks()]);
  }

  function startRecording() {
    if (isRecording || !callId) return;
    try {
      const mixed = buildRecordingStream(callType);
      recordedChunks = [];
      mediaRecorder = new MediaRecorder(mixed, { mimeType: MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus') ? 'video/webm;codecs=vp8,opus' : 'audio/webm' });
      mediaRecorder.ondataavailable = e => { if (e.data && e.data.size > 0) recordedChunks.push(e.data); };
      mediaRecorder.onstop = () => uploadRecording(callId, callType);
      mediaRecorder.start(1000);
      isRecording = true;
      $('recordBtn').classList.add('active');
      toast('Recording started.');
    } catch (e) {
      console.error('Recording failed to start', e);
      toast('Could not start recording on this device/browser.', 'warning');
    }
  }

  function stopRecording() {
    if (!isRecording) return;
    isRecording = false;
    $('recordBtn').classList.remove('active');
    try { mediaRecorder?.stop(); } catch (_) {}
    if (recordCanvasRAF) cancelAnimationFrame(recordCanvasRAF);
    recordCanvasRAF = null;
    if (recordAudioCtx) { recordAudioCtx.close().catch(() => {}); recordAudioCtx = null; }
  }

  async function uploadRecording(recordingCallId, type) {
    if (!recordedChunks.length) return;
    const blob = new Blob(recordedChunks, { type: type === 'video' ? 'video/webm' : 'audio/webm' });
    recordedChunks = [];
    const link=document.createElement('a'); link.href=URL.createObjectURL(blob); link.download=`crms-call-${recordingCallId}.webm`; link.click(); URL.revokeObjectURL(link.href);
    toast('Recording downloaded to this device. Configure your own file storage if shared recording playback is required.');
  }

  // ---- Recordings panel (lists past calls that have a saved recording) ----
  let recordingsOpen = false;
  let recordingsModal = null;

  function contactNameFor(call) {
    const otherId = String(call.caller_id) === identityUserId ? String(call.callee_id) : String(call.caller_id);
    const match = contacts.find(c => String(c.id) === otherId);
    return match ? match.name : 'Unknown contact';
  }

  async function loadRecordings() {
    const list = $('recordingsList');
    list.innerHTML = '<div class="text-center text-muted small py-4">Loading recordings…</div>';
    try {
      const data = await api('/api/calls');
      const recorded = (data.calls || []).filter(c => c.recording_path);
      if (!recorded.length) {
        list.innerHTML = '<div class="text-center text-muted small py-4">No recordings yet. Use the record button during a 1:1 call to save one.</div>';
        return;
      }
      list.innerHTML = recorded.map(c => {
        const icon = c.call_type === 'video' ? '🎥' : '📞';
        return `<div class="history-item recording-item" data-path="${escapeHtml(c.recording_path)}">
          <div><strong>${icon} ${escapeHtml(contactNameFor(c))}</strong><small>${escapeHtml(formatDateTime(c.started_at))} · ${formatDuration(c.duration_seconds)}</small></div>
          <button class="btn btn-sm btn-outline-primary recording-play-btn"><i class="ti ti-player-play"></i> Play</button>
        </div>`;
      }).join('');
      list.querySelectorAll('.recording-play-btn').forEach(btn => {
        btn.onclick = () => playRecording(btn.closest('.recording-item').dataset.path);
      });
    } catch (e) {
      list.innerHTML = `<div class="text-center text-danger small py-4">Could not load recordings: ${escapeHtml(e.message)}</div>`;
    }
  }

  async function playRecording(path) {
    const player = $('recordingPlayer');
    player.style.display = 'block';
    player.removeAttribute('src');
    toast('Recordings are downloaded locally in the MySQL deployment.', 'info');
  }

  function toast(text, kind = 'dark') {
    const el = document.createElement('div');
    el.className = `alert alert-${kind} shadow-sm py-2 px-3 mb-2`;
    el.textContent = text;
    $('toastStack').appendChild(el);
    setTimeout(() => el.remove(), 3500);
  }

  function installBrowserReadiness() {
    const missing = [];
    if (!navigator.mediaDevices?.getUserMedia) missing.push('camera/microphone');
    if (!window.RTCPeerConnection) missing.push('WebRTC');
    if (!window.WebSocket) missing.push('real-time connection');
    if (missing.length) toast(`This browser cannot support meetings: ${missing.join(', ')}. Use a current Chrome, Edge, Firefox, or Safari.`, 'warning');
    if (!location.protocol.startsWith('https') && location.hostname !== 'localhost') toast('Meetings require HTTPS in production for microphone, camera, and notifications.', 'warning');
  }
  function installCookieConsent() {
    if (localStorage.getItem('crmCookieConsent')) return;
    const banner = document.createElement('div'); banner.className = 'alert alert-dark shadow-lg'; banner.style.cssText = 'position:fixed;left:20px;bottom:20px;z-index:1100;max-width:420px';
    banner.innerHTML = `We use essential storage for sign-in, meeting preferences, and notification settings. <button class="btn btn-sm btn-light ms-2">OK</button>`;
    banner.querySelector('button').onclick = () => { localStorage.setItem('crmCookieConsent', 'essential'); banner.remove(); };
    document.body.appendChild(banner);
  }
  function installMeetingSchedule() {
    const anchor = $('teamRoomBtn'); if (!anchor || $('scheduleMeetingBtn')) return;
    const button = document.createElement('button'); button.id = 'scheduleMeetingBtn'; button.className = 'btn btn-outline-primary'; button.innerHTML = '<i class="ti ti-calendar-event me-1"></i>Schedule'; anchor.after(button);
    button.onclick = () => {
      const raw = prompt('Meeting date and time (for example: 2026-09-09T15:30)'); if (!raw) return;
      const when = new Date(raw); if (Number.isNaN(when.getTime()) || when <= new Date()) return toast('Enter a future date and time.', 'warning');
      const meetings = JSON.parse(localStorage.getItem('crmMeetings') || '[]'); meetings.push({ id:crypto.randomUUID(), when:when.toISOString(), notified:false }); localStorage.setItem('crmMeetings', JSON.stringify(meetings)); toast(`Team Room scheduled for ${formatDateTime(when)}. You will be reminded five minutes before.`, 'success');
    };
    setInterval(() => { const now = Date.now(); const meetings = JSON.parse(localStorage.getItem('crmMeetings') || '[]'); let changed = false; meetings.forEach(m => { const until = new Date(m.when).getTime() - now; if (!m.notified && until <= 300000 && until > -60000) { m.notified = true; changed = true; toast(`Team Room starts at ${formatDateTime(m.when)}.`, 'info'); if (Notification.permission === 'granted') new Notification('Team Room starts soon', { body:`Starts at ${formatDateTime(m.when)}` }); } }); if (changed) localStorage.setItem('crmMeetings', JSON.stringify(meetings)); }, 30000);
  }

  // ---- Ringtone (no external audio file needed) ----
  let ringAudioCtx = null;
  let ringInterval = null;
  function startRingtone() {
    stopRingtone();
    try {
      ringAudioCtx = new (window.AudioContext || window.webkitAudioContext)();
      const ringOnce = () => {
        if (!ringAudioCtx) return;
        const osc = ringAudioCtx.createOscillator();
        const gain = ringAudioCtx.createGain();
        osc.type = 'sine';
        osc.frequency.value = 880;
        gain.gain.setValueAtTime(0.0001, ringAudioCtx.currentTime);
        gain.gain.exponentialRampToValueAtTime(0.25, ringAudioCtx.currentTime + 0.05);
        gain.gain.exponentialRampToValueAtTime(0.0001, ringAudioCtx.currentTime + 0.5);
        osc.connect(gain).connect(ringAudioCtx.destination);
        osc.start();
        osc.stop(ringAudioCtx.currentTime + 0.55);
      };
      ringOnce();
      ringInterval = setInterval(ringOnce, 1200);
    } catch (e) { console.warn('Ringtone unavailable', e); }
  }
  function stopRingtone() {
    if (ringInterval) clearInterval(ringInterval);
    ringInterval = null;
    if (ringAudioCtx) { try { ringAudioCtx.close(); } catch (_) {} ringAudioCtx = null; }
  }

  // ---- Browser/OS notifications for incoming calls ----
  function requestNotificationPermission() {
    if ('Notification' in window && Notification.permission === 'default') {
      Notification.requestPermission().catch(() => {});
    }
  }
  function notifyIncomingCall(p) {
    if (!('Notification' in window) || Notification.permission !== 'granted') return;
    try {
      const title = `Incoming ${p.type === 'video' ? 'video' : 'audio'} call`;
      const notif = new Notification(title, {
        body: p.callerName || p.from || 'Someone is calling you',
        tag: `call-${p.callId}`,
        requireInteraction: true,
        icon: 'assets/img/logo-small.png'
      });
      notif.onclick = () => {
        window.focus();
        notif.close();
      };
    } catch (e) { console.warn('Notification failed', e); }
  }

  function labelStatus(status) {
    return status === 'online' ? 'Online' : status === 'away' ? 'Away' : status === 'busy' ? 'Busy' : 'Offline';
  }

  function setIdentity(nextSession) {
    session = nextSession;
    const user = session.user;
    identityUserId = String(user.userId ?? user.id);
    identity = user.fullName || user.user_metadata?.full_name || user.email?.split('@')[0] || 'User';
    $('identityLabel').textContent = identity;
    $('identityLabelTop').textContent = `${identity} · ${user.email}`;
    $('accountEmail').textContent = user.email || '—';
    $('accountName').textContent = identity;
  }

  async function api(path, options = {}) {
    if (!session?.access_token) throw new Error('AUTH_REQUIRED');
    const headers = new Headers(options.headers || {});
    headers.set('Authorization', `Bearer ${session.access_token}`);
    if (options.body && !headers.has('Content-Type')) headers.set('Content-Type', 'application/json');
    const response = await fetch(path, { ...options, headers });
    let data = {};
    try { data = await response.json(); } catch (_) {}
    if (!response.ok) throw new Error(data.details || data.error || `HTTP_${response.status}`);
    return data;
  }

  async function ensureProfile() {
    try { await api('/api/auth/me'); } catch (e) { console.warn('Profile sync failed', e); }
  }

  // ---- Team Room (LiveKit group calling) ----
  let teamRoom = null;
  let activeTeamRoomName = null;
  let teamRecorder = null;
  let teamRecordingChunks = [];
  let teamRecordingCanvas = null;
  let teamRecordingFrame = null;
  let teamRecordingAudioContext = null;
  let teamTimer = null;
  let teamStart = null;
  let teamMuted = false;
  let teamCameraOff = false;
  let teamScreenSharing = false;

  function tileId(identity) { return `team-tile-${identity}`; }

  // Mirrors Google Meet's behaviour: as long as nobody is presenting, camera
  // tiles fill the stage in a near-square grid. The moment a screen share is
  // live, that share takes over the whole stage and every camera (including
  // your own) drops into a slim scrollable strip underneath — so a shared
  // screen is unmistakably the big thing on screen, never just another
  // same-size square next to a face.
  function updateTeamGridLayout() {
    const grid = $('teamGrid');
    const camGrid = $('teamCamGrid');
    const spotlighting = $('teamScreenArea').children.length > 0;
    grid.classList.toggle('has-screen', spotlighting);
    const count = camGrid.children.length || 1;
    if (spotlighting) {
      // In the strip, columns don't matter — tiles are fixed-width and
      // scroll horizontally — so just leave --team-cols alone.
      return;
    }
    const maxCols = window.innerWidth < 700 ? 2 : window.innerWidth < 1100 ? 3 : 5;
    const cols = Math.max(1, Math.min(maxCols, Math.ceil(Math.sqrt(count))));
    camGrid.style.setProperty('--team-cols', cols);
  }
  window.addEventListener('resize', () => { if (teamRoom) updateTeamGridLayout(); });

  function ensureTile(identity, name) {
    let tile = document.getElementById(tileId(identity));
    if (tile) return tile;
    tile = document.createElement('div');
    tile.id = tileId(identity);
    tile.className = 'team-tile';
    tile.innerHTML = `<div class="tile-avatar">${escapeHtml((name || '?').slice(0,1).toUpperCase())}</div><span class="tile-name">${escapeHtml(name || 'Participant')}</span>`;
    $('teamCamGrid').appendChild(tile);
    updateTeamGridLayout();
    return tile;
  }

  function ensureTeamStatusStyles() {
    if (document.getElementById('team-status-inline-styles')) return;
    const style = document.createElement('style');
    style.id = 'team-status-inline-styles';
    style.textContent = `
      .team-tile { position: relative; }
      .team-mic-status {
        position: absolute; left: 10px; bottom: 34px; z-index: 20;
        width: 30px; height: 30px; display: flex; align-items: center;
        justify-content: center; border-radius: 50%; background: rgba(0,0,0,.70);
        color: #fff; border: 1px solid rgba(255,255,255,.25);
        box-shadow: 0 3px 10px rgba(0,0,0,.30); font-size: 15px; pointer-events: none;
      }
      .team-mic-status.muted { background: rgba(190,35,35,.95); }
      .team-mic-status i { line-height: 1; }
    `;
    document.head.appendChild(style);
  }

  function ensureParticipantMicStatus(identity, name) {
    let tile = document.getElementById(tileId(identity));
    if (!tile) tile = ensureTile(identity, name);
    if (!tile) return null;
    let status = tile.querySelector('.team-mic-status');
    if (!status) {
      status = document.createElement('span');
      status.className = 'team-mic-status';
      status.setAttribute('aria-hidden', 'true');
      tile.appendChild(status);
    }
    return status;
  }

  function setParticipantMicStatus(identity, muted, name) {
    const status = ensureParticipantMicStatus(identity, name);
    if (!status) return;
    const isMuted = Boolean(muted);
    status.classList.toggle('muted', isMuted);
    status.innerHTML = `<i class=\"ti ti-microphone${isMuted ? '-off' : ''}\"></i>`;
    status.title = isMuted ? 'Microphone muted' : 'Microphone on';
  }

  function participantMicrophoneMuted(participant) {
    if (!participant) return true;
    const publications = participant.trackPublications
      ? Array.from(participant.trackPublications.values()) : [];
    const mic = publications.find(publication => {
      try { return publication.source === window.LivekitClient.Track.Source.Microphone; }
      catch (_) { return false; }
    });
    return mic ? Boolean(mic.isMuted) : true;
  }

  function refreshParticipantMicStatus(participant) {
    if (!participant) return;
    setParticipantMicStatus(participant.identity, participantMicrophoneMuted(participant), participant.name || participant.identity);
  }

  function removeTile(identity) {
    document.getElementById(tileId(identity))?.remove();
    updateTeamGridLayout();
  }

  function screenTileId(identity) { return `team-screen-${identity}`; }

  function ensureScreenTile(identity, name) {
    let tile = document.getElementById(screenTileId(identity));
    if (tile) return tile;

    tile = document.createElement('div');
    tile.id = screenTileId(identity);
    tile.className = 'team-tile screen-tile';

    const videoEl = document.createElement('video');
    videoEl.autoplay = true;
    videoEl.playsInline = true;
    videoEl.muted = true;
    videoEl.controls = false;
    videoEl.setAttribute('disablePictureInPicture', '');
    videoEl.setAttribute('aria-label', `${name || 'Participant'}'s shared screen`);

    const label = document.createElement('span');
    label.className = 'tile-name';
    label.innerHTML = `<i class="ti ti-screen-share me-1"></i>${escapeHtml(name || 'Participant')}'s screen`;

    tile.append(videoEl, label);

    // Keep exactly one presenter in the spotlight, but NEVER recreate the
    // existing presenter's video element. Recreating/detaching the element
    // can make a WebRTC presentation look frozen at its first frame.
    const area = $('teamScreenArea');
    const current = area.querySelector('.screen-tile');
    if (current && current.id !== tile.id) current.remove();
    if (!tile.isConnected) area.appendChild(tile);

    updateTeamGridLayout();
    return tile;
  }

  function removeScreenTile(identity) {
    document.getElementById(screenTileId(identity))?.remove();
    updateTeamGridLayout();
  }

  function attachTrack(identity, name, track) {
    const tile = ensureTile(identity, name);
    if (track.kind === 'video') {
      tile.querySelector('.tile-avatar')?.remove();
      let videoEl = tile.querySelector('video');
      if (!videoEl) {
        videoEl = document.createElement('video');
        videoEl.autoplay = true;
        videoEl.playsInline = true;
        tile.prepend(videoEl);
      }
      track.attach(videoEl);
    } else if (track.kind === 'audio') {
      let audioEl = tile.querySelector('audio');
      if (!audioEl) {
        audioEl = document.createElement('audio');
        audioEl.autoplay = true;
        audioEl.playsInline = true;
        tile.appendChild(audioEl);
      }
      track.attach(audioEl);
      // Attaching a remote track after an async room join is not always
      // considered an autoplay-eligible action (notably in Safari and some
      // Chromium configurations). Request playback explicitly as well.
      audioEl.play().catch(error => console.warn('Remote Team Room audio is waiting for playback permission.', error));
    }
  }

  function showTeamAudioUnlockButton() {
    if ($('teamEnableAudio') || !teamRoom) return;
    const button = document.createElement('button');
    button.id = 'teamEnableAudio';
    button.type = 'button';
    button.className = 'btn btn-light';
    button.textContent = 'Enable audio';
    button.onclick = () => {
      // This is deliberately invoked directly by a click. Some browsers
      // reject audio playback unless startAudio is called from that gesture.
      teamRoom?.startAudio().then(() => button.remove()).catch(error => {
        console.warn('Could not enable Team Room audio.', error);
        toast('Browser audio is still blocked. Check the tab and site sound permissions.', 'warning');
      });
    };
    $('teamStage').querySelector('.call-controls')?.prepend(button);
  }

  async function joinTeamRoom() {
    if (teamRoom) return;
    const requestedName = prompt('Enter a Team Room name. Everyone joining the same name joins the same private meeting.', localStorage.getItem('crmsLastTeamRoom') || 'crm-team-room');
    if (requestedName === null) return;
    const cleanedName = requestedName.trim().toLowerCase().replace(/[^a-z0-9_-]/g, '-').replace(/-+/g, '-').slice(0, 64);
    if (!cleanedName) return toast('Enter a Team Room name using letters, numbers, hyphens, or underscores.', 'warning');
    activeTeamRoomName = cleanedName;
    localStorage.setItem('crmsLastTeamRoom', cleanedName);
    buildTeamCollaborationPanel();
    ensureTeamStatusStyles();
    if (!window.LivekitClient) return toast('Group calling library failed to load.', 'warning');
    try {
      const { Room, RoomEvent, Track } = window.LivekitClient;
      // Screen sharing must not be allowed to downshift/pause because the
      // spotlight element changes size/visibility. Keep the room's video
      // forwarding path stable; this is much more reliable for presentations.
      teamRoom = new Room({
        adaptiveStream: false,
        dynacast: false,
        audioCaptureDefaults: {
          autoGainControl: true,
          echoCancellation: true,
          noiseSuppression: true
        }
      });

      // LiveKit must unlock remote-audio playback while this click handler
      // still has the browser's user-gesture permission. Without this,
      // participants can join and see video but hear no microphone audio.
      // Older SDKs do not expose startAudio, so keep them compatible.
      const unlockAudio = teamRoom.startAudio?.();

      // Do this only after requesting playback activation. Awaiting a network
      // request first would lose the click's user-activation context.
      const data = await api('/api/livekit/token', { method: 'POST', body: JSON.stringify({ room: activeTeamRoomName }) });
      activeTeamRoomName = data.room;

      teamRoom.on(RoomEvent.ParticipantConnected, p => {
        ensureTile(p.identity, p.name);
        refreshParticipantMicStatus(p);
      });
      if (RoomEvent.ActiveSpeakersChanged) teamRoom.on(RoomEvent.ActiveSpeakersChanged, speakers => {
        document.querySelectorAll('.team-tile.speaking').forEach(tile => tile.classList.remove('speaking'));
        speakers.forEach(person => document.getElementById(tileId(person.identity))?.classList.add('speaking'));
      });
      teamRoom.on(RoomEvent.ParticipantDisconnected, p => { removeTile(p.identity); removeScreenTile(p.identity); });
      if (RoomEvent.TrackMuted) teamRoom.on(RoomEvent.TrackMuted, (pub, participant) => {
        if (pub?.source === Track.Source.Microphone && participant) refreshParticipantMicStatus(participant);
      });
      if (RoomEvent.TrackUnmuted) teamRoom.on(RoomEvent.TrackUnmuted, (pub, participant) => {
        if (pub?.source === Track.Source.Microphone && participant) refreshParticipantMicStatus(participant);
      });
      teamRoom.on(RoomEvent.TrackSubscribed, (track, pub, participant) => {
        if (pub.source === Track.Source.ScreenShare) {
          const videoEl = ensureScreenTile(participant.identity, participant.name).querySelector('video');
          track.attach(videoEl);
          videoEl.play().catch(() => {});
          videoEl.onloadedmetadata = () => videoEl.play().catch(() => {});
        } else {
          attachTrack(participant.identity, participant.name, track);
        }
      });
      teamRoom.on(RoomEvent.TrackUnsubscribed, (track, pub, participant) => {
        track.detach().forEach(el => el.remove());
        if (pub.source === Track.Source.ScreenShare) removeScreenTile(participant.identity);
      });
      teamRoom.on(RoomEvent.LocalTrackPublished, pub => {
        if (pub.track && pub.source === Track.Source.ScreenShare) {
          const videoEl = ensureScreenTile(
            teamRoom.localParticipant.identity,
            `${teamRoom.localParticipant.name || 'You'} (You)`
          ).querySelector('video');
          pub.track.attach(videoEl);
          videoEl.play().catch(() => {});
          videoEl.onloadedmetadata = () => videoEl.play().catch(() => {});
        }
      });
      teamRoom.on(RoomEvent.LocalTrackUnpublished, pub => {
        if (pub.source === Track.Source.ScreenShare) {
          removeScreenTile(teamRoom.localParticipant.identity);
          teamScreenSharing = false;
          $('teamScreenBtn')?.classList.remove('active');
        }
      });
      teamRoom.on(RoomEvent.Disconnected, () => leaveTeamRoom(true));
      if (RoomEvent.AudioPlaybackStatusChanged) {
        teamRoom.on(RoomEvent.AudioPlaybackStatusChanged, () => {
          if (teamRoom && !teamRoom.canPlaybackAudio) showTeamAudioUnlockButton();
          else $('teamEnableAudio')?.remove();
        });
      }

      await teamRoom.connect(data.url, data.token);
      await unlockAudio?.catch(error => console.warn('Team Room audio unlock was deferred.', error));

      // Local tile — marked '.local' so CSS mirrors only this tile's video,
      // matching how Meet/Zoom mirror your own preview but send/show the
      // real (unmirrored) feed to everyone else.
      const localTile = ensureTile(teamRoom.localParticipant.identity, `${teamRoom.localParticipant.name || 'You'} (You)`);
      localTile.classList.add('local');
      localTile.classList.toggle('mirror-flip', getMirrorPref());
      if (!localTile.querySelector('.mirror-toggle-btn')) {
        const flipBtn = document.createElement('button');
        flipBtn.type = 'button';
        flipBtn.className = 'mirror-toggle-btn';
        flipBtn.title = 'Flip my view';
        flipBtn.innerHTML = '<i class="ti ti-arrows-horizontal"></i>';
        flipBtn.onclick = e => {
          e.stopPropagation();
          const next = !localTile.classList.contains('mirror-flip');
          localTile.classList.toggle('mirror-flip', next);
          setMirrorPref(next);
        };
        localTile.appendChild(flipBtn);
      }
      await teamRoom.localParticipant.setMicrophoneEnabled(true);
      teamMuted = false;
      setParticipantMicStatus(teamRoom.localParticipant.identity, false, `${teamRoom.localParticipant.name || 'You'} (You)`);
      await teamRoom.localParticipant.setCameraEnabled(true);
      teamRoom.localParticipant.videoTrackPublications.forEach(pub => {
        if (pub.track) attachTrack(teamRoom.localParticipant.identity, `${teamRoom.localParticipant.name || 'You'} (You)`, pub.track);
      });

      // Existing participants already in the room when we joined
      teamRoom.remoteParticipants.forEach(p => {
        ensureTile(p.identity, p.name);
        refreshParticipantMicStatus(p);
        p.trackPublications.forEach(pub => {
          if (!pub.track) return;
          if (pub.source === Track.Source.ScreenShare) {
            const videoEl = ensureScreenTile(p.identity, p.name).querySelector('video');
            pub.track.attach(videoEl);
            videoEl.play().catch(() => {});
            videoEl.onloadedmetadata = () => videoEl.play().catch(() => {});
          } else {
            attachTrack(p.identity, p.name, pub.track);
          }
        });
      });

      $('teamStage').classList.add('active');
      $('teamSubtitle').textContent = `${teamRoom.numParticipants} in the room`;
      teamStart = Date.now();
      clearInterval(teamTimer);
      teamTimer = setInterval(() => { $('teamTimer').textContent = formatDuration(Math.floor((Date.now() - teamStart) / 1000)); }, 1000);
      socket?.emit('team:join', { room: activeTeamRoomName });
      try {
        const savedNotes = await api(`/api/team-rooms/${encodeURIComponent(activeTeamRoomName)}/notes`);
        if ($('teamNotesInput')) $('teamNotesInput').value = savedNotes.notes || '';
      } catch (error) { console.warn('Could not load saved meeting notes', error); }
      toast(`Joined Team Room: ${activeTeamRoomName}`);
    } catch (e) {
      console.error('Team room join failed', e);
      teamRoom = null;
      const micErrors = {
        NotAllowedError: 'Microphone/camera permission was blocked. Check the site permissions in your browser (and OS-level mic permission), then try again.',
        NotFoundError: 'No microphone or camera was found on this device.',
        NotReadableError: 'Your microphone or camera is already being used by another app or tab.',
        OverconstrainedError: 'Your microphone/camera does not support the requested settings.'
      };
      const message = e.message === 'GROUP_CALLING_NOT_CONFIGURED' ? 'Group calling is not set up yet on the server.'
        : e.message === 'TEAM_ROOM_FULL' ? 'The Team Room is currently full. Please wait for someone to leave and try again.'
        : micErrors[e.name] || 'Could not join the team room.';
      toast(message, 'warning');
    }
  }

  function leaveTeamRoom(silent) {
    stopTeamRecording();
    if (teamRoom) { try { teamRoom.disconnect(); } catch (_) {} }
    teamRoom = null;
    $('teamCamGrid').innerHTML = '';
    $('teamScreenArea').innerHTML = '';
    $('teamEnableAudio')?.remove();
    $('teamGrid').classList.remove('has-screen');
    $('teamStage').classList.remove('active');
    clearInterval(teamTimer);
    teamTimer = null;
    teamMuted = false;
    teamCameraOff = false;
    teamScreenSharing = false;
    $('teamMuteBtn')?.classList.remove('active');
    $('teamMuteBtn')?.setAttribute('title', 'Mute');
    $('teamMuteBtn')?.setAttribute('aria-label', 'Mute');
    $('teamMuteBtn')?.replaceChildren(Object.assign(document.createElement('i'), { className: 'ti ti-microphone' }));
    $('teamCameraBtn')?.classList.remove('active');
    $('teamScreenBtn')?.classList.remove('active');
    if (activeTeamRoomName) socket?.emit('team:leave', { room:activeTeamRoomName });
    activeTeamRoomName = null;
    if (!silent) toast('Left the team room.');
  }

  // ---- Team Room badge: shows who's in the room right now, even before
  // you join, and pops a toast when someone joins/leaves while you're away ----
  let lastTeamMemberIds = new Set();
  function renderTeamBadge(members) {
    const btn = $('teamRoomBtn');
    if (!btn) return;
    const count = members.length;
    const isInRoom = teamRoom && !teamRoom.disconnected;
    let badge = btn.querySelector('.team-badge');
    if (count > 0) {
      if (!badge) {
        badge = document.createElement('span');
        badge.className = 'team-badge';
        btn.appendChild(badge);
      }
      badge.textContent = count;
      btn.title = `${count} in the Team Room: ${members.map(m => m.name).join(', ')}`;
    } else if (badge) {
      badge.remove();
      btn.title = '';
    }

    // Notify about joins/leaves that happened while this tab wasn't the one changing state
    const currentIds = new Set(members.map(m => m.userId));
    if (!isInRoom) {
      for (const m of members) {
        if (!lastTeamMemberIds.has(m.userId) && m.userId !== identityUserId) {
          toast(`${m.name} joined the Team Room.`);
        }
      }
    }
    lastTeamMemberIds = currentIds;
  }

  let teamChatUnread = 0;
  function updateTeamUnread() { const badge = $('teamChatUnread'); if (badge) { badge.hidden = teamChatUnread === 0; badge.textContent = teamChatUnread; } }
  function drawTeamRecordingFrame() {
    if (!teamRecordingCanvas) return;
    const ctx = teamRecordingCanvas.getContext('2d'); const videos = [...$('teamGrid').querySelectorAll('video')].filter(v => v.readyState >= 2);
    ctx.fillStyle = '#0b1020'; ctx.fillRect(0, 0, teamRecordingCanvas.width, teamRecordingCanvas.height);
    const cols = Math.max(1, Math.ceil(Math.sqrt(Math.max(1, videos.length)))); const rows = Math.max(1, Math.ceil(videos.length / cols)); const w = teamRecordingCanvas.width / cols; const h = teamRecordingCanvas.height / rows;
    videos.forEach((video, index) => { const x = (index % cols) * w; const y = Math.floor(index / cols) * h; ctx.drawImage(video, x, y, w, h); });
    teamRecordingFrame = requestAnimationFrame(drawTeamRecordingFrame);
  }
  function startTeamRecording() {
    if (!teamRoom || teamRecorder) return;
    try {
      teamRecordingCanvas = document.createElement('canvas'); teamRecordingCanvas.width = 1280; teamRecordingCanvas.height = 720; drawTeamRecordingFrame();
      teamRecordingAudioContext = new (window.AudioContext || window.webkitAudioContext)(); const destination = teamRecordingAudioContext.createMediaStreamDestination();
      $('teamGrid').querySelectorAll('video,audio').forEach(element => { const stream = element.srcObject; if (!stream?.getAudioTracks?.().length) return; try { teamRecordingAudioContext.createMediaStreamSource(stream).connect(destination); } catch (_) {} });
      const stream = new MediaStream([...teamRecordingCanvas.captureStream(20).getVideoTracks(), ...destination.stream.getAudioTracks()]);
      teamRecordingChunks = []; teamRecorder = new MediaRecorder(stream, { mimeType:MediaRecorder.isTypeSupported('video/webm;codecs=vp8,opus') ? 'video/webm;codecs=vp8,opus' : 'video/webm' });
      teamRecorder.ondataavailable = event => { if (event.data.size) teamRecordingChunks.push(event.data); };
      teamRecorder.onstop = () => { const blob = new Blob(teamRecordingChunks, {type:'video/webm'}); const link = document.createElement('a'); link.href = URL.createObjectURL(blob); link.download = `team-room-${new Date().toISOString().replace(/[:.]/g,'-')}.webm`; link.click(); setTimeout(() => URL.revokeObjectURL(link.href), 1000); };
      teamRecorder.start(1000); $('teamRecordBtn').classList.add('active'); $('teamRecordBtn').title = 'Stop and download recording'; toast('Meeting recording started. Keep this tab open until you stop it.', 'success');
    } catch (error) { console.error('Team recording failed', error); toast('Recording is not supported by this browser.', 'warning'); }
  }
  function stopTeamRecording() { if (!teamRecorder) return; try { teamRecorder.stop(); } catch (_) {} teamRecorder = null; if (teamRecordingFrame) cancelAnimationFrame(teamRecordingFrame); teamRecordingFrame = null; teamRecordingCanvas = null; teamRecordingAudioContext?.close(); teamRecordingAudioContext = null; $('teamRecordBtn')?.classList.remove('active'); toast('Recording downloaded to this device.', 'success'); }
  function buildTeamCollaborationPanel() {
    if ($('teamSidepanel')) return;
    const grid = $('teamGrid'); const layout = document.createElement('div'); layout.className = 'team-layout';
    const panel = document.createElement('aside'); panel.id = 'teamSidepanel'; panel.className = 'team-sidepanel';
    panel.innerHTML = `<div class="team-panel-tabs"><button type="button" class="team-panel-tab active" data-team-panel="chat"><i class="ti ti-message-circle"></i> Chat <span id="teamChatUnread" class="team-unread" hidden>0</span></button><button type="button" class="team-panel-tab" data-team-panel="notes"><i class="ti ti-notes"></i> Notes</button></div><section id="teamChatPanel" class="team-panel-content"><div id="teamChatMessages" class="team-chat-messages" aria-live="polite"></div><div class="team-composer"><input id="teamChatInput" maxlength="1000" autocomplete="off" placeholder="Message everyone"><button type="button" id="teamChatSend" aria-label="Send message"><i class="ti ti-send"></i></button></div></section><section id="teamNotesPanel" class="team-panel-content" hidden><p class="team-panel-help">Shared minutes update live for everyone in this room.</p><textarea id="teamNotesInput" maxlength="10000" placeholder="Agenda, decisions, action items…"></textarea><button type="button" id="teamNotesSave" class="btn btn-sm btn-primary w-100 mt-2">Save shared notes</button><button type="button" id="teamNotesSummary" class="btn btn-sm btn-outline-light w-100 mt-2">Format minutes summary</button></section>`;
    grid.parentNode.insertBefore(layout, grid); layout.append(grid, panel);
    panel.querySelectorAll('[data-team-panel]').forEach(button => button.onclick = () => { panel.querySelectorAll('[data-team-panel]').forEach(x => x.classList.toggle('active', x === button)); $('teamChatPanel').hidden = button.dataset.teamPanel !== 'chat'; $('teamNotesPanel').hidden = button.dataset.teamPanel !== 'notes'; if (button.dataset.teamPanel === 'chat') { teamChatUnread = 0; updateTeamUnread(); } });
    const send = () => { const input = $('teamChatInput'); const text = input.value.trim(); if (!text) return; if (!socket?.connected || !teamRoom || !activeTeamRoomName) return toast('Join the Team Room before sending a message.', 'warning'); socket.timeout(5000).emit('team:chat', { room:activeTeamRoomName, text }, (error, result) => { if (error || !result?.ok) return toast('Could not send the Team Room message.', 'warning'); input.value = ''; }); };
    $('teamChatSend').onclick = send; $('teamChatInput').onkeydown = e => { if (e.key === 'Enter') send(); };
    $('teamNotesSave').onclick = async () => { if (!teamRoom || !activeTeamRoomName) return toast('Join a Team Room before saving notes.', 'warning'); try { const notes=$('teamNotesInput').value; await api(`/api/team-rooms/${encodeURIComponent(activeTeamRoomName)}/notes`, { method:'PUT', body:JSON.stringify({notes}) }); socket?.emit('team:notes', { room:activeTeamRoomName, notes }); toast('Shared meeting notes saved.'); } catch (error) { console.error('Could not save meeting notes', error); toast('Could not save meeting notes.', 'warning'); } };
    $('teamNotesSummary').onclick = () => { const field = $('teamNotesInput'); const lines = field.value.split('\n').map(x => x.trim()).filter(Boolean); field.value = lines.length ? `Meeting minutes\n\nDecisions / updates\n${lines.map(x => `• ${x}`).join('\n')}\n\nAction items\n• Owner: ______  Due: ______` : 'Meeting minutes\n\nDecisions / updates\n• \n\nAction items\n• Owner: ______  Due: ______'; };
    const record = document.createElement('button'); record.id = 'teamRecordBtn'; record.type = 'button'; record.className = 'call-control'; record.title = 'Record meeting to this device'; record.innerHTML = '<i class="ti ti-player-record"></i>'; record.onclick = () => teamRecorder ? stopTeamRecording() : startTeamRecording(); $('teamStage').querySelector('.call-controls').insertBefore(record, $('teamLeaveBtn'));
  }
  function appendTeamMessage(message) { const area = $('teamChatMessages'); if (!area) return; const line = document.createElement('div'); line.className = 'team-message'; line.innerHTML = `<strong>${escapeHtml(message.name || 'Participant')} <time>${escapeHtml(new Date(message.createdAt).toLocaleTimeString([], {hour:'2-digit',minute:'2-digit'}))}</time></strong>${escapeHtml(message.text)}`; area.appendChild(line); area.scrollTop = area.scrollHeight; if ($('teamChatPanel').hidden) { teamChatUnread++; updateTeamUnread(); } }
  $('teamRoomBtn').onclick = joinTeamRoom;

  // Recordings panel is optional — only wire it up if calls.html actually
  // contains the #recordingsModal markup. Without this guard, a missing
  // element would throw here and silently stop every handler below it
  // (including sign-in) from ever being registered.
  if ($('recordingsModal') && $('recordingsBtn')) {
    recordingsModal = new bootstrap.Modal($('recordingsModal'));
    $('recordingsBtn').onclick = () => {
      recordingsOpen = true;
      recordingsModal.show();
      loadRecordings().catch(() => {});
    };
    $('recordingsModal').addEventListener('hidden.bs.modal', () => {
      recordingsOpen = false;
      const player = $('recordingPlayer');
      player.pause();
      player.removeAttribute('src');
      player.style.display = 'none';
    });
  }
  $('teamLeaveBtn').onclick = () => leaveTeamRoom(false);
  $('teamMuteBtn').onclick = async () => {
    if (!teamRoom) {
      toast('You are not connected to the Team Room.', 'warning');
      return;
    }
    try {
      const localParticipant = teamRoom.localParticipant;
      const publications = Array.from(localParticipant.trackPublications.values());
      const microphonePublication = publications.find(pub => pub.source === LivekitClient.Track.Source.Microphone);
      const currentlyMuted = microphonePublication ? Boolean(microphonePublication.isMuted) : teamMuted;
      const shouldMute = !currentlyMuted;
      await localParticipant.setMicrophoneEnabled(!shouldMute);
      teamMuted = shouldMute;
      const btn = $('teamMuteBtn');
      btn.classList.toggle('active', teamMuted);
      btn.title = teamMuted ? 'Unmute' : 'Mute';
      btn.setAttribute('aria-label', btn.title);
      btn.innerHTML = `<i class=\"ti ti-microphone${teamMuted ? '-off' : ''}\"></i>`;
      setParticipantMicStatus(localParticipant.identity, teamMuted, `${localParticipant.name || 'You'} (You)`);
    } catch (e) {
      console.error('[Team Room microphone]', e);
      toast('Could not change microphone state.', 'warning');
    }
  };
  $('teamCameraBtn').onclick = async () => {
    if (!teamRoom) return;
    teamCameraOff = !teamCameraOff;
    await teamRoom.localParticipant.setCameraEnabled(!teamCameraOff);
    $('teamCameraBtn').classList.toggle('active', teamCameraOff);
  };
  $('teamScreenBtn').onclick = async () => {
    if (!teamRoom) return;
    try {
      teamScreenSharing = !teamScreenSharing;
      if (teamScreenSharing) toast('Tip: don\'t share this browser tab/window itself — that causes a recursive mirror effect.', 'info');
      // setScreenShareEnabled handles the browser's getDisplayMedia prompt,
      // publishing, AND auto-unpublishing when the person clicks the
      // browser's own native "Stop sharing" bar — no extra wiring needed
      // for that case since LocalTrackUnpublished (above) resets our UI too.
      await teamRoom.localParticipant.setScreenShareEnabled(teamScreenSharing, {
        audio: true,
        contentHint: 'detail',
        resolution: { width: 1920, height: 1080, frameRate: 15 }
      });
      $('teamScreenBtn').classList.toggle('active', teamScreenSharing);
    } catch (e) {
      teamScreenSharing = false;
      $('teamScreenBtn').classList.remove('active');
      if (e?.name !== 'NotAllowedError') toast('Could not start screen sharing.', 'warning');
    }
  };

  async function loadContacts() {
    const data = await api('/api/calls/users');
    contacts = (data.contacts || []).map(c => ({ ...c, id:String(c.id), status:c.status || 'offline' }));
    renderContacts();
    if (selected) {
      const updated = contacts.find(c => c.id === selected.id);
      if (updated) selectCustomer(updated.id, false);
    }
  }

  async function loadWebRtcConfig() {
    const data = await api('/api/webrtc/config');
    webrtcConfig = data.iceServers;
  }

  function renderContacts() {
    const list = $('contactList');
    const filtered = contacts.filter(c => filter === 'all' || c.status === 'online');
    if (!filtered.length) {
      list.innerHTML = '<div class="p-4 text-center text-muted small">No contacts yet. Use “New conversation” to add a customer by email.</div>';
    } else {
      list.innerHTML = filtered.map(c => `
        <div class="contact-item ${selected?.id === c.id ? 'active' : ''}" data-id="${escapeHtml(c.id)}" data-email="${escapeHtml(c.email || '')}">
          <div class="avatar-wrap"><img src="${escapeHtml(c.avatar || 'assets/img/profiles/avatar-01.jpg')}" alt=""><span class="presence ${escapeHtml(c.status || 'offline')}"></span></div>
          <div class="contact-main">
            <div class="contact-row"><span class="contact-name">${escapeHtml(c.name)}</span><span class="contact-time">${escapeHtml(c.time ? new Date(c.time).toLocaleDateString() : '')}</span></div>
            <div class="contact-preview">${escapeHtml(c.preview || `Email · ${c.email || '—'}`)}</div>
          </div>
        </div>`).join('');
      list.querySelectorAll('.contact-item').forEach(el => el.onclick = () => selectCustomer(el.dataset.id));
    }
    $('onlineCount').textContent = `${contacts.filter(c => c.status === 'online').length} online`;
  }

  function selectCustomer(id, refreshUi = true) {
    selected = contacts.find(c => c.id === id) || selected;
    if (!selected) return;
    $('centerEmpty').style.display = 'none';
    $('conversation').style.display = 'flex';
    $('personName').textContent = selected.name;
    $('personStatus').textContent = labelStatus(selected.status);
    $('personAvatar').src = selected.avatar || 'assets/img/profiles/avatar-01.jpg';
    $('personPresence').className = `presence ${selected.status || 'offline'}`;
    $('detailName').textContent = selected.name;
    $('detailAvatar').src = selected.avatar || 'assets/img/profiles/avatar-01.jpg';
    $('detailId').textContent = selected.email || '—';
    $('detailLast').textContent = selected.time ? formatDateTime(selected.time) : 'No previous contact';
    $('detailPreferred').textContent = selected.preferred || 'Chat';
    $('detailStatus').innerHTML = `<span>●</span> ${labelStatus(selected.status)}`;
    if (refreshUi) renderContacts();
    loadChat().catch(e => toast(`Could not load chat history: ${e.message}`, 'warning'));
    loadHistory().catch(e => toast(`Could not load call history: ${e.message}`, 'warning'));
  }

  async function loadChat() {
    if (!selected) return;
    $('chatArea').innerHTML = '<div class="day-label">Conversation</div><div class="text-center text-muted small py-4">Loading messages…</div>';
    const conversation = await directConversation(false);
    $('chatArea').innerHTML = '<div class="day-label">Conversation</div>';
    if (!conversation) {
      const empty = document.createElement('div');
      empty.className = 'text-center text-muted small py-5';
      empty.textContent = 'No messages yet. Start the conversation below.';
      $('chatArea').appendChild(empty);
    } else {
      const messages = await api(`/api/chat/conversations/${conversation.conversationId}/messages?page=0&size=200`);
      messages.forEach(m => appendMessage({
        text: m.content,
        time: new Date(m.createdAt).toLocaleTimeString([], { hour:'2-digit', minute:'2-digit' }),
        mine: String(m.sender?.userId) === identityUserId,
        senderName: String(m.sender?.userId) === identityUserId ? 'You' : (m.sender?.fullName || selected.name)
      }));
    }
  }

  async function directConversation(create) {
    const conversations = await api('/api/chat/conversations');
    let conversation = conversations.find(c =>
      String(c.conversationType).toUpperCase() === 'DIRECT' &&
      (c.participants || []).some(p => String(p.userId) === String(selected.id))
    );
    if (!conversation && create) {
      conversation = await api('/api/chat/conversations', {
        method:'POST', body:JSON.stringify({conversationType:'DIRECT', participantUserIds:[Number(selected.id)]})
      });
    }
    return conversation;
  }

  async function loadHistory() {
    if (!selected) return;
    const data = await api(`/api/calls?contactId=${encodeURIComponent(selected.id)}`);
    const calls = data.calls || [];
    $('historyList').innerHTML = calls.length ? calls.slice(0, 12).map(c => {
      const icon = c.call_type === 'video' ? '🎥' : '📞';
      const label = c.status === 'declined' ? 'Declined' : c.status === 'cancelled' ? 'Cancelled' : c.status === 'completed' ? 'Completed' : c.status;
      return `<div class="history-item"><div><strong>${icon} ${c.call_type === 'video' ? 'Video call' : 'Audio call'}</strong><small>${escapeHtml(formatDateTime(c.started_at))} · ${escapeHtml(label)}</small></div><span class="badge bg-light text-dark">${formatDuration(c.duration_seconds)}</span></div>`;
    }).join('') : '<div class="text-muted small py-3">No call history yet.</div>';
    await refreshCallStats();
  }

  // Global call stats — independent of whether a contact is selected.
  // Called on boot and after every call event so counters update automatically.
  async function refreshCallStats() {
    try {
      const all = await api('/api/calls');
      const allCalls = all.calls || [];
      $('statConversations').textContent = contacts.length;
      $('statVideo').textContent = allCalls.filter(c => c.call_type === 'video' && c.status === 'completed').length;
      $('statAudio').textContent = allCalls.filter(c => c.call_type === 'audio' && c.status === 'completed').length;
      $('statMissed').textContent = allCalls.filter(c => ['declined','missed','cancelled'].includes(c.status)).length;
    } catch (e) { console.warn('Stats refresh failed', e); }
  }

  function appendMessage(m) {
    const area = $('chatArea');
    const row = document.createElement('div');
    row.className = `bubble-row ${m.mine ? 'mine' : ''}`;
    row.innerHTML = `<div class="bubble"><div class="bubble-author">${escapeHtml(m.senderName || (m.mine ? 'You' : selected?.name || 'User'))}</div>${escapeHtml(m.text)}<div class="bubble-meta">${escapeHtml(m.time || '')}</div></div>`;
    area.appendChild(row);
    area.scrollTop = area.scrollHeight;
  }

  async function sendMessage() {
    if (!selected) return toast('Select a customer first.', 'warning');
    const input = $('messageInput');
    const text = input.value.trim();
    if (!text) return;
    try {
      const conversation = await directConversation(true);
      const message = await api('/api/chat/messages', {method:'POST',body:JSON.stringify({conversationId:conversation.conversationId,content:text})});
      appendMessage({text:message.content,time:new Date(message.createdAt).toLocaleTimeString([],{hour:'2-digit',minute:'2-digit'}),mine:true,senderName:'You'});
      input.value='';
    } catch (error) { toast(error.message || 'Message was not sent.', 'warning'); }
  }

  async function presenceCheck(email) {
    return new Promise(resolve => {
      if (!socket?.connected) return resolve(false);
      const timeout = setTimeout(() => resolve(false), 5000);
      socket.timeout(4500).emit('presence:check', { email }, (err, response) => {
        clearTimeout(timeout);
        resolve(!err && !!response?.online);
      });
    });
  }

  async function beginCall(type) {
    if (!selected) return toast('Select a customer first.', 'warning');
    if (!socket?.connected) return toast('Realtime server is not connected.', 'warning');
    if (callId) return toast('You already have an active call.', 'warning');
    if (identityUserId === selected.id) return toast('You cannot call yourself.', 'warning');

    const onlineNow = await presenceCheck(selected.email);
    if (!onlineNow) {
      selected.status = 'offline';
      renderContacts();
      return toast(`${selected.name} is offline. You can still send a chat message.`, 'warning');
    }

    callType = type;
    callTargetId = selected.email;
    callId = crypto.randomUUID();
    callStart = Date.now();

    try {
      await startMedia(type);
      showCallStage(type, 'Calling…');
      const offer = await pc.createOffer();
      await pc.setLocalDescription(offer);
      socket.emit('call:invite', { callId, to:callTargetId, type, offer, avatar:selected.avatar });
      $('callSubtitle').textContent = 'Ringing…';
      $('audioStatus').textContent = 'Ringing…';
      ringTimeout = setTimeout(() => {
        if (callId) {
          toast('No answer. The call timed out.', 'warning');
          endCall(true);
        }
      }, 45000);
    } catch (err) {
      console.error('Call start error', err);
      toast(mediaErrorMessage(err, type), 'warning');
      endCall(false);
    }
  }

  function mediaErrorMessage(err, type) {
    if (err.name === 'NotAllowedError' || err.name === 'PermissionDeniedError') return 'Camera/microphone permission was blocked. Allow access in the browser address bar and try again.';
    if (err.name === 'NotFoundError') return type === 'video' ? 'No camera was found.' : 'No microphone was found.';
    if (err.name === 'NotReadableError') return 'The camera or microphone is already being used by another application.';
    if (err.name === 'SecurityError') return 'Camera/microphone access requires localhost or HTTPS.';
    if (err.message === 'MEDIA_UNAVAILABLE') return 'This browser does not support camera/microphone access.';
    return 'Could not start the call. Please check your devices and try again.';
  }

  async function startMedia(type) {
    if (!navigator.mediaDevices?.getUserMedia) throw new Error('MEDIA_UNAVAILABLE');
    const constraints = {
      video: type === 'video' ? { width:{ideal:1280}, height:{ideal:720}, facingMode:'user' } : false,
      audio: { echoCancellation:true, noiseSuppression:true, autoGainControl:true }
    };
    localStream = await navigator.mediaDevices.getUserMedia(constraints);
    $('localVideo').srcObject = localStream;
    $('localVideo').classList.toggle('mirror-flip', getMirrorPref());
    if (type === 'video') {
      $('localVideo').style.display = 'block';
      try { await $('localVideo').play(); } catch (_) {}
    } else {
      $('localVideo').style.display = 'none';
    }
    setupPeer();
  }

  function setupPeer() {
    pc = new RTCPeerConnection({ iceServers: webrtcConfig || [
      { urls:'stun:stun.l.google.com:19302' },
      { urls:'stun:stun.cloudflare.com:3478' }
    ] });
    localStream?.getTracks().forEach(track => pc.addTrack(track, localStream));
    pc.ontrack = event => {
      if (!event.streams?.[0]) return;
      const remote = $('remoteVideo');
      remote.srcObject = event.streams[0];
      let audio = $('remoteAudio');
      if (!audio) { audio = document.createElement('audio'); audio.id = 'remoteAudio'; audio.autoplay = true; audio.playsInline = true; document.body.appendChild(audio); }
      audio.srcObject = event.streams[0];
      // Explicit playback prevents audio-only calls from being silent when
      // the stream reaches the hidden video element after negotiation.
      Promise.all([remote.play(), audio.play()]).catch(() => toast('Remote audio is ready. Click the call window once to enable sound.', 'warning'));
    };
    pc.onicecandidate = event => {
      if (event.candidate && callId && callTargetId) socket.emit('call:ice', { callId, to:callTargetId, candidate:event.candidate });
    };
    pc.onconnectionstatechange = () => {
      const state = pc.connectionState;
      if (state === 'connected') {
        clearTimeout(ringTimeout);
        $('callSubtitle').textContent = 'Connected';
        $('audioStatus').textContent = 'Connected';
      } else if (state === 'connecting') {
        $('callSubtitle').textContent = 'Connecting…';
        $('audioStatus').textContent = 'Connecting…';
      } else if (state === 'failed') {
        $('callSubtitle').textContent = 'Connection failed';
        $('audioStatus').textContent = 'Connection failed';
        toast('The connection could not be established. A TURN server is required for some networks.', 'warning');
      } else if (state === 'disconnected') {
        $('callSubtitle').textContent = 'Connection interrupted';
        $('audioStatus').textContent = 'Connection interrupted';
      }
    };
  }

  async function flushIceCandidates() {
    if (!pc || !pc.remoteDescription) return;
    const queued = pendingIceCandidates.splice(0);
    for (const candidate of queued) {
      try { await pc.addIceCandidate(candidate); } catch (e) { console.warn('ICE candidate rejected', e); }
    }
  }

  function showCallStage(type, subtitle) {
    $('callStage').classList.add('active');
    $('callName').textContent = selected?.name || 'Customer';
    $('callAvatar').src = selected?.avatar || 'assets/img/profiles/avatar-01.jpg';
    $('callSubtitle').textContent = subtitle;
    $('videoStage').style.display = type === 'video' ? 'grid' : 'none';
    $('audioStage').style.display = type === 'audio' ? 'grid' : 'none';
    $('audioAvatar').src = selected?.avatar || 'assets/img/profiles/avatar-01.jpg';
    $('audioName').textContent = selected?.name || 'Customer';
    $('audioStatus').textContent = subtitle;
    $('callTimer').textContent = '00:00';
    clearInterval(callTimer);
    callTimer = setInterval(() => {
      const t = formatDuration(Math.floor((Date.now() - callStart) / 1000));
      $('callTimer').textContent = t;
      $('dockTimer').textContent = t;
    }, 1000);
  }

  async function acceptIncoming() {
    if (!pendingOffer) return;
    stopRingtone();
    const p = pendingOffer;
    pendingOffer = null;
    $('incomingCall').classList.remove('show');
    selected = contacts.find(c => c.id === p.callerUserId) || {
      id: p.callerUserId,
      email: p.callerEmail || p.from,
      phone: p.callerPhone || null,
      name: p.callerName || p.from,
      avatar: p.avatar || 'assets/img/profiles/avatar-01.jpg',
      status:'online', preferred:p.type === 'video' ? 'Video' : 'Audio'
    };
    callId = p.callId;
    callType = p.type;
    callTargetId = p.from;
    callStart = Date.now();
    showCallStage(callType, 'Connecting…');
    try {
      await startMedia(callType);
      await pc.setRemoteDescription(new RTCSessionDescription(p.offer));
      await flushIceCandidates();
      const answer = await pc.createAnswer();
      await pc.setLocalDescription(answer);
      socket.emit('call:answer', { callId, to:p.from, answer });
    } catch (e) {
      console.error('Accept call error', e);
      toast(mediaErrorMessage(e, callType), 'warning');
      endCall(false);
    }
  }

  function endCall(notify = true) {
    clearTimeout(ringTimeout);
    if (notify && callId && callTargetId && socket?.connected) {
      socket.emit('call:end', { callId, to:callTargetId, type:callType });
    }
    localStream?.getTracks().forEach(track => track.stop());
    if (pc) pc.close();
    pc = null;
    if (isRecording) stopRecording();
    localStream = null;
    $('remoteVideo').srcObject = null;
    if ($('remoteAudio')) $('remoteAudio').srcObject = null;
    $('localVideo').srcObject = null;
    $('callStage').classList.remove('active');
    $('callStage').classList.remove('minimized');
    $('callDock').classList.remove('show');
    clearInterval(callTimer);
    callTimer = null;
    const hadCall = !!callId;
    callId = null;
    callTargetId = null;
    callStart = null;
    pendingIceCandidates = [];
    if (hadCall) {
      if (selected) loadHistory().catch(() => {});
      else refreshCallStats();
    }
  }

  function applyPresence(p) {
    const email = normalizeEmail(p.email);
    const contact = contacts.find(c => normalizeEmail(c.email) === email || c.id === p.userId);
    if (!contact) return;
    contact.status = p.status;
    if (selected?.id === contact.id) {
      $('personStatus').textContent = labelStatus(contact.status);
      $('personPresence').className = `presence ${contact.status}`;
      $('detailStatus').innerHTML = `<span>●</span> ${labelStatus(contact.status)}`;
    }
    renderContacts();
  }

  function connectSocket() {
    if (socket) socket.disconnect();
    // `auth` keeps the Node-era contract; `query` lets the Java Socket.IO
    // gateway authenticate during the Engine.IO handshake as well.
    // Docker/Nginx serves Socket.IO at the same origin. For a direct local
    // Spring Boot run, the Java Socket.IO listener is intentionally on 3001.
    const socketUrl = window.CRM_SOCKET_URL || (location.hostname === 'localhost' && location.port === '8080' ? 'http://localhost:3001' : undefined);
    socket = io(socketUrl, { auth:{ token:session.access_token }, query:{ token:session.access_token }, transports:['websocket','polling'] });

    socket.on('connect', () => {
      socket.emit('presence:set');
      const room = activeTeamRoomName || localStorage.getItem('crmsLastTeamRoom') || 'crm-team-room';
      socket.emit('team:snapshot', { room }, members => renderTeamBadge(members || []));
      toast('Secure realtime connection ready.', 'success');
    });
    socket.on('connect_error', err => {
      console.error('Socket connection error', err.message);
      if (err.message === 'AUTH_INVALID' || err.message === 'AUTH_REQUIRED') toast('Your session expired. Please sign in again.', 'warning');
      else toast('Realtime server is unavailable. Calls will not work until it reconnects.', 'warning');
    });
    socket.on('disconnect', () => { if (callId) toast('Realtime connection lost.', 'warning'); });
    socket.on('presence:snapshot', list => list.forEach(applyPresence));
    socket.on('presence:update', applyPresence);
    socket.on('team:update', renderTeamBadge);
    socket.on('team:chat', message => { if (message.room === activeTeamRoomName) appendTeamMessage(message); });
    socket.on('team:notes', update => {
      if (update.room !== activeTeamRoomName) return;
      const field = $('teamNotesInput');
      if (field && update.from !== identityUserId) { field.value = update.notes; toast(`${update.name} updated the shared meeting notes.`, 'info'); }
    });
    socket.on('chat:error', error => toast(error.message || 'Message could not be sent.', 'warning'));

    socket.on('chat:message', m => {
      if (!selected) return;
      if (![m.senderId, m.recipientId].includes(selected.id) && ![m.senderId, m.recipientId].includes(identityUserId)) return;
      appendMessage({
        text:m.text,
        time:new Date(m.createdAt).toLocaleTimeString([], {hour:'2-digit', minute:'2-digit'}),
        mine:m.senderId === identityUserId,
        senderName:m.senderName || (m.senderId === identityUserId ? 'You' : selected.name)
      });
    });
    socket.on('chat:error', p => toast(p.message || 'Message could not be sent.', 'warning'));

    socket.on('call:offer', p => {
      if (callId) return socket.emit('call:decline', { callId:p.callId, to:p.from });
      pendingOffer = p;
      $('incomingAvatar').src = p.avatar || 'assets/img/profiles/avatar-01.jpg';
      $('incomingName').textContent = p.callerName || p.from;
      $('incomingType').textContent = p.type === 'video' ? 'Incoming video call' : 'Incoming audio call';
      $('incomingCall').classList.add('show');
      startRingtone();
      notifyIncomingCall(p);
    });
    socket.on('call:ringing', () => { $('callSubtitle').textContent='Ringing…'; $('audioStatus').textContent='Ringing…'; });
    socket.on('call:answer', async p => {
      if (!pc) return;
      try {
        await pc.setRemoteDescription(new RTCSessionDescription(p.answer));
        await flushIceCandidates();
      } catch (e) { console.error('Answer error', e); toast('Could not establish the call connection.', 'warning'); endCall(false); }
    });
    socket.on('call:ice', async p => {
      try {
        if (!pc || !pc.remoteDescription) pendingIceCandidates.push(p.candidate);
        else await pc.addIceCandidate(p.candidate);
      } catch (e) { console.warn('ICE error', e); }
    });
    socket.on('call:declined', () => { toast('Call declined.', 'warning'); endCall(false); refreshCallStats(); });
    socket.on('call:unavailable', () => { toast('Customer is no longer connected.', 'warning'); endCall(false); refreshCallStats(); });
    socket.on('call:forbidden', () => { toast('You are not permitted to contact this customer.', 'warning'); endCall(false); });
    socket.on('call:error', p => { toast(p.message || 'Call could not be started.', 'warning'); endCall(false); });
    socket.on('call:ended', () => { toast('Call ended.'); endCall(false); refreshCallStats(); });
  }

  async function newConversation() {
    const raw = prompt('Enter the customer email address, for example customer2@example.com');
    const email = normalizeEmail(raw);
    if (!email) return;
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) return toast('Enter a valid email address.', 'warning');
    try {
      await loadContacts();
      const added = contacts.find(c => normalizeEmail(c.email) === email);
      if (!added) return toast('No active CRM user has that email address.', 'warning');
      selectCustomer(added.id);
      toast('User selected.', 'success');
    } catch (e) {
      toast(e.message, 'warning');
    }
  }

  async function boot(nextSession) {
    setIdentity(nextSession);
    await ensureProfile();
    try {
      await loadWebRtcConfig();
    } catch (error) {
      console.warn('WebRTC configuration could not be loaded', error);
      webrtcConfig = [{ urls: 'stun:stun.l.google.com:19302' }];
      toast('Using fallback call configuration. Group calling still requires the backend configuration.', 'warning');
    }
    await loadContacts();
    connectSocket();
    requestNotificationPermission();
    installBrowserReadiness();
    installCookieConsent();
    installMeetingSchedule();
    await refreshCallStats();
    // Safety-net poll so stats/history stay current even if a socket event is missed.
    setInterval(() => refreshCallStats(), 30000);
  }

  $('sendMessage').onclick = sendMessage;
  $('sendMessage').addEventListener('pointerup', event => { event.preventDefault(); sendMessage(); });
  $('messageInput').onkeydown = e => { if (e.key === 'Enter') sendMessage(); };
  $('newConversation').onclick = newConversation;
  $('attachBtn').onclick = () => toast('Attachment storage is not enabled in this production baseline yet.', 'warning');
  $('modeVideo').onclick = () => { document.querySelectorAll('.mode-card').forEach(x=>x.classList.remove('selected')); $('modeVideo').classList.add('selected'); beginCall('video'); };
  $('modeAudio').onclick = () => { document.querySelectorAll('.mode-card').forEach(x=>x.classList.remove('selected')); $('modeAudio').classList.add('selected'); beginCall('audio'); };
  $('modeChat').onclick = () => { document.querySelectorAll('.mode-card').forEach(x=>x.classList.remove('selected')); $('modeChat').classList.add('selected'); $('messageInput').focus(); };
  $('headerVideo').onclick = () => beginCall('video');
  $('headerAudio').onclick = () => beginCall('audio');
  $('callChatBtn').onclick = () => minimizeCall();
  $('dockReturn').onclick = () => restoreCall();
  $('dockEnd').onclick = () => { if (callId && callTargetId) socket.emit('call:end', { callId, to:callTargetId, type:callType }); endCall(false); };

  function minimizeCall() {
    if (!callId) return;
    $('callStage').classList.remove('active');
    $('callStage').classList.add('minimized');
    $('dockName').textContent = selected?.name || 'Customer';
    $('callDock').classList.add('show');
    toast('Call minimized — you can chat while staying connected.');
  }

  function restoreCall() {
    if (!callId) return;
    $('callStage').classList.remove('minimized');
    $('callStage').classList.add('active');
    $('callDock').classList.remove('show');
  }
  $('endCallBtn').onclick = () => endCall(true);
  $('acceptCall').onclick = () => acceptIncoming();
  $('declineCall').onclick = () => {
    stopRingtone();
    if (pendingOffer) socket.emit('call:decline', { callId:pendingOffer.callId, to:pendingOffer.from });
    pendingOffer = null;
    $('incomingCall').classList.remove('show');
    refreshCallStats();
  };
  $('muteBtn').onclick = () => {
    const track = localStream?.getAudioTracks()[0];
    if (!track) return;
    track.enabled = !track.enabled;
    $('muteBtn').classList.toggle('active', !track.enabled);
    $('muteBtn').innerHTML = `<i class="ti ti-microphone${track.enabled ? '' : '-off'}"></i>`;
  };
  $('cameraBtn').onclick = () => {
    const track = localStream?.getVideoTracks()[0];
    if (!track) return toast('Camera is not active in an audio call.', 'warning');
    track.enabled = !track.enabled;
    $('cameraBtn').classList.toggle('active', !track.enabled);
  };
  $('mirrorToggleBtn').onclick = () => {
    const next = !$('localVideo').classList.contains('mirror-flip');
    $('localVideo').classList.toggle('mirror-flip', next);
    setMirrorPref(next);
  };
  $('screenBtn').onclick = async () => {
    if (!pc || callType !== 'video') return toast('Screen sharing is available during video calls.', 'warning');
    try {
      toast('Tip: don\'t share this browser tab/window itself — that causes a recursive mirror effect.', 'info');
      // displaySurface is only a hint (browsers still let people pick any
      // surface), but it steers the picker toward Window/Screen first
      // instead of the Browser Tab list, reducing the chance of someone
      // accidentally selecting the very tab running this call.
      const display = await navigator.mediaDevices.getDisplayMedia({ video: { displaySurface: 'window' } });
      const sender = pc.getSenders().find(s => s.track?.kind === 'video');
      if (sender) sender.replaceTrack(display.getVideoTracks()[0]);
      display.getVideoTracks()[0].onended = () => {
        const cameraTrack = localStream?.getVideoTracks()[0];
        if (cameraTrack && sender) sender.replaceTrack(cameraTrack);
      };
    } catch (_) {}
  };
  $('recordBtn').onclick = () => { isRecording ? stopRecording() : startRecording(); };

  $('searchInput').oninput = e => {
    const q = e.target.value.toLowerCase();
    document.querySelectorAll('.contact-item').forEach(el => {
      el.style.display = (el.textContent.toLowerCase().includes(q) || (el.dataset.email || '').includes(normalizeEmail(q))) ? 'flex' : 'none';
    });
  };
  document.querySelectorAll('[data-filter]').forEach(button => button.onclick = () => {
    document.querySelectorAll('[data-filter]').forEach(x=>x.classList.remove('active'));
    button.classList.add('active');
    filter = button.dataset.filter;
    renderContacts();
  });
  $('identityBtn').onclick = () => new bootstrap.Modal($('accountModal')).show();
  $('identityBtnTop').onclick = () => new bootstrap.Modal($('accountModal')).show();

  $('loginPasswordToggle').onclick = () => {
    const input = $('loginPassword');
    const isHidden = input.type === 'password';
    input.type = isHidden ? 'text' : 'password';
    $('loginPasswordToggle').innerHTML = `<i class="ti ti-eye${isHidden ? '-off' : ''}"></i>`;
    $('loginPasswordToggle').setAttribute('aria-label', isHidden ? 'Hide password' : 'Show password');
  };

  async function localAuth(path, body) { const response=await fetch(path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body)});const data=await response.json().catch(()=>({}));if(!response.ok)throw new Error(data.error||'Authentication failed');localStorage.setItem('crmsSession',JSON.stringify({access_token:data.accessToken,user:data.user}));return JSON.parse(localStorage.getItem('crmsSession')); }
  $('signInBtn').onclick = async () => { try { await boot(await localAuth('/api/auth/login',{email:normalizeEmail($('loginEmail').value),password:$('loginPassword').value})); authModal.hide(); } catch(e) { $('authError').textContent=e.message; } };
  $('signUpBtn').onclick = async () => { try { await boot(await localAuth('/api/auth/register',{fullName:$('loginName').value.trim()||normalizeEmail($('loginEmail').value).split('@')[0],email:normalizeEmail($('loginEmail').value),password:$('loginPassword').value})); authModal.hide(); } catch(e) { $('authError').textContent=e.message; } };
  $('logoutBtn').onclick = () => { socket?.disconnect();localStorage.removeItem('crmsSession');window.location.replace('login.html'); };
  // A service/data loading error must not log a user out.  Previously any
  // failed boot request cleared localStorage and sent a valid dashboard
  // session back to the sign-in page.  Only the API's explicit auth errors
  // should end the session.
  boot(JSON.parse(storedSession)).catch(e => {
    console.error('Calls & Conversations could not finish loading', e);
    const authFailure = ['AUTH_REQUIRED', 'AUTH_INVALID', 'HTTP_401', 'HTTP_403'].includes(e?.message);
    if (authFailure) {
      localStorage.removeItem('crmsSession');
      location.replace('login.html');
      return;
    }
    toast('Some Calls & Conversations data could not be loaded. Your session is still active; please refresh in a moment.', 'warning');
  });
  window.addEventListener('beforeunload', () => {
    if (callId && socket?.connected) socket.emit('call:end', { callId, to:callTargetId, type:callType });
  });
})();
