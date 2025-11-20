// Game state
let gameId = null;
let gameData = null;
let currentUser = null;
let selectedSquare = null;
let possibleMoves = [];
let websocket = null;

// Get game ID from URL
function getGameIdFromUrl() {
    const params = new URLSearchParams(window.location.search);
    return params.get('id');
}

// Get user data from localStorage
function getUserData() {
    const userId = localStorage.getItem('userId');
    const userName = localStorage.getItem('userName');

    if (userId && userName) {
        return { userId, userName };
    }
    return null;
}

// Fetch game from backend
async function fetchGame(gameId) {
    try {
        const response = await fetch(`/api/games/${gameId}`);
        if (!response.ok) {
            throw new Error('Failed to fetch game');
        }
        const game = await response.json();
        console.log('Game fetched:', game);
        return game;
    } catch (error) {
        console.error('Error fetching game:', error);
        alert('Failed to load game. Redirecting to home...');
        window.location.href = '/';
        return null;
    }
}

// Convert row/col to chess notation (e.g., 0,0 -> a8)
function positionToNotation(row, col) {
    const file = String.fromCharCode(97 + col); // 'a' + col
    const rank = 8 - row;
    return file + rank;
}

// Convert chess notation to row/col (e.g., a8 -> 0,0)
function notationToPosition(notation) {
    const file = notation.charCodeAt(0) - 97; // col
    const rank = 8 - parseInt(notation.charAt(1)); // row
    return { row: rank, col: file };
}

// Highlight possible moves (don't clear selected piece)
function highlightPossibleMoves(moves) {
    console.log('Highlighting possible moves:', moves);
    // Clear only possible-move highlights, not selected piece
    document.querySelectorAll('.square.possible-move').forEach(square => {
        square.classList.remove('possible-move');
    });

    moves.forEach(move => {
        const pos = notationToPosition(move);
        console.log(`Looking for square at row=${pos.row}, col=${pos.col} for move ${move}`);
        const square = document.querySelector(`[data-row="${pos.row}"][data-col="${pos.col}"]`);
        if (square) {
            square.classList.add('possible-move');
            console.log('Added possible-move class to square:', move, pos, 'Classes:', square.className);
        } else {
            console.warn('Could not find square for move:', move, pos);
        }
    });

    // Verify highlights were added
    const highlightedSquares = document.querySelectorAll('.possible-move');
    console.log(`Total highlighted squares: ${highlightedSquares.length}`);
}

// Clear all highlights
function clearHighlights() {
    document.querySelectorAll('.square').forEach(square => {
        square.classList.remove('selected', 'possible-move');
    });
}

// Fetch possible moves for a piece
async function fetchPossibleMoves(from) {
    try {
        const response = await fetch(`/api/games/${gameId}/possible-moves?from=${from}&playerId=${currentUser.userId}`);
        if (!response.ok) {
            throw new Error('Failed to fetch possible moves');
        }
        const moves = await response.json();
        console.log('Possible moves:', moves);
        return moves;
    } catch (error) {
        console.error('Error fetching possible moves:', error);
        return [];
    }
}

// Make a move
async function makeMove(from, to) {
    try {
        const response = await fetch(`/api/games/${gameId}/move`, {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({
                playerId: currentUser.userId,
                from: from,
                to: to
            })
        });

        if (!response.ok) {
            throw new Error('Invalid move');
        }

        const updatedGame = await response.json();
        console.log('Move successful:', updatedGame);
        gameData = updatedGame;
        updateGameUI(gameData, currentUser);
        return true;
    } catch (error) {
        console.error('Error making move:', error);
        alert('Invalid move!');
        return false;
    }
}

// Handle square click
async function handleSquareClick(event) {
    const square = event.currentTarget;
    const row = parseInt(square.dataset.row);
    const col = parseInt(square.dataset.col);
    const position = positionToNotation(row, col);

    // If no piece is selected yet
    if (!selectedSquare) {
        const piece = gameData.board[row][col];
        if (!piece) return; // No piece on this square

        // Check if it's player's turn
        const playerRole = getPlayerRole(gameData, currentUser.userId);
        const isWhitePiece = piece === piece.toUpperCase();
        const isPlayersTurn =
            (playerRole === 'white' && gameData.currentTurn === 'WHITE') ||
            (playerRole === 'black' && gameData.currentTurn === 'BLACK');
        const isPlayersPiece =
            (playerRole === 'white' && isWhitePiece) ||
            (playerRole === 'black' && !isWhitePiece);

        if (!isPlayersTurn || !isPlayersPiece) {
            return; // Not player's turn or not their piece
        }

        // Select this piece and show possible moves
        selectedSquare = position;
        square.classList.add('selected');
        possibleMoves = await fetchPossibleMoves(position);
        highlightPossibleMoves(possibleMoves);
    } else {
        // A piece is already selected, try to move
        if (possibleMoves.includes(position)) {
            // Valid destination, make the move
            await makeMove(selectedSquare, position);
        }

        // Deselect and clear highlights
        selectedSquare = null;
        possibleMoves = [];
        clearHighlights();
    }
}

