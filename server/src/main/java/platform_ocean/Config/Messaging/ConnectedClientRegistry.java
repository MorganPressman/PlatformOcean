package platform_ocean.Config.Messaging;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConnectedClientRegistry {

    private final Map<UUID, Set<String>> clientSessions = new ConcurrentHashMap<>();
    private final Set<UUID> connectedClients = ConcurrentHashMap.newKeySet();
    private final Map<String, UUID> sessionToClient = new ConcurrentHashMap<>();

    @EventListener
    public void onConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String clientIdStr = accessor.getFirstNativeHeader("clientID");
        String sessionId = accessor.getSessionId();
        if (clientIdStr == null || sessionId == null) return;

        try {
            UUID clientId = UUID.fromString(clientIdStr);
            clientSessions.computeIfAbsent(clientId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
            connectedClients.add(clientId);
            sessionToClient.put(sessionId, clientId);
        } catch (IllegalArgumentException ignored) {}
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        UUID clientId = sessionToClient.remove(sessionId);
        if (clientId == null) return;

        Set<String> sessions = clientSessions.get(clientId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                clientSessions.remove(clientId);
                connectedClients.remove(clientId);
            }
        }
    }

    public Set<UUID> getConnectedClients() {
        return Collections.unmodifiableSet(connectedClients);
    }

    public boolean isConnected(UUID clientId) {
        return connectedClients.contains(clientId);
    }
}
