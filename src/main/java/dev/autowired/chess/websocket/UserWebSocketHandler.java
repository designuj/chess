package dev.autowired.chess.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.autowired.chess.model.ConnectedUser;
import dev.autowired.chess.service.ChessGameService;
import dev.autowired.chess.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserWebSocketHandler implements WebSocketHandler {

    private final UserSessionService userSessionService;
    private final ChessGameService chessGameService;
    private final ObjectMapper objectMapper;

    // Store all active WebSocket sessions
    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    // Sink for broadcasting messages to all connected clients
    private final Sinks.Many<String> broadcastSink = Sinks.many().multicast().onBackpressureBuffer();

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = session.getId();
        sessions.put(sessionId, session);

        log.info("WebSocket session opened: {}", sessionId);

        // Handle incoming messages
        Mono<Void> input = session.receive()
                .map(WebSocketMessage::getPayloadAsText)
                .flatMap(message -> handleIncomingMessage(session, message))
                .doFinally(signalType -> {
                    sessions.remove(sessionId);
                    userSessionService.removeUser(sessionId);
                    broadcastUserList();
                    log.info("WebSocket session closed: {} ({})", sessionId, signalType);
                })
                .then();

        // Send messages from broadcast sink
        Mono<Void> output = session.send(
                broadcastSink.asFlux()
                        .map(session::textMessage)
        );

        return Flux.merge(input, output).then();
    }

    private Mono<Void> handleIncomingMessage(WebSocketSession session, String message) {
        return Mono.fromRunnable(() -> {
            try {
                log.info("Received message: {}", message);

                @SuppressWarnings("unchecked")
                Map<String, Object> messageMap = objectMapper.readValue(message, Map.class);
                String type = (String) messageMap.get("type");

                switch (type) {
                    case "CONNECT":
                        handleConnect(session, messageMap);
                        break;
                    case "PING":
                        handlePing(session);
                        break;
                    case "SEND_INVITATION":
                        handleSendInvitation(messageMap);
                        break;
                    case "ACCEPT_INVITATION":
                        handleAcceptInvitation(messageMap);
                        break;
                    case "DECLINE_INVITATION":
                        handleDeclineInvitation(messageMap);
                        break;
                    default:
                        log.warn("Unknown message type: {}", type);
                }
            } catch (Exception e) {
                log.error("Error handling message: {}", e.getMessage(), e);
            }
        });
    }

    private void handleConnect(WebSocketSession session, Map<String, Object> messageMap) {
        String userId = (String) messageMap.get("userId");
        String userName = (String) messageMap.get("userName");

        if (userId != null && userName != null) {
            ConnectedUser user = userSessionService.addUser(userId, userName, session.getId());
            log.info("User registered via WebSocket: {} ({})", userName, userId);

            // Send confirmation to the user
            sendToSession(session, Map.of(
                    "type", "CONNECTED",
                    "userId", userId,
                    "userName", userName
            ));

            // Broadcast updated user list to all clients
            broadcastUserList();
        }
    }

    private void handlePing(WebSocketSession session) {
        userSessionService.updateActivity(session.getId());
        sendToSession(session, Map.of("type", "PONG"));
    }

    private void broadcastUserList() {
        List<ConnectedUser> users = userSessionService.getAllConnectedUsers();

        Map<String, Object> message = new HashMap<>();
        message.put("type", "USER_LIST");
        message.put("users", users.stream().map(user -> {
            Map<String, String> userMap = new HashMap<>();
            userMap.put("userId", user.getUserId());
            userMap.put("userName", user.getUserName());
            return userMap;
        }).toList());

        broadcast(message);
    }

    private void sendToSession(WebSocketSession session, Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            session.send(Mono.just(session.textMessage(json))).subscribe();
        } catch (Exception e) {
            log.error("Error sending message to session: {}", e.getMessage(), e);
        }
    }

    private void broadcast(Map<String, Object> message) {
        try {
            String json = objectMapper.writeValueAsString(message);
            broadcastSink.tryEmitNext(json);
            log.debug("Broadcasting message: {}", json);
        } catch (Exception e) {
            log.error("Error broadcasting message: {}", e.getMessage(), e);
        }
    }

    private void handleSendInvitation(Map<String, Object> messageMap) {
        String fromUserId = (String) messageMap.get("fromUserId");
        String fromUserName = (String) messageMap.get("fromUserName");
        String toUserId = (String) messageMap.get("toUserId");
        String toUserName = (String) messageMap.get("toUserName");

        log.info("Invitation from {} to {}", fromUserName, toUserName);

        // Find the target user's session
        ConnectedUser targetUser = userSessionService.getUserById(toUserId);
        if (targetUser != null) {
            WebSocketSession targetSession = sessions.get(targetUser.getSessionId());
            if (targetSession != null) {
                // Send invitation to target user (no game created yet)
                sendToSession(targetSession, Map.of(
                        "type", "INVITATION_RECEIVED",
                        "fromUserId", fromUserId,
                        "fromUserName", fromUserName
                ));
            }
        }
    }

    private void handleAcceptInvitation(Map<String, Object> messageMap) {
        String fromUserId = (String) messageMap.get("fromUserId");
        String fromUserName = (String) messageMap.get("fromUserName");
        String toUserId = (String) messageMap.get("toUserId");
        String toUserName = (String) messageMap.get("toUserName");

        log.info("Invitation accepted by {} (accepter will play as white)", toUserName);

        // Accepter creates the game and plays as white
        chessGameService.createGame(toUserId, toUserName)
                .flatMap(game -> {
                    log.info("Game created by accepter {}: {}", toUserName, game.getId());

                    // Sender joins as black player
                    return chessGameService.joinGame(game.getId(), fromUserId, fromUserName)
                            .map(updatedGame -> {
                                log.info("Inviter {} joined game {} as black", fromUserName, game.getId());
                                return updatedGame;
                            });
                })
                .subscribe(game -> {
                    // Send game ID to both users
                    ConnectedUser inviterUser = userSessionService.getUserById(fromUserId);
                    ConnectedUser accepterUser = userSessionService.getUserById(toUserId);

                    if (inviterUser != null) {
                        WebSocketSession inviterSession = sessions.get(inviterUser.getSessionId());
                        if (inviterSession != null) {
                            sendToSession(inviterSession, Map.of(
                                    "type", "INVITATION_ACCEPTED",
                                    "gameId", game.getId(),
                                    "fromUserName", toUserName
                            ));
                        }
                    }

                    if (accepterUser != null) {
                        WebSocketSession accepterSession = sessions.get(accepterUser.getSessionId());
                        if (accepterSession != null) {
                            sendToSession(accepterSession, Map.of(
                                    "type", "INVITATION_ACCEPTED",
                                    "gameId", game.getId(),
                                    "fromUserName", fromUserName
                            ));
                        }
                    }
                }, error -> {
                    log.error("Error creating/joining game: {}", error.getMessage());
                });
    }

    private void handleDeclineInvitation(Map<String, Object> messageMap) {
        String fromUserId = (String) messageMap.get("fromUserId");
        String fromUserName = (String) messageMap.get("fromUserName");
        String toUserId = (String) messageMap.get("toUserId");
        String toUserName = (String) messageMap.get("toUserName");

        log.info("Invitation declined by {}", toUserName);

        // Notify the inviter (no game to delete since it wasn't created yet)
        ConnectedUser inviterUser = userSessionService.getUserById(fromUserId);
        if (inviterUser != null) {
            WebSocketSession inviterSession = sessions.get(inviterUser.getSessionId());
            if (inviterSession != null) {
                sendToSession(inviterSession, Map.of(
                        "type", "INVITATION_DECLINED",
                        "fromUserName", toUserName
                ));
            }
        }
    }
}