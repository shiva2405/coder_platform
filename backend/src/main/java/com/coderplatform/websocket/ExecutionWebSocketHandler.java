package com.coderplatform.websocket;

import com.coderplatform.auth.AuthContext;
import com.coderplatform.exception.RateLimitExceededException;
import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.model.LiveClientMessage;
import com.coderplatform.model.LiveServerMessage;
import com.coderplatform.model.User;
import com.coderplatform.service.ClientKey;
import com.coderplatform.service.ExecutionQuotaService;
import com.coderplatform.service.LiveExecutionListener;
import com.coderplatform.service.LiveExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;

@Component
public class ExecutionWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(ExecutionWebSocketHandler.class);

    private final LiveExecutionService liveExecutionService;
    private final ObjectMapper objectMapper;
    private final ExecutionQuotaService quotaService;

    public ExecutionWebSocketHandler(
            LiveExecutionService liveExecutionService,
            ObjectMapper objectMapper,
            ExecutionQuotaService quotaService
    ) {
        this.liveExecutionService = liveExecutionService;
        this.objectMapper = objectMapper;
        this.quotaService = quotaService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        logger.debug("Live execution socket opened {}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        LiveClientMessage clientMessage;
        try {
            clientMessage = objectMapper.readValue(message.getPayload(), LiveClientMessage.class);
        } catch (Exception e) {
            send(session, LiveServerMessage.error("Invalid message"));
            return;
        }

        String type = clientMessage.getType() == null ? "" : clientMessage.getType().trim().toLowerCase();
        try {
            switch (type) {
                case "start" -> handleStart(session, clientMessage);
                case "stdin" -> liveExecutionService.writeStdin(session.getId(), clientMessage.getData());
                case "eof" -> liveExecutionService.closeStdin(session.getId());
                case "stop" -> liveExecutionService.stopOwner(session.getId());
                default -> send(session, LiveServerMessage.error("Unknown message type"));
            }
        } catch (RateLimitExceededException e) {
            send(session, LiveServerMessage.rejected(e.getMessage(), e.getRetryAfterSeconds(), e.getReason()));
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(session, LiveServerMessage.error(e.getMessage()));
        } catch (IOException e) {
            send(session, LiveServerMessage.error("Failed to write input"));
        } catch (Exception e) {
            logger.error("Live execution socket {} failed to handle {}", session.getId(), type, e);
            send(session, LiveServerMessage.error("Execution failed: " + e.getMessage()));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        liveExecutionService.releaseOwner(session.getId());
        logger.debug("Live execution socket closed {} ({})", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.warn("Live execution socket {} transport error", session.getId(), exception);
        liveExecutionService.releaseOwner(session.getId());
    }

    private void handleStart(WebSocketSession session, LiveClientMessage message) {
        User user = (User) session.getAttributes().get(AuthContext.USER_ATTR);
        String ip = (String) session.getAttributes().get(AuthContext.CLIENT_IP_ATTR);
        if (ip == null || ip.isBlank()) {
            ip = "unknown";
        }
        quotaService.consume(user, ip);
        liveExecutionService.start(
                session.getId(),
                message.getLanguage(),
                message.getCode(),
                message.getStdin(),
                ClientKey.of(user, ip),
                new SocketListener(session)
        );
    }

    private void send(WebSocketSession session, LiveServerMessage payload) {
        if (session == null || !session.isOpen()) {
            return;
        }
        synchronized (session) {
            if (!session.isOpen()) {
                return;
            }
            try {
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
            } catch (Exception e) {
                logger.debug("Failed to send live message to {}", session.getId(), e);
            }
        }
    }

    private final class SocketListener implements LiveExecutionListener {
        private final WebSocketSession session;

        private SocketListener(WebSocketSession session) {
            this.session = session;
        }

        @Override
        public void onQueued(String executionId, int position, long estimatedWaitMs) {
            send(session, LiveServerMessage.queued(executionId, position, estimatedWaitMs));
        }

        @Override
        public void onStarted(String executionId) {
            send(session, LiveServerMessage.started(executionId));
        }

        @Override
        public void onStdout(String chunk) {
            send(session, LiveServerMessage.stdout(chunk));
        }

        @Override
        public void onStderr(String chunk) {
            send(session, LiveServerMessage.stderr(chunk));
        }

        @Override
        public void onCompleted(CodeExecutionResponse result) {
            send(session, LiveServerMessage.done(result));
        }

        @Override
        public void onRejected(String message, long retryAfterSeconds, String reason) {
            send(session, LiveServerMessage.rejected(message, retryAfterSeconds, reason));
        }
    }
}
