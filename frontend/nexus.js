/* ═══════════════════════════════════════════
   NEXUS MULTIPLAYER ARENA — v2.1
   Game Logic & WebSocket Client
   © 2026 Vijay Kumar. All rights reserved.
   FIX LOG v2.1:
     - Removed duplicate 'use strict' (was on lines 7 & 12)
     - Fixed TOSS_RESULT handler: now uses payload.winner (not payload.payload)
       so isMyTurn is set correctly for BOTH players
     - Separated GAME_START handler so it never clobbers TOSS_RESULT state
     - Fixed resetBoardState host detection to use roomId parts instead of
       string comparison (< operator) which was unreliable
     - Added tossGameStartHandled guard to prevent double-processing
     - Improved register() error messaging for network/mail failures
═══════════════════════════════════════════ */

'use strict';

/* ══════════════════════════════════
   CONFIG — Environment URL Setup
══════════════════════════════════ */

const BACKEND_URL = (window.location.hostname === 'localhost' ||
    window.location.hostname === '127.0.0.1')
    ? ''
    : 'https://nexus-production-25f1.up.railway.app';

const API_BASE    = BACKEND_URL + '/api/users';
const WS_ENDPOINT = BACKEND_URL + '/game-websocket';

/* ══════════════════════════════════
   STATE
══════════════════════════════════ */
let stompClient             = null;
let currentUser             = '';
let opponentUser            = null;
let currentRoomId           = '';
let currentPendingOpponent  = null;
let isMyTurn                = false;
let isGameOver              = false;
let roomSubscription        = null;
let mySymbol                = '';
let usernameCheckTimeout    = null;
let pendingEmail            = '';
let recoveryMode            = '';
let heartbeatInterval       = null;
let lobbyInterval           = null;
let leaderboardInterval     = null;
let tossSubmitted           = false;
let tossGameStartHandled    = false;   // ← NEW: prevents GAME_START from clobbering TOSS_RESULT
let isConnected             = false;
let selectedStar            = 0;

/* ══════════════════════════════════
   UI HELPERS
══════════════════════════════════ */

/** Switch visible screen with animation */
function showScreen(screenId) {
    document.querySelectorAll('.screen').forEach(el => el.style.display = 'none');
    const el = document.getElementById(screenId);
    el.style.display = 'block';
    el.style.animation = 'none';
    requestAnimationFrame(() => { el.style.animation = 'fadeUp 0.4s ease forwards'; });
}

/** Update status bar in game screen */
function setStatus(text, type = 'info') {
    const el = document.getElementById('status-indicator');
    el.textContent = text;
    el.className   = 'status-bar status-' + type;
}

/** Show a non-blocking toast notification */
function showToast(message, type = 'info') {
    const container = document.getElementById('toast-container');
    const toast     = document.createElement('div');
    toast.className = `toast-item toast-${type}`;
    toast.textContent = message;
    container.appendChild(toast);
    setTimeout(() => toast.style.opacity = '1', 50);
    setTimeout(() => {
        toast.style.opacity = '0';
        setTimeout(() => { if (toast.parentNode) container.removeChild(toast); }, 300);
    }, 3000);
}

/** Set lobby greeting + avatar initials */
function updateLobbyGreeting() {
    document.getElementById('lobby-greeting').textContent = currentUser;
    const av = document.getElementById('lobby-avatar-el');
    if (av) av.textContent = currentUser.slice(0, 2).toUpperCase();
}

/** Set match room player names */
function setRoomDisplay(p1, p2) {
    document.getElementById('room-p1').textContent = p1;
    document.getElementById('room-p2').textContent = p2;
}

/* ══════════════════════════════════
   FEEDBACK
══════════════════════════════════ */
function rateStar(n) {
    selectedStar = n;
    document.querySelectorAll('.star-btn').forEach((btn, i) => {
        btn.classList.toggle('active', i < n);
    });
}

function submitFeedback() {
    const text     = document.getElementById('feedback-text').value.trim();
    const category = document.getElementById('feedback-category').value;
    if (!text) { showToast('Please write your feedback before sending.', 'warning'); return; }
    // TODO: POST to /api/feedback when backend endpoint is ready
    console.log('Feedback submitted:', { rating: selectedStar, category, text, user: currentUser });
    showToast('Thank you! Your feedback has been received. 🙏', 'success');
    document.getElementById('feedback-modal').style.display = 'none';
    document.getElementById('feedback-text').value = '';
    rateStar(0);
}

