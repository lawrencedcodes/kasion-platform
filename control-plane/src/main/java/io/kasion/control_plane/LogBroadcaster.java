package io.kasion.control_plane;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class LogBroadcaster {

    private final ConcurrentHashMap<String, List<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public void register(String deploymentId, WebSocketSession session) {
        sessions.computeIfAbsent(deploymentId, k -> new CopyOnWriteArrayList<>()).add(session);
    }

    public void unregister(String deploymentId, WebSocketSession session) {
        if (sessions.containsKey(deploymentId)) {
            sessions.get(deploymentId).remove(session);
        }
    }

    public void broadcast(String deploymentId, String message) {
        if (sessions.containsKey(deploymentId)) {
            TextMessage textMessage = new TextMessage(message);
            for (WebSocketSession session : sessions.get(deploymentId)) {
                try {
                    session.sendMessage(textMessage);
                } catch (IOException e) {
                    // Handle exception, e.g., log it
                }
            }
        }
    }
}
