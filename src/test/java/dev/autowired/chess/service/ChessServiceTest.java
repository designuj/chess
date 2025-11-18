package dev.autowired.chess.service;

import dev.autowired.chess.model.*;
import dev.autowired.chess.repository.GameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChessServiceTest {

    @Mock
    private GameRepository gameRepository;

    @InjectMocks
    private ChessService chessService;

    private Game testGame;
    private static final String GAME_ID = "test-game-id";
    private static final String WHITE_PLAYER = "white-player";
    private static final String BLACK_PLAYER = "black-player";

    @BeforeEach
    void setUp() {
        testGame = new Game(WHITE_PLAYER, "Player1");
        testGame.setId(GAME_ID);
    }

    @Test
    @DisplayName("Should create a new game with initial board setup")
    void shouldCreateNewGame() {
        when(gameRepository.findAll()).thenReturn(Flux.empty());
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> {
            Game game = invocation.getArgument(0);
            game.setId(GAME_ID);
            return Mono.just(game);
        });

        StepVerifier.create(chessService.createGame(WHITE_PLAYER, "Player1"))
                .assertNext(game -> {
                    assertThat(game.getWhitePlayerId()).isEqualTo(WHITE_PLAYER);
                    assertThat(game.getStatus()).isEqualTo(GameStatus.WAITING);
                    assertThat(game.getCurrentTurn()).isEqualTo(PieceColor.WHITE);
                    assertThat(game.getBoard()).isNotNull();
                    assertThat(game.getBoard().length).isEqualTo(8);

                    // Verify initial piece positions
                    assertThat(game.getPieceAt(new Position(0, 0)).getType()).isEqualTo(PieceType.ROOK);
                    assertThat(game.getPieceAt(new Position(0, 0)).getColor()).isEqualTo(PieceColor.BLACK);
                    assertThat(game.getPieceAt(new Position(7, 0)).getType()).isEqualTo(PieceType.ROOK);
                    assertThat(game.getPieceAt(new Position(7, 0)).getColor()).isEqualTo(PieceColor.WHITE);
                    assertThat(game.getPieceAt(new Position(1, 0)).getType()).isEqualTo(PieceType.PAWN);
                    assertThat(game.getPieceAt(new Position(6, 0)).getType()).isEqualTo(PieceType.PAWN);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow second player to join and start game")
    void shouldAllowSecondPlayerToJoin() {
        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        StepVerifier.create(chessService.joinGame(GAME_ID, BLACK_PLAYER, "Player2"))
                .assertNext(game -> {
                    assertThat(game.getBlackPlayerId()).isEqualTo(BLACK_PLAYER);
                    assertThat(game.getStatus()).isEqualTo(GameStatus.IN_PROGRESS);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should not allow same player to join twice")
    void shouldNotAllowSamePlayerToJoinTwice() {
        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        StepVerifier.create(chessService.joinGame(GAME_ID, WHITE_PLAYER, "Player1"))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("cannot join your own game"))
                .verify();
    }

    @Test
    @DisplayName("Should not allow joining game that is already in progress")
    void shouldNotAllowJoiningInProgressGame() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);
        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        StepVerifier.create(chessService.joinGame(GAME_ID, "third-player", "Player3"))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("not accepting players"))
                .verify();
    }

    @Test
    @DisplayName("Should allow valid pawn move (one square forward)")
    void shouldAllowValidPawnMove() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Position from = new Position(6, 4); // e2
        Position to = new Position(5, 4);   // e3

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .assertNext(game -> {
                    assertThat(game.getPieceAt(to)).isNotNull();
                    assertThat(game.getPieceAt(to).getType()).isEqualTo(PieceType.PAWN);
                    assertThat(game.getPieceAt(to).getColor()).isEqualTo(PieceColor.WHITE);
                    assertThat(game.getPieceAt(from)).isNull();
                    assertThat(game.getCurrentTurn()).isEqualTo(PieceColor.BLACK);
                    assertThat(game.getMoveHistory()).hasSize(1);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow valid pawn move (two squares forward from start)")
    void shouldAllowPawnTwoSquareMoveFromStart() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Position from = new Position(6, 4); // e2
        Position to = new Position(4, 4);   // e4

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .assertNext(game -> {
                    assertThat(game.getPieceAt(to)).isNotNull();
                    assertThat(game.getPieceAt(to).getType()).isEqualTo(PieceType.PAWN);
                    assertThat(game.getPieceAt(from)).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should allow valid knight move")
    void shouldAllowValidKnightMove() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Position from = new Position(7, 1); // b1 knight
        Position to = new Position(5, 2);   // c3

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .assertNext(game -> {
                    assertThat(game.getPieceAt(to)).isNotNull();
                    assertThat(game.getPieceAt(to).getType()).isEqualTo(PieceType.KNIGHT);
                    assertThat(game.getPieceAt(from)).isNull();
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should not allow move when it's not player's turn")
    void shouldNotAllowMoveWhenNotPlayersTurn() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);
        testGame.setCurrentTurn(PieceColor.WHITE);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        Position from = new Position(1, 4); // Black pawn
        Position to = new Position(2, 4);

        StepVerifier.create(chessService.makeMove(GAME_ID, BLACK_PLAYER, from, to))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("not your turn"))
                .verify();
    }

    @Test
    @DisplayName("Should not allow moving opponent's piece")
    void shouldNotAllowMovingOpponentsPiece() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        Position from = new Position(1, 4); // Black pawn
        Position to = new Position(2, 4);

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("Invalid piece selection"))
                .verify();
    }

    @Test
    @DisplayName("Should not allow invalid pawn move (sideways)")
    void shouldNotAllowInvalidPawnMove() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        Position from = new Position(6, 4); // e2
        Position to = new Position(6, 5);   // f2 (sideways)

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("Invalid move"))
                .verify();
    }

    @Test
    @DisplayName("Should not allow move when path is blocked")
    void shouldNotAllowMoveWhenPathIsBlocked() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        Position from = new Position(7, 0); // a1 rook
        Position to = new Position(5, 0);   // a3 (blocked by pawn at a2)

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("Invalid move"))
                .verify();
    }

    @Test
    @DisplayName("Should allow capturing opponent piece")
    void shouldAllowCapturingOpponentPiece() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        // Move black pawn to position where it can be captured
        testGame.setPieceAt(new Position(5, 5), new ChessPiece(PieceType.PAWN, PieceColor.BLACK, new Position(5, 5)));

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Position from = new Position(6, 4); // e2 white pawn
        Position to = new Position(5, 5);   // f3 diagonal capture

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .assertNext(game -> {
                    assertThat(game.getPieceAt(to)).isNotNull();
                    assertThat(game.getPieceAt(to).getColor()).isEqualTo(PieceColor.WHITE);
                    assertThat(game.getMoveHistory().get(0).getCapturedPiece()).isNotNull();
                    assertThat(game.getMoveHistory().get(0).getCapturedPiece().getColor()).isEqualTo(PieceColor.BLACK);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should not allow capturing own piece")
    void shouldNotAllowCapturingOwnPiece() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        Position from = new Position(7, 1); // b1 knight
        Position to = new Position(6, 3);   // d2 where white pawn is

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .expectErrorMatches(throwable ->
                    throwable instanceof IllegalStateException &&
                    throwable.getMessage().contains("Invalid move"))
                .verify();
    }

    @Test
    @DisplayName("Should end game when king is captured")
    void shouldEndGameWhenKingIsCaptured() {
        testGame.setBlackPlayerId(BLACK_PLAYER);
        testGame.setStatus(GameStatus.IN_PROGRESS);

        // Set up scenario where white can capture black king
        testGame.setPieceAt(new Position(4, 4), new ChessPiece(PieceType.QUEEN, PieceColor.WHITE, new Position(4, 4)));
        testGame.setPieceAt(new Position(3, 4), new ChessPiece(PieceType.KING, PieceColor.BLACK, new Position(3, 4)));

        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));
        when(gameRepository.save(any(Game.class))).thenAnswer(invocation -> Mono.just(invocation.getArgument(0)));

        Position from = new Position(4, 4); // Queen
        Position to = new Position(3, 4);   // King position

        StepVerifier.create(chessService.makeMove(GAME_ID, WHITE_PLAYER, from, to))
                .assertNext(game -> {
                    assertThat(game.getStatus()).isEqualTo(GameStatus.COMPLETED);
                    assertThat(game.getWinner()).isEqualTo(WHITE_PLAYER);
                })
                .verifyComplete();
    }

    @Test
    @DisplayName("Should retrieve existing game by ID")
    void shouldRetrieveGameById() {
        when(gameRepository.findById(GAME_ID)).thenReturn(Mono.just(testGame));

        StepVerifier.create(chessService.getGame(GAME_ID))
                .assertNext(game -> {
                    assertThat(game.getId()).isEqualTo(GAME_ID);
                    assertThat(game.getWhitePlayerId()).isEqualTo(WHITE_PLAYER);
                })
                .verifyComplete();
    }
}