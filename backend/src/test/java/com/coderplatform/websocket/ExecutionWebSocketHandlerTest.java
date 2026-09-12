package com.coderplatform.websocket;

import com.coderplatform.model.CodeExecutionResponse;
import com.coderplatform.service.LiveExecutionListener;
import com.coderplatform.service.LiveExecutionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExecutionWebSocketHandlerTest {

    @Mock
    private LiveExecutionService liveExecutionService;

    @Mock
    private WebSocketSession session;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final List<String> sent = new ArrayList<>();
    private ExecutionWebSocketHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        handler = new ExecutionWebSocketHandler(liveExecutionService, objectMapper);
        lenient().when(session.getId()).thenReturn("sess-1");
        lenient().when(session.isOpen()).thenReturn(true);
        lenient().doAnswer(invocation -> {
            TextMessage message = invocation.getArgument(0);
            sent.add(message.getPayload());
            return null;
        }).when(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void startDelegatesToLiveService() throws Exception {
        when(liveExecutionService.start(eq("sess-1"), eq("python"), eq("print(1)"), eq(""), any()))
                .thenReturn("exec-1");

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"start","language":"python","code":"print(1)","stdin":""}
                """));

        verify(liveExecutionService).start(eq("sess-1"), eq("python"), eq("print(1)"), eq(""), any());
    }

    @Test
    void streamsListenerEventsToClient() throws Exception {
        doAnswer(invocation -> {
            LiveExecutionListener listener = invocation.getArgument(4);
            listener.onStarted("exec-1");
            listener.onStdout("hello\\n".replace("\\n", "\n"));
            listener.onStderr("warn");
            listener.onCompleted(CodeExecutionResponse.success("hello\n", 12));
            return "exec-1";
        }).when(liveExecutionService).start(eq("sess-1"), eq("python"), eq("print(1)"), eq(""), any());

        handler.handleTextMessage(session, new TextMessage("""
                {"type":"start","language":"python","code":"print(1)","stdin":""}
                """));

        assertThat(sent).hasSize(4);
        assertThat(json(sent.get(0)).get("type").asText()).isEqualTo("started");
        assertThat(json(sent.get(1)).get("type").asText()).isEqualTo("stdout");
        assertThat(json(sent.get(2)).get("type").asText()).isEqualTo("stderr");
        JsonNode done = json(sent.get(3));
        assertThat(done.get("type").asText()).isEqualTo("done");
        assertThat(done.get("status").asText()).isEqualTo("SUCCESS");
        assertThat(done.get("executionTime").asLong()).isEqualTo(12);
    }

    @Test
    void stdinAndStopUseSessionOwner() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"stdin\",\"data\":\"Ada\\n\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"eof\"}"));
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"stop\"}"));

        verify(liveExecutionService).writeStdin("sess-1", "Ada\n");
        verify(liveExecutionService).closeStdin("sess-1");
        verify(liveExecutionService).stopOwner("sess-1");
    }

    @Test
    void closingSocketReleasesOwner() {
        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        verify(liveExecutionService).releaseOwner("sess-1");
    }

    @Test
    void unknownTypeReturnsError() throws Exception {
        handler.handleTextMessage(session, new TextMessage("{\"type\":\"explode\"}"));
        assertThat(json(sent.get(0)).get("type").asText()).isEqualTo("error");
        assertThat(json(sent.get(0)).get("message").asText()).contains("Unknown");
    }

    private JsonNode json(String payload) throws Exception {
        return objectMapper.readTree(payload);
    }
}