// Render chess board
function renderBoard(board) {
    const chessBoard = document.getElementById('chessBoard');
    if (!chessBoard) {
        console.error('Chess board element not found');
        return;
    }

    chessBoard.innerHTML = '';
    console.log('Rendering board with data:', board);

    // Create board squares
    for (let row = 0; row < 8; row++) {
        for (let col = 0; col < 8; col++) {
            const square = document.createElement('div');
            square.className = 'square';
            square.dataset.row = row;
            square.dataset.col = col;

            // Alternate colors
            if ((row + col) % 2 === 0) {
                square.classList.add('light');
            } else {
                square.classList.add('dark');
            }

            // Add piece if exists
            const piece = board[row][col];
            if (piece) {
                const pieceElement = document.createElement('div');
                pieceElement.className = 'piece';
                pieceElement.textContent = getPieceSymbol(piece);
                square.appendChild(pieceElement);
            }

            // Add click handler
            square.addEventListener('click', handleSquareClick);

            chessBoard.appendChild(square);
        }
    }

    // Restore highlights if piece is selected
    if (selectedSquare) {
        const selectedPos = notationToPosition(selectedSquare);
        const selectedElement = document.querySelector(`[data-row="${selectedPos.row}"][data-col="${selectedPos.col}"]`);
        if (selectedElement) {
            selectedElement.classList.add('selected');
        }

        // Restore possible move highlights
        highlightPossibleMoves(possibleMoves);
    }
}


// Get piece unicode symbol
function getPieceSymbol(piece) {
    const symbols = {
        'K': '♔', 'Q': '♕', 'R': '♖', 'B': '♗', 'N': '♘', 'P': '♙',
        'k': '♚', 'q': '♛', 'r': '♜', 'b': '♝', 'n': '♞', 'p': '♟'
    };
    return symbols[piece] || piece;
}

// Determine player role
function getPlayerRole(game, userId) {
    if (game.whitePlayerId === userId) {
        return 'white';
    } else if (game.blackPlayerId === userId) {
        return 'black';
    }
    return null;
}

// Update UI with game data
function updateGameUI(game, user) {
    const playerRole = getPlayerRole(game, user.userId);

    // Update player name
    document.getElementById('playerName').textContent = user.userName;

    // Update role
    if (playerRole === 'white') {
        document.getElementById('playerRole').textContent = game.whitePlayerName;
        document.getElementById('playingAs').textContent = 'White ♔';
    } else if (playerRole === 'black') {
        document.getElementById('playerRole').textContent = game.blackPlayerName;
        document.getElementById('playingAs').textContent = 'Black ♚';
    } else {
        document.getElementById('playerRole').textContent = 'Spectator';
        document.getElementById('playingAs').textContent = 'Watching';
    }

    // Update current move
    const currentTurn = game.currentTurn || 'WHITE';
    document.getElementById('currentMove').textContent = currentTurn.charAt(0) + currentTurn.slice(1).toLowerCase();

    // Render board
    renderBoard(game.board);
}

// Connect to WebSocket
function connectWebSocket() {
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsUrl = `${protocol}//${window.location.host}/ws`;

    console.log('Connecting to WebSocket:', wsUrl);
    websocket = new WebSocket(wsUrl);

    websocket.onopen = () => {
        console.log('WebSocket connected');
        // Send connect message
        websocket.send(JSON.stringify({
            type: 'CONNECT',
            userId: currentUser.userId,
            userName: currentUser.userName
        }));
    };

    websocket.onmessage = (event) => {
        console.log('WebSocket message received:', event.data);
        const message = JSON.parse(event.data);
        handleWebSocketMessage(message);
    };

    websocket.onerror = (error) => {
        console.error('WebSocket error:', error);
    };

    websocket.onclose = () => {
        console.log('WebSocket closed, reconnecting in 3s...');
        setTimeout(connectWebSocket, 3000);
    };
}