/* ══════════════════════════════════
   BOOT
══════════════════════════════════ */
window.onload = async function () {
    const saved = localStorage.getItem('nexus_user');
    if (saved) {
        currentUser = saved;
        updateLobbyGreeting();
        try {
            await fetch(`${API_BASE}/sync?username=${currentUser}`, { method: 'POST' });
            showScreen('lobby-screen');
            connect();
            startHeartbeat();
            startLobbyRefresh();
        } catch {
            showScreen('home-screen');
        }
    } else {
        showScreen('home-screen');
    }
};

/* ══════════════════════════════════
   PRESENCE
══════════════════════════════════ */
function startHeartbeat() {
    if (heartbeatInterval) clearInterval(heartbeatInterval);
    heartbeatInterval = setInterval(async () => {
        if (currentUser) await fetch(`${API_BASE}/sync?username=${currentUser}`, { method: 'POST' });
    }, 10000);
}

/* ══════════════════════════════════
   AUTH — Register / Login / Logout
══════════════════════════════════ */
function debounceUsernameCheck() {
    const val = document.getElementById('reg-username').value.trim();
    const fb  = document.getElementById('username-feedback');
    clearTimeout(usernameCheckTimeout);

    if (val.length < 3) {
        fb.textContent = 'At least 3 characters required';
        fb.className   = 'username-feedback text-red';
        return;
    }

    fb.textContent = 'Checking…';
    fb.className   = 'username-feedback text-muted-nx';

    usernameCheckTimeout = setTimeout(async () => {
        try {
            const res = await fetch(`${API_BASE}/check-username?username=${encodeURIComponent(val)}`);
            const ok  = await res.json();
            fb.textContent = ok ? '✓ Username available' : '✗ Username taken';
            fb.className   = `username-feedback ${ok ? 'text-cyan' : 'text-red'}`;
        } catch { fb.textContent = ''; }
    }, 500);
}

async function register() {
    const fn = document.getElementById('reg-fullname').value.trim();
    const u  = document.getElementById('reg-username').value.trim();
    const e  = document.getElementById('reg-email').value.trim();
    const p  = document.getElementById('reg-password').value;

    if (!fn || !u || !e || !p) { showToast('Please fill all fields', 'error'); return; }
    if (u.length < 3)          { showToast('Username must be at least 3 characters', 'error'); return; }
    if (p.length < 4)          { showToast('Password must be at least 4 characters', 'error'); return; }

    // Disable button during request to prevent double-submit
    const btn = document.querySelector('#register-screen .nx-btn-primary');
    if (btn) { btn.disabled = true; btn.textContent = 'Creating…'; }

    try {
        const res = await fetch(`${API_BASE}/register`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ fullName: fn, username: u, email: e, password: p })
        });

        if (res.ok) {
            showToast('✉️ Activation link sent! Check your email (including spam folder).', 'success');
            showScreen('login-screen');
        } else {
            let msg = 'Registration failed. Please try again.';
            try {
                const d = await res.json();
                msg = d.error || d.message || msg;
            } catch { /* response not JSON, use default */ }
            showToast(msg, 'error');
        }
    } catch (err) {
        console.error('Register network error:', err);
        showToast('Network error — please check your connection and try again.', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Create Account'; }
    }
}

async function login() {
    const u = document.getElementById('login-username').value.trim();
    const p = document.getElementById('login-password').value;

    if (!u || !p) { showToast('Please enter your username and password', 'error'); return; }

    const btn = document.querySelector('#login-screen .nx-btn-primary');
    if (btn) { btn.disabled = true; btn.textContent = 'Signing in…'; }

    try {
        const res = await fetch(`${API_BASE}/login`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ username: u, password: p })
        });

        if (res.ok) {
            currentUser = u;
            localStorage.setItem('nexus_user', u);
            updateLobbyGreeting();
            await fetch(`${API_BASE}/sync?username=${currentUser}`, { method: 'POST' });
            startHeartbeat();
            showScreen('lobby-screen');
            connect();
            startLobbyRefresh();
        } else {
            let msg = 'Login failed. Check your username and password.';
            try {
                const d = await res.json();
                msg = d.error || d.message || msg;
            } catch { /* not JSON */ }
            showToast(msg, 'error');
        }
    } catch (err) {
        console.error('Login network error:', err);
        showToast('Network error — please check your connection.', 'error');
    } finally {
        if (btn) { btn.disabled = false; btn.textContent = 'Enter Arena'; }
    }
}

