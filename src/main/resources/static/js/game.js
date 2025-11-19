// Game state
let gameId = null;
let gameData = null;
let currentUser = null;

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

// Render chess board
function renderBoard(board) {
    const chessBoard = document.getElementById('chessBoard');
    chessBoard.innerHTML = '';

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

            chessBoard.appendChild(square);
        }
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

// Initialize game
async function initGame() {
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
}

// Initialize on page load
document.addEventListener('DOMContentLoaded', initGame);