// WebSocket connection
let ws = null;
let reconnectAttempts = 0;
const MAX_RECONNECT_ATTEMPTS = 5;

// Generate unique user ID
function generateUserId() {
    return 'user_' + Math.random().toString(36).substr(2, 9) + '_' + Date.now();
}

// Get or create user data
function getUserData() {
    const userId = localStorage.getItem('userId');
    const userName = localStorage.getItem('userName');

    if (userId && userName) {
        return { userId, userName };
    }
    return null;
}

// Save user data to localStorage
function saveUserData(name) {
    const userId = generateUserId();
    localStorage.setItem('userId', userId);
    localStorage.setItem('userName', name);
    return { userId, userName: name };
}

// WebSocket Functions
function connectWebSocket(userData) {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws/users`;

    console.log('Connecting to WebSocket:', wsUrl);

    ws = new WebSocket(wsUrl);

    ws.onopen = () => {
        console.log('WebSocket connected');
        reconnectAttempts = 0;

        // Send connect message
        ws.send(JSON.stringify({
            type: 'CONNECT',
            userId: userData.userId,
            userName: userData.userName
        }));

        // Start heartbeat
        startHeartbeat();
    };

    ws.onmessage = (event) => {
        try {
            const message = JSON.parse(event.data);
            console.log('WebSocket message received:', message);
            handleWebSocketMessage(message);
        } catch (error) {
            console.error('Error parsing WebSocket message:', error);
        }
    };

    ws.onclose = () => {
        console.log('WebSocket disconnected');
        stopHeartbeat();

        // Attempt to reconnect
        if (reconnectAttempts < MAX_RECONNECT_ATTEMPTS) {
            reconnectAttempts++;
            console.log(`Reconnecting... Attempt ${reconnectAttempts}/${MAX_RECONNECT_ATTEMPTS}`);
            setTimeout(() => {
                const userData = getUserData();
                if (userData) {
                    connectWebSocket(userData);
                }
            }, 3000);
        }
    };

    ws.onerror = (error) => {
        console.error('WebSocket error:', error);
    };
}

let heartbeatInterval = null;

function startHeartbeat() {
    heartbeatInterval = setInterval(() => {
        if (ws && ws.readyState === WebSocket.OPEN) {
            ws.send(JSON.stringify({ type: 'PING' }));
        }
    }, 30000); // Every 30 seconds
}

function stopHeartbeat() {
    if (heartbeatInterval) {
        clearInterval(heartbeatInterval);
        heartbeatInterval = null;
    }
}

function handleWebSocketMessage(message) {
    switch (message.type) {
        case 'CONNECTED':
            console.log('Successfully connected to WebSocket');
            break;

        case 'USER_LIST':
            updateUserList(message.users);
            break;

        case 'PONG':
            // Heartbeat response
            break;

        case 'INVITATION_RECEIVED':
            showInvitationNotification(message.fromUserId, message.fromUserName);
            break;

        case 'INVITATION_ACCEPTED':
            redirectToGame(message.gameId);
            break;

        case 'INVITATION_DECLINED':
            hideModal();
            alert(`${message.fromUserName} declined your invitation.`);
            break;

        default:
            console.log('Unknown message type:', message.type);
    }
}

function updateUserList(users) {
    const currentUser = getUserData();
    if (!currentUser) return;

    // Filter out current user
    const otherUsers = users.filter(user => user.userId !== currentUser.userId);

    const usersGrid = document.querySelector('.users-grid');
    if (!usersGrid) return;

    if (otherUsers.length === 0) {
        usersGrid.innerHTML = `
            <div style="text-align: center; padding: 32px; color: #9ca3af;">
                <p style="font-size: 14px;">No other players online</p>
                <p style="font-size: 12px; margin-top: 8px;">Waiting for players to join...</p>
            </div>
        `;
        return;
    }

    usersGrid.innerHTML = otherUsers.map(user => `
        <div class="user-card" onclick="selectUser('${user.userId}')">
            <div class="user-card-avatar">♟️</div>
            <div class="user-card-info">
                <h4>${user.userName}</h4>
                <span class="status-badge online">Available</span>
            </div>
        </div>
    `).join('');
}

// Show user list
function showUserList(currentUser) {
    const container = document.querySelector('.container');

    container.innerHTML = `
        <div class="user-list-header">
            <div class="current-user-info">
                <div class="user-avatar">👤</div>
                <div>
                    <h2>${currentUser.userName}</h2>
                    <p class="user-status">Ready to play</p>
                </div>
            </div>
            <button class="btn-logout" onclick="logout()">Change Name</button>
        </div>

        <div class="section-divider"></div>

        <div class="users-section">
            <h3 class="section-title">Available Players</h3>
            <div class="users-grid">
                <div style="text-align: center; padding: 32px; color: #9ca3af;">
                    <p style="font-size: 14px;">Connecting...</p>
                </div>
            </div>
        </div>
    `;

    // Connect to WebSocket
    connectWebSocket(currentUser);
}

// Handle form submission
function handleFormSubmit(event) {
    event.preventDefault();

    const nameInput = document.getElementById('playerName');
    const name = nameInput.value.trim();

    if (name) {
        const userData = saveUserData(name);

        // Add fade-out animation
        const container = document.querySelector('.container');
        container.style.opacity = '0';
        container.style.transform = 'scale(0.95)';

        // Wait for animation, then show user list
        setTimeout(() => {
            showUserList(userData);
            // Trigger fade-in
            setTimeout(() => {
                container.style.opacity = '1';
                container.style.transform = 'scale(1)';
            }, 50);
        }, 300);
    }
}

// Select user to play with
function selectUser(userId) {
    const userCard = event.currentTarget;
    if (userCard.classList.contains('disabled')) {
        return;
    }

    // Find user info
    const userCards = document.querySelectorAll('.user-card');
    let targetUserName = '';

    userCards.forEach(card => {
        if (card.onclick && card.onclick.toString().includes(userId)) {
            targetUserName = card.querySelector('h4').textContent;
        }
    });

    showConfirmationModal(userId, targetUserName);
}

// Show confirmation modal before sending invitation
function showConfirmationModal(toUserId, toUserName) {
    const modalHtml = `
        <div class="modal-overlay" id="confirmModal">
            <div class="modal">
                <div class="modal-header">
                    <div class="modal-icon">♔</div>
                    <h3>Send Game Invitation</h3>
                    <p>Do you want to send a game invitation to <strong>${toUserName}</strong>?</p>
                </div>
                <div class="modal-actions">
                    <button class="modal-btn modal-btn-secondary" onclick="hideModal()">Cancel</button>
                    <button class="modal-btn modal-btn-primary" onclick="sendInvitation('${toUserId}', '${toUserName}')">Send Invitation</button>
                </div>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML('beforeend', modalHtml);

    // Trigger animation
    setTimeout(() => {
        document.getElementById('confirmModal').classList.add('show');
    }, 10);
}