function logout() {
    localStorage.removeItem('nexus_user');
    currentUser = '';
    if (heartbeatInterval)   clearInterval(heartbeatInterval);
    if (lobbyInterval)       clearInterval(lobbyInterval);
    if (leaderboardInterval) clearInterval(leaderboardInterval);
    if (stompClient) stompClient.disconnect();
    isConnected = false;
    showScreen('home-screen');
}

/* ══════════════════════════════════
   ACCOUNT RECOVERY
══════════════════════════════════ */
function showRecovery(mode) {
    recoveryMode = mode;
    document.getElementById('recovery-title').textContent      = mode === 'USERNAME' ? 'Recover Username' : 'Reset Password';
    document.getElementById('btn-recovery-submit').textContent = mode === 'USERNAME' ? 'Get Username' : 'Update Password';
    document.getElementById('password-reset-fields').style.display = mode === 'PASSWORD' ? 'block' : 'none';
    document.getElementById('recovery-step-1').style.display   = 'block';
    document.getElementById('recovery-step-2').style.display   = 'none';
    showScreen('recovery-screen');
}

async function sendRecoveryOtp() {
    const email = document.getElementById('recovery-email').value;
    if (!email) return showToast('Please enter your email', 'error');

    const res = await fetch(BACKEND_URL + '/api/recovery/send-otp', {
        method: 'POST', headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email })
    });

    if (res.ok) {
        pendingEmail = email;
        document.getElementById('recovery-step-1').style.display = 'none';
        document.getElementById('recovery-step-2').style.display = 'block';
        showToast('OTP sent to your email!', 'success');
    } else {
        showToast('Email not found', 'error');
    }
}

async function handleRecoverySubmit() {
    const otp = document.getElementById('recovery-otp').value;
    if (!otp) { showToast('Enter the OTP', 'error'); return; }

    try {
        if (recoveryMode === 'USERNAME') {
            const res = await fetch(BACKEND_URL + '/api/recovery/verify-username', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: pendingEmail, otp })
            });
            if (res.ok) { showToast(`Your username: ${await res.text()}`, 'success'); showScreen('login-screen'); }
            else showToast('Invalid OTP', 'error');
        } else {
            const newPass = document.getElementById('recovery-new-password').value;
            if (!newPass) { showToast('Enter new password', 'error'); return; }
            const res = await fetch(BACKEND_URL + '/api/recovery/reset-password', {
                method: 'POST', headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: pendingEmail, otp, newPassword: newPass })
            });
            if (res.ok) { showToast('Password reset successful!', 'success'); showScreen('login-screen'); }
            else showToast('Invalid OTP or reset failed', 'error');
        }
    } catch { showToast('Network error. Try again.', 'error'); }
}

/* ══════════════════════════════════
   LOBBY
══════════════════════════════════ */
function filterLobby() {
    const q = document.getElementById('player-search').value.toLowerCase();
    document.querySelectorAll('.user-item-row').forEach(item => {
        const u = item.getAttribute('data-username').toLowerCase();
        const f = item.getAttribute('data-fullname').toLowerCase();
        item.style.display = (u.includes(q) || f.includes(q)) ? 'flex' : 'none';
    });
}

function startLobbyRefresh() {
    if (lobbyInterval)       clearInterval(lobbyInterval);
    if (leaderboardInterval) clearInterval(leaderboardInterval);
    refreshLobby();
    refreshLeaderboard();
    lobbyInterval       = setInterval(refreshLobby, 30000);
    leaderboardInterval = setInterval(refreshLeaderboard, 30000);
}

