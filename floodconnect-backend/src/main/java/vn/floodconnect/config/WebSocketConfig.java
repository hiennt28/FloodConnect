package vn.floodconnect.config;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import java.util.List;
import vn.floodconnect.security.JwtService;

/**
 * Thông báo realtime: client kết nối ws://host/ws (STOMP), gửi header Authorization: Bearer <jwt>
 * khi CONNECT rồi subscribe /user/queue/notifications.
 */
@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    private final JwtService jwt;
    @Value("${app.cors.allowed-origins}") private List<String> origins;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry r) {
        r.addEndpoint("/ws").setAllowedOrigins(origins.toArray(new String[0]));
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry r) {
        r.enableSimpleBroker("/queue", "/topic");
        r.setUserDestinationPrefix("/user");
        r.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration reg) {
        reg.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor acc = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (acc != null && StompCommand.CONNECT.equals(acc.getCommand())) {
                    String h = acc.getFirstNativeHeader("Authorization");
                    if (h != null && h.startsWith("Bearer ")) {
                        try {
                            Claims c = jwt.parse(h.substring(7));
                            acc.setUser(new UsernamePasswordAuthenticationToken(c.getSubject(), null, List.of()));
                        } catch (Exception ignored) { /* kết nối ẩn danh: không nhận thông báo riêng */ }
                    }
                }
                return message;
            }
        });
    }
}
