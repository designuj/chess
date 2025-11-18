package dev.autowired.chess.model;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ConnectedUser {
    private String userId;
    private String userName;
    private String sessionId;
    private LocalDateTime connectedAt;
    private LocalDateTime lastActivity;

    public ConnectedUser(String userId, String userName, String sessionId) {
        this.userId = userId;
        this.userName = userName;
        this.sessionId = sessionId;
        this.connectedAt = LocalDateTime.now();
        this.lastActivity = LocalDateTime.now();
    }

    public void updateActivity() {
        this.lastActivity = LocalDateTime.now();
    }
}