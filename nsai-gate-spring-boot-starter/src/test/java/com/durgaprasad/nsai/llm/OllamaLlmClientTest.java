package com.durgaprasad.nsai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the Ollama client against an in-process HTTP server, so no model is needed.
 */
class OllamaLlmClientTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<String> lastRequestBody = new AtomicReference<>();
    private HttpServer server;
    private int status = 200;
    private String responseBody = "{\"model\":\"llama3.2\",\"response\":\"{\\\"interestRate\\\":\\\"7%\\\"}\",\"done\":true}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/generate", exchange -> {
            lastRequestBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/";
    }

    @Test
    void sendsDeterministicJsonRequestAndReturnsModelText() throws Exception {
        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "llama3.2", Duration.ofSeconds(5), true);

        String text = client.complete("Draft an offer");

        assertThat(text).isEqualTo("{\"interestRate\":\"7%\"}");
        JsonNode request = mapper.readTree(lastRequestBody.get());
        assertThat(request.get("model").asText()).isEqualTo("llama3.2");
        assertThat(request.get("prompt").asText()).isEqualTo("Draft an offer");
        assertThat(request.get("stream").asBoolean()).isFalse();
        assertThat(request.get("format").asText()).isEqualTo("json");
        assertThat(request.at("/options/temperature").asInt()).isZero();
        assertThat(client.name()).isEqualTo("ollama:llama3.2");
    }

    @Test
    void omitsJsonFormatWhenDisabled() throws Exception {
        new OllamaLlmClient(baseUrl(), "m", Duration.ofSeconds(5), false).complete("p");

        assertThat(mapper.readTree(lastRequestBody.get()).has("format")).isFalse();
    }

    @Test
    void httpErrorBecomesLlmException() {
        status = 404;
        responseBody = "{\"error\":\"model 'nope' not found\"}";
        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "nope", Duration.ofSeconds(5), true);

        assertThatThrownBy(() -> client.complete("p"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("HTTP 404")
                .hasMessageContaining("not found");
    }

    @Test
    void missingResponseFieldBecomesLlmException() {
        responseBody = "{\"done\":true}";
        OllamaLlmClient client = new OllamaLlmClient(baseUrl(), "m", Duration.ofSeconds(5), true);

        assertThatThrownBy(() -> client.complete("p"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("no 'response' field");
    }

    @Test
    void unreachableServerBecomesLlmException() {
        int port = server.getAddress().getPort();
        server.stop(0);
        OllamaLlmClient client = new OllamaLlmClient("http://127.0.0.1:" + port, "m", Duration.ofSeconds(2), true);

        assertThatThrownBy(() -> client.complete("p"))
                .isInstanceOf(LlmException.class)
                .hasMessageContaining("Cannot reach Ollama");
    }
}