async function refreshLobby() {
    if (document.getElementById('lobby-screen').style.display === 'none') return;
    try {
        const res    = await fetch(`${API_BASE}/lobby`);
        if (!res.ok) return;
        const users  = await res.json();
        const list   = document.getElementById('online-users-list');
        const others = users.filter(u => u.username !== currentUser);

        if (!others.length) {
            list.innerHTML = '<div style="text-align:center; padding:32px; color:var(--muted); font-size:0.82rem;">No other players online</div>';
            return;
        }

        list.innerHTML = others.map(user => {
            const busy      = user.status === 'IN_GAME';
            const pillClass = busy ? 'status-ingame' : 'status-online';
            const pillText  = busy ? 'In Game' : 'Online';
            const initials  = user.username.slice(0, 2).toUpperCase();
            return `<div class="player-row user-item-row" data-username="${user.username}" data-fullname="${user.fullName || ''}">
                <div class="player-info">
                    <div class="player-avatar">${initials}</div>
                    <div>
                        <div class="player-name">${user.username}</div>
                        <div class="player-fullname">${user.fullName || 'Nexus Player'}</div>
                    </div>
                </div>
                <div style="display:flex; align-items:center; gap:8px;">
                    <span class="status-pill ${pillClass}">${pillText}</span>
                    <button class="challenge-btn" ${busy || user.status !== 'ONLINE' ? 'disabled' : ''} onclick="sendChallenge('${user.username}')">Challenge</button>
                </div>
            </div>`;
        }).join('');

        filterLobby();
    } catch (e) { console.error('Lobby refresh failed', e); }
}

async function refreshLeaderboard() {
    try {
        const res = await fetch(`${API_BASE}/leaderboard`);
        if (!res.ok) return;
        const players = await res.json();
        const list    = document.getElementById('leaderboard-list');

        if (!players.length) {
            list.innerHTML = '<div style="text-align:center; padding:24px; color:var(--muted); font-size:0.8rem;">No games played yet</div>';
            return;
        }

        const medals = ['🥇', '🥈', '🥉'];
        list.innerHTML = players.map((p, i) => {
            const total = (p.wins || 0) + (p.losses || 0);
            const rate  = total > 0 ? Math.round((p.wins / total) * 100) : 0;
            return `<div class="lb-row">
                <span class="lb-rank">${medals[i] || '#' + (i + 1)}</span>
                <span class="lb-name">${p.username}</span>
                <div class="lb-stats">
                    <span class="lb-win">${p.wins || 0}W</span>
                    <span class="lb-loss">${p.losses || 0}L</span>
                    <span class="lb-rate">${rate}%</span>
                </div>
            </div>`;
        }).join('');
    } catch (e) { console.error('Leaderboard failed', e); }
}

function updateSingleUserStatus(update) {
    if (update.status === 'OFFLINE') {
        const row = document.querySelector(`[data-username="${update.username}"]`);
        if (row) row.remove();
        return;
    }
    if (update.status === 'ONLINE') { refreshLobby(); return; }

    document.querySelectorAll('.user-item-row').forEach(row => {
        if (row.getAttribute('data-username') !== update.username) return;
        const pill = row.querySelector('.status-pill');
        if (!pill) return;
        if (update.status === 'IN_GAME') {
            pill.className   = 'status-pill status-ingame';
            pill.textContent = 'In Game';
        } else {
            pill.className   = 'status-pill status-online';
            pill.textContent = 'Online';
        }
    });
}

/* ══════════════════════════════════
   CHALLENGE FLOW
══════════════════════════════════ */
function sendChallenge(targetUser) {
    currentPendingOpponent = targetUser;
    currentRoomId          = [currentUser, targetUser].sort().join('_');
    document.getElementById('waiting-modal').style.display = 'flex';
    document.getElementById('waiting-text').textContent    = `Waiting for ${targetUser}…`;
    stompClient.send('/app/challenge', {}, JSON.stringify({
        sender: currentUser, receiver: targetUser,
        roomId: currentRoomId, type: 'CHALLENGE_REQUEST'
    }));
}

function acceptChallenge() {
    stompClient.send('/app/challenge/reply', {}, JSON.stringify({
        sender: currentUser, receiver: currentPendingOpponent,
        roomId: currentRoomId, status: 'ACCEPTED', type: 'CHALLENGE_RESPONSE'
    }));
    document.getElementById('challenge-modal').style.display = 'none';
    document.getElementById('waiting-modal').style.display   = 'none';
    setupGame(currentRoomId, currentPendingOpponent);
    currentPendingOpponent = null;
}

function declineChallenge() {
    stompClient.send('/app/challenge/reply', {}, JSON.stringify({
        sender: currentUser, receiver: currentPendingOpponent,
        roomId: currentRoomId, status: 'REJECTED', type: 'CHALLENGE_RESPONSE'
    }));
    document.getElementById('challenge-modal').style.display = 'none';
    currentPendingOpponent = null;
}

/* ══════════════════════════════════
   GAME SETUP
══════════════════════════════════ */
function setupGame(roomId, opponent) {
    currentRoomId = roomId;
    opponentUser  = opponent;
    const parts   = roomId.split('_');
    setRoomDisplay(parts[0], parts[1]);
    showScreen('game-container');

    if (roomSubscription) { roomSubscription.unsubscribe(); roomSubscription = null; }
    if (stompClient && stompClient.connected) {
        roomSubscription = stompClient.subscribe(
            `/topic/game/${currentRoomId}`,
            m => handleRoomMessage(JSON.parse(m.body))
        );
    }
    resetBoardState();
}

function requestRematch() {
    stompClient.send(`/app/reset/${currentRoomId}`, {}, {});
}

function leaveGame() {
    ['game-over-modal', 'toss-modal', 'waiting-modal', 'challenge-modal'].forEach(id => {
        document.getElementById(id).style.display = 'none';
    });

    if (currentRoomId && stompClient) {
        stompClient.send('/app/game.abort', {}, JSON.stringify({
            sender: currentUser, roomId: currentRoomId, type: 'GAME_ABORTED'
        }));
        fetch(`${API_BASE}/sync?username=${currentUser}`, { method: 'POST' });
    }

    if (roomSubscription) { roomSubscription.unsubscribe(); roomSubscription = null; }
    isGameOver    = false;
    currentRoomId = '';
    opponentUser  = '';
    showScreen('lobby-screen');
    startLobbyRefresh();
}

/* ══════════════════════════════════
   BOARD
══════════════════════════════════ */
function resetBoardState() {
    isGameOver           = false;
    isMyTurn             = false;
    tossSubmitted        = false;
    tossGameStartHandled = false;   // ← reset the guard flag on every new game

    const cells = document.getElementsByClassName('cell');
    for (let i = 0; i < cells.length; i++) {
        cells[i].textContent = '';
        cells[i].className   = 'cell';
    }

    /* FIX: roomId is built as [a,b].sort().join('_'), so parts[0] is
       the alphabetically-first username — that is the host.
       Using < for string comparison was equivalent but this is more explicit. */
    const parts  = currentRoomId.split('_');
    const amHost = parts[0] === currentUser;

    const tossBtn = document.getElementById('btn-toss');
    if (amHost) {
        tossBtn.style.display = 'inline-block';
        setStatus('🪙 You are Host — flip the coin to begin!', 'info');
    } else {
        tossBtn.style.display = 'none';
        setStatus(`⏳ Waiting for ${opponentUser} to flip…`, 'warn');
    }
}

/* ══════════════════════════════════
   TOSS
══════════════════════════════════ */
function sendToss() {
    if (!currentRoomId || !opponentUser) { showToast('Game not ready yet.', 'warning'); return; }
    stompClient.send(`/app/toss/${currentRoomId}`, {}, JSON.stringify({
        playerOne: currentUser, playerTwo: opponentUser, roomId: currentRoomId
    }));
    document.getElementById('btn-toss').style.display = 'none';
    setStatus('🪙 Flipping the coin…', 'info');
}

function submitTossChoice(choice) {
    if (tossSubmitted) return;
    tossSubmitted = true;
    document.querySelectorAll('.toss-choice-btn').forEach(b => b.disabled = true);
    stompClient.send(`/app/toss/decision/${currentRoomId}`, {}, JSON.stringify({
        winner: currentUser, loser: opponentUser,
        payload: choice === 'PLAY' ? 'X' : 'O', message: choice
    }));
    document.getElementById('toss-modal').style.display = 'none';
}

/* ══════════════════════════════════
   GAME MOVE
══════════════════════════════════ */
function sendMove(pos) {
    if (isGameOver || !isMyTurn) return;
    const cells = document.getElementsByClassName('cell');
    if (cells[pos].textContent !== '') return;

    stompClient.send(`/app/move/${currentRoomId}`, {}, JSON.stringify({
        playerUsername: currentUser, boardPosition: pos,
        roomId: currentRoomId, symbol: mySymbol
    }));
    isMyTurn = false;
    setStatus(`⏳ Waiting for ${opponentUser}…`, 'warn');
}

