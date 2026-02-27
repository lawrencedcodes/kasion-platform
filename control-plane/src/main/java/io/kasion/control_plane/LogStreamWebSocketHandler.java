package io.kasion.control_plane;

import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Objects;

public class LogStreamWebSocketHandler extends TextWebSocketHandler {

    private final LogBroadcaster logBroadcaster;

    public LogStreamWebSocketHandler(LogBroadcaster logBroadcaster) {
        this.logBroadcaster = logBroadcaster;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String deploymentId = getDeploymentId(session);
        if (deploymentId != null) {
            logBroadcaster.register(deploymentId, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String deploymentId = getDeploymentId(session);
        if (deploymentId != null) {
            logBroadcaster.unregister(deploymentId, session);
        }
    }

    private String getDeploymentId(WebSocketSession session) {
        String path = Objects.requireNonNull(session.getUri()).getPath();
        String[] segments = path.split("/");
        if (segments.length > 0) {
            return segments[segments.length - 1];
        }
        return null;
    }
}
