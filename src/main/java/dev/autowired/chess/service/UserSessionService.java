package dev.autowired.chess.service;

import dev.autowired.chess.model.ConnectedUser;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class UserSessionService {

    private final Map<String, ConnectedUser> connectedUsers = new ConcurrentHashMap<>();

    public ConnectedUser addUser(String userId, String userName, String sessionId) {
        ConnectedUser user = new ConnectedUser(userId, userName, sessionId);
        connectedUsers.put(sessionId, user);
        log.info("User connected: {} ({})", userName, userId);
        return user;
    }

    public void removeUser(String sessionId) {
        ConnectedUser user = connectedUsers.remove(sessionId);
        if (user != null) {
            log.info("User disconnected: {} ({})", user.getUserName(), user.getUserId());
        }
    }

    public ConnectedUser getUser(String sessionId) {
        return connectedUsers.get(sessionId);
    }

    public ConnectedUser getUserById(String userId) {
        return connectedUsers.values().stream()
                .filter(user -> user.getUserId().equals(userId))
                .findFirst()
                .orElse(null);
    }

    public void updateActivity(String sessionId) {
        ConnectedUser user = connectedUsers.get(sessionId);
        if (user != null) {
            user.updateActivity();
        }
    }

    public List<ConnectedUser> getAllConnectedUsers() {
        return List.copyOf(connectedUsers.values());
    }

    public int getConnectedUserCount() {
        return connectedUsers.size();
    }

    public boolean isUserConnected(String userId) {
        return connectedUsers.values().stream()
                .anyMatch(user -> user.getUserId().equals(userId));
    }
}