// Hide modal
function hideModal() {
    const modal = document.getElementById('confirmModal');
    if (modal) {
        modal.classList.remove('show');
        setTimeout(() => modal.remove(), 300);
    }
}

// Send invitation via WebSocket
function sendInvitation(toUserId, toUserName) {
    const currentUser = getUserData();
    if (!currentUser) return;

    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
            type: 'SEND_INVITATION',
            fromUserId: currentUser.userId,
            fromUserName: currentUser.userName,
            toUserId: toUserId,
            toUserName: toUserName
        }));

        console.log('Invitation sent to:', toUserName);

        // Update modal to show waiting state
        const modal = document.getElementById('confirmModal');
        if (modal) {
            modal.querySelector('.modal-header p').innerHTML =
                `Waiting for <strong>${toUserName}</strong> to accept...`;
            modal.querySelector('.modal-actions').innerHTML = `
                <button class="modal-btn modal-btn-secondary" onclick="hideModal()">Cancel</button>
            `;
        }
    }
}

// Show invitation notification
function showInvitationNotification(fromUserId, fromUserName) {
    const notificationHtml = `
        <div class="notification" id="invitationNotification">
            <div class="notification-header">
                <div class="notification-icon">♔</div>
                <h4 class="notification-title">Game Invitation</h4>
            </div>
            <p class="notification-message">
                <strong>${fromUserName}</strong> wants to play chess with you!
            </p>
            <div class="notification-actions">
                <button class="notification-btn notification-btn-decline"
                        onclick="declineInvitation('${fromUserId}', '${fromUserName}')">
                    Decline
                </button>
                <button class="notification-btn notification-btn-accept"
                        onclick="acceptInvitation('${fromUserId}', '${fromUserName}')">
                    Accept
                </button>
            </div>
        </div>
    `;

    document.body.insertAdjacentHTML('beforeend', notificationHtml);

    // Trigger animation
    setTimeout(() => {
        document.getElementById('invitationNotification').classList.add('show');
    }, 10);
}

// Hide notification
function hideNotification() {
    const notification = document.getElementById('invitationNotification');
    if (notification) {
        notification.classList.remove('show');
        setTimeout(() => notification.remove(), 300);
    }
}

// Accept invitation
function acceptInvitation(fromUserId, fromUserName) {
    const currentUser = getUserData();
    if (!currentUser) return;

    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
            type: 'ACCEPT_INVITATION',
            fromUserId: fromUserId,
            fromUserName: fromUserName,
            toUserId: currentUser.userId,
            toUserName: currentUser.userName
        }));

        console.log('Invitation accepted, creating game...');
        hideNotification();
    }
}

// Decline invitation
function declineInvitation(fromUserId, fromUserName) {
    const currentUser = getUserData();
    if (!currentUser) return;

    if (ws && ws.readyState === WebSocket.OPEN) {
        ws.send(JSON.stringify({
            type: 'DECLINE_INVITATION',
            fromUserId: fromUserId,
            fromUserName: fromUserName,
            toUserId: currentUser.userId,
            toUserName: currentUser.userName
        }));

        console.log('Invitation declined');
        hideNotification();
    }
}

// Redirect to game page
function redirectToGame(gameId) {
    console.log('Redirecting to game:', gameId);
    window.location.href = `/game?id=${gameId}`;
}

// Logout (clear localStorage)
function logout() {
    const container = document.querySelector('.container');
    container.style.opacity = '0';
    container.style.transform = 'scale(0.95)';

    // Close WebSocket connection
    if (ws) {
        ws.close();
    }

    setTimeout(() => {
        localStorage.removeItem('userId');
        localStorage.removeItem('userName');
        location.reload();
    }, 300);
}

// Initialize page
document.addEventListener('DOMContentLoaded', function() {
    const userData = getUserData();

    if (userData) {
        // User already exists, show user list
        showUserList(userData);
    } else {
        // Show form
        const form = document.querySelector('form');
        if (form) {
            form.addEventListener('submit', handleFormSubmit);
        }
    }
});