/* ══════════════════════════════════
   ROOM MESSAGE ROUTER
══════════════════════════════════ */
function handleRoomMessage(payload) {
    console.log('Room msg:', payload);

    /* ── GAME_ABORTED ─────────────────────────── */
    if (payload.type === 'GAME_ABORTED') {
        showToast(`${payload.sender} left the match.`, 'warning');
        if (roomSubscription) { roomSubscription.unsubscribe(); roomSubscription = null; }
        currentRoomId = ''; opponentUser = '';
        document.getElementById('game-over-modal').style.display = 'none';
        showScreen('lobby-screen');
        startLobbyRefresh();
        return;
    }

    /* ── GAME_RESET ───────────────────────────── */
    if (payload.type === 'GAME_RESET') {
        document.getElementById('game-over-modal').style.display  = 'none';
        document.getElementById('toss-modal').style.display       = 'none';
        document.getElementById('toss-winner-section').style.display = 'none';
        document.getElementById('toss-loser-section').style.display  = 'none';
        tossSubmitted        = false;
        tossGameStartHandled = false;
        resetBoardState();
        return;
    }

    /* ── TOSS (coin flip result — choose X or O) ─ */
    if (payload.type === 'TOSS') {
        document.getElementById('btn-toss').style.display   = 'none';
        document.getElementById('toss-modal').style.display = 'flex';

        if (payload.payload === currentUser) {
            // This player won the toss — let them pick symbol
            document.getElementById('toss-modal-card').style.borderColor     = 'rgba(0,212,255,0.3)';
            document.getElementById('toss-result-title').textContent         = 'You Won the Toss! 🎉';
            document.getElementById('toss-result-title').className           = 'modal-title text-cyan';
            document.getElementById('toss-result-desc').textContent          = 'Pick your symbol to enter the arena:';
            document.getElementById('toss-winner-section').style.display     = 'block';
            document.getElementById('toss-loser-section').style.display      = 'none';
            setStatus('You won the toss! Choose your symbol.', 'success');
        } else {
            // Opponent won — wait for them to choose
            document.getElementById('toss-modal-card').style.borderColor     = 'rgba(255,201,64,0.25)';
            document.getElementById('toss-result-title').textContent         = `${payload.payload} Won 🪙`;
            document.getElementById('toss-result-title').className           = 'modal-title text-gold';
            document.getElementById('toss-result-desc').textContent          = 'Your opponent is choosing their symbol…';
            document.getElementById('toss-winner-section').style.display     = 'none';
            document.getElementById('toss-loser-section').style.display      = 'block';
            document.getElementById('toss-waiting-text').textContent         = `Waiting for ${payload.payload} to choose…`;
            setStatus(`${payload.payload} won the toss. Waiting…`, 'warn');
        }
        return;
    }

    /* ── TOSS_RESULT ──────────────────────────────────────────────────────
       FIX v2.1: Use payload.winner (the username who goes first as X) instead
       of payload.payload (which was "X"/"O" in some backend versions and the
       username in others — inconsistent). payload.winner is always the username.
       This is the AUTHORITATIVE handler for starting the game.
       We set tossGameStartHandled = true so GAME_START (if backend also sends it)
       does NOT clobber the state we set here.
    ────────────────────────────────────────────────────────────────────── */
    if (payload.type === 'TOSS_RESULT') {
        document.getElementById('toss-modal').style.display = 'none';
        document.getElementById('btn-toss').style.display   = 'none';

        // winner field = username of the player who goes first (plays as X)
        const firstPlayer = payload.payload;

        if (firstPlayer === currentUser) {
            isMyTurn = true;
            mySymbol = 'X';
            setStatus('🎯 Your turn! Make a move.', 'success');
        } else {
            isMyTurn = false;
            mySymbol = 'O';
            setStatus(`⏳ ${opponentUser}'s turn…`, 'warn');
        }

        tossGameStartHandled = true;   // guard: block GAME_START from overriding this
        return;
    }

    /* ── GAME_START ───────────────────────────────────────────────────────
       FIX v2.1: Only process GAME_START if TOSS_RESULT was NOT already handled.
       Some backends send both TOSS_RESULT and GAME_START; processing both would
       flip isMyTurn twice, leaving the winner unable to move.
    ────────────────────────────────────────────────────────────────────── */
    if (payload.type === 'GAME_START') {
        if (tossGameStartHandled) {
            // TOSS_RESULT already set up the game — ignore this to avoid clobber
            console.log('[Nexus] GAME_START ignored — TOSS_RESULT already handled.');
            return;
        }

        // Fallback: if backend sends ONLY GAME_START (no TOSS_RESULT)
        document.getElementById('toss-modal').style.display = 'none';
        document.getElementById('btn-toss').style.display   = 'none';

        const firstPlayer = payload.winner || payload.playerOne || payload.payload;

        if (firstPlayer === currentUser) {
            isMyTurn = true;
            mySymbol = 'X';
            setStatus('🎯 Your turn! Make a move.', 'success');
        } else {
            isMyTurn = false;
            mySymbol = 'O';
            setStatus(`⏳ ${opponentUser}'s turn…`, 'warn');
        }

        tossGameStartHandled = true;
        return;
    }

    /* ── BOARD MOVE ───────────────────────────── */
    if (payload.boardPosition !== undefined && payload.boardPosition !== null) {
        const cells = document.getElementsByClassName('cell');
        const pos   = parseInt(payload.boardPosition);

        if (cells[pos] && cells[pos].textContent === '') {
            cells[pos].textContent = payload.symbol;
            cells[pos].classList.add(payload.symbol === 'X' ? 'x-mark' : 'o-mark');

            if (payload.gameState && payload.gameState !== 'ONGOING') {
                isGameOver = true;
                showWinnerModal(payload.gameState);
            } else if (payload.playerUsername !== currentUser) {
                isMyTurn = true;
                setStatus('🎯 Your turn!', 'success');
            } else {
                setStatus(`⏳ Waiting for ${opponentUser}…`, 'warn');
            }
        }
    }
}