// Handle WebSocket messages
function handleWebSocketMessage(message) {
    switch (message.type) {
        case 'CONNECTED':
            console.log('Connected to WebSocket as', message.userName);
            break;

        case 'GAME_UPDATE':
            console.log('Game update received via WebSocket:', message);

            // Only update if it's for the current game
            if (message.gameId === gameId) {
                console.log('Updating game board:', message.game);
                gameData = message.game;
                updateGameUI(gameData, currentUser);

                // Show notification if it's now player's turn
                const playerRole = getPlayerRole(gameData, currentUser.userId);
                const isPlayerTurn =
                    (playerRole === 'white' && gameData.currentTurn === 'WHITE') ||
                    (playerRole === 'black' && gameData.currentTurn === 'BLACK');

                if (isPlayerTurn) {
                    showTurnNotification();
                }
            } else {
                console.log('Ignoring game update for different game:', message.gameId);
            }
            break;

        case 'GAME_OVER':
            console.log('Game over received via WebSocket:', message);

            // Only handle if it's for the current game
            if (message.gameId === gameId) {
                showGameOverModal(message.winnerName, message.reason);
            }
            break;

        case 'PONG':
            // Heartbeat response
            break;

        default:
            console.log('Unknown WebSocket message type:', message.type);
    }
}

// Show notification that it's player's turn
function showTurnNotification() {
    // Flash the current move indicator
    const currentMoveElement = document.getElementById('currentMove');
    if (currentMoveElement) {
        currentMoveElement.style.transition = 'all 0.3s ease';
        currentMoveElement.style.transform = 'scale(1.2)';
        currentMoveElement.style.fontWeight = '900';
        currentMoveElement.style.color = '#10b981';

        setTimeout(() => {
            currentMoveElement.style.transform = 'scale(1)';
            currentMoveElement.style.fontWeight = '700';
            currentMoveElement.style.color = '#1a202c';
        }, 500);
    }

    // Optional: Show browser notification
    if ('Notification' in window && Notification.permission === 'granted') {
        new Notification('Chess Game', {
            body: 'It\'s your turn!',
            icon: '/favicon.ico'
        });
    }
}

// Handle Give Up button click
async function handleGiveUp() {
    if (!confirm('Are you sure you want to give up? The other player will win.')) {
        return;
    }

    try {
        const response = await fetch(`/api/games/${gameId}/give-up?playerId=${currentUser.userId}`, {
            method: 'DELETE'
        });

        if (!response.ok) {
            throw new Error('Failed to give up');
        }

        console.log('Successfully gave up the game');
    } catch (error) {
        console.error('Error giving up:', error);
        alert('Failed to give up the game');
    }
}

// Show game over modal
function showGameOverModal(winnerName, reason) {
    const modal = document.getElementById('gameOverModal');
    const title = document.getElementById('gameOverTitle');
    const message = document.getElementById('gameOverMessage');

    title.textContent = 'Game Over';

    if (reason === 'give_up') {
        message.textContent = `${winnerName} wins! Opponent gave up.`;
    } else {
        message.textContent = `${winnerName} wins!`;
    }

    modal.classList.add('show');
}

// Handle back to home button
function handleBackToHome() {
    window.location.href = '/';
}

// Initialize game
async function initGame() {
    console.log('Initializing game...');

    // Get game ID from URL
    gameId = getGameIdFromUrl();
    if (!gameId) {
        alert('No game ID provided. Redirecting to home...');
        window.location.href = '/';
        return;
    }

    // Get user data
    currentUser = getUserData();
    if (!currentUser) {
        alert('Please set your name first. Redirecting to home...');
        window.location.href = '/';
        return;
    }

    // Fetch game
    gameData = await fetchGame(gameId);
    if (!gameData) {
        return; // Error already handled in fetchGame
    }

    // Update UI
    updateGameUI(gameData, currentUser);

    // Connect to WebSocket for real-time updates
    connectWebSocket();

    // Request notification permission
    if ('Notification' in window && Notification.permission === 'default') {
        Notification.requestPermission();
    }

    // Add Give Up button event listener
    const giveUpBtn = document.getElementById('giveUpBtn');
    if (giveUpBtn) {
        giveUpBtn.addEventListener('click', handleGiveUp);
    }

    // Add Back to Home button event listener
    const backToHomeBtn = document.getElementById('backToHomeBtn');
    if (backToHomeBtn) {
        backToHomeBtn.addEventListener('click', handleBackToHome);
    }
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', initGame);