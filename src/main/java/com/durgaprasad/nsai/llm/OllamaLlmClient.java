package com.durgaprasad.nsai.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Calls a local <a href="https://ollama.com">Ollama</a> server through its
 * {@code /api/generate} endpoint. Uses only the JDK HTTP client, so no extra
 * dependency is needed.
 */
public class OllamaLlmClient implements LlmClient {

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final URI generateUri;
    private final String model;
    private final Duration timeout;
    private final boolean jsonOutput;

    public OllamaLlmClient(String baseUrl, String model, Duration timeout, boolean jsonOutput) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build(),
                new ObjectMapper(), baseUrl, model, timeout, jsonOutput);
    }

    OllamaLlmClient(HttpClient http, ObjectMapper mapper, String baseUrl, String model,
                    Duration timeout, boolean jsonOutput) {
        this.http = http;
        this.mapper = mapper;
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.generateUri = URI.create(base + "/api/generate");
        this.model = model;
        this.timeout = timeout;
        this.jsonOutput = jsonOutput;
    }

    @Override
    public String complete(String prompt) {
        ObjectNode body = mapper.createObjectNode()
                .put("model", model)
                .put("prompt", prompt)
                .put("stream", false);
        if (jsonOutput) {
            body.put("format", "json");
        }
        body.putObject("options").put("temperature", 0);

        HttpRequest request = HttpRequest.newBuilder(generateUri)
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new LlmException("Ollama returned HTTP " + response.statusCode() + ": " + response.body());
            }
            JsonNode json = mapper.readTree(response.body());
            JsonNode text = json.get("response");
            if (text == null || !text.isTextual()) {
                throw new LlmException("Ollama response has no 'response' field: " + response.body());
            }
            return text.asText();
        } catch (IOException e) {
            String reason = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            throw new LlmException("Cannot reach Ollama at " + generateUri + ": " + reason, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Interrupted while waiting for Ollama", e);
        }
    }

    @Override
    public String name() {
        return "ollama:" + model;
    }
}