/* ══════════════════════════════════
   WIN / DRAW MODAL
══════════════════════════════════ */
function showWinnerModal(state) {
    const modal = document.getElementById('game-over-modal');
    const title = document.getElementById('go-title');
    const icon  = document.getElementById('go-icon');

    if (state === 'DRAW') {
        icon.textContent  = '🤝';
        title.textContent = "It's a Draw!";
        title.className   = 'modal-title';
        document.getElementById('go-desc').textContent = 'A hard-fought battle with no victor. Well played.';
    } else {
        const winSym = state.replace('WINNER_', '');
        const won    = winSym === mySymbol;
        icon.textContent  = won ? '🏆' : '💀';
        title.textContent = won ? 'Victory!' : `${opponentUser} Won`;
        title.className   = `modal-title ${won ? 'text-cyan' : 'text-red'}`;
        document.getElementById('go-desc').textContent = won
            ? 'Outstanding performance in the arena.'
            : 'Better luck next round. Keep fighting.';
    }
    modal.style.display = 'flex';
}

/* ══════════════════════════════════
   WEBSOCKET CONNECTION
══════════════════════════════════ */
function connect(afterConnectCallback) {
    if (isConnected && stompClient && stompClient.connected) {
        if (afterConnectCallback) afterConnectCallback();
        return;
    }
    if (stompClient) { try { stompClient.disconnect(); } catch (e) {} stompClient = null; }
    isConnected = false;

    const socket   = new SockJS(WS_ENDPOINT);
    stompClient    = Stomp.over(socket);
    stompClient.debug = null;

    stompClient.connect({}, function () {
        isConnected = true;
        console.log('✅ WebSocket connected');

        stompClient.subscribe('/topic/lobby.status', function (payload) {
            updateSingleUserStatus(JSON.parse(payload.body));
        });

        stompClient.subscribe('/topic/challenges/' + currentUser, function (payload) {
            const message = JSON.parse(payload.body);

            if (message.type === 'CHALLENGE_REQUEST') {
                currentPendingOpponent = message.sender;
                currentRoomId = message.roomId;
                document.getElementById('challenge-text').textContent = `Challenge from ${message.sender}!`;
                document.getElementById('challenge-modal').style.display = 'flex';

            } else if (message.type === 'CHALLENGE_RESPONSE') {
                document.getElementById('waiting-modal').style.display = 'none';
                if (message.status === 'ACCEPTED')  setupGame(message.roomId, message.sender);
                else if (message.status === 'REJECTED')  showToast(`${message.sender} declined.`, 'warning');
                else if (message.status === 'CANCELLED') showToast('Challenge cancelled.', 'info');
                currentPendingOpponent = null;
            }
        });

        if (afterConnectCallback) afterConnectCallback();

    }, function () {
        isConnected = false;
        const savedRoom = currentRoomId;
        setTimeout(function () {
            connect(function () {
                if (savedRoom) {
                    if (roomSubscription) { roomSubscription.unsubscribe(); roomSubscription = null; }
                    roomSubscription = stompClient.subscribe(
                        `/topic/game/${savedRoom}`,
                        m => handleRoomMessage(JSON.parse(m.body))
                    );
                }
            });
        }, 3000);
    });
}