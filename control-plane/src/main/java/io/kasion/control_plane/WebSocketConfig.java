package io.kasion.control_plane;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final LogBroadcaster logBroadcaster;

    public WebSocketConfig(LogBroadcaster logBroadcaster) {
        this.logBroadcaster = logBroadcaster;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new LogStreamWebSocketHandler(logBroadcaster), "/ws/logs/{deploymentId}")
                .setAllowedOrigins("*");
    }
}
