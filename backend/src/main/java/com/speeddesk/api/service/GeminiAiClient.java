package com.speeddesk.api.service;

import com.speeddesk.api.config.AiProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class GeminiAiClient {

    private final AiProperties properties;
    private final JsonMapper jsonMapper;

    public Optional<String> generateStructured(
            String systemInstruction,
            String prompt,
            Map<String, Object> responseSchema
    ) {
        if (!properties.remoteAvailable()) return Optional.empty();
        if (!properties.model().matches("[a-zA-Z0-9._-]+")) return Optional.empty();

        Map<String, Object> payload = Map.of(
                "systemInstruction", Map.of(
                        "parts", List.of(Map.of("text", systemInstruction))
                ),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", prompt))
                )),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "maxOutputTokens", 1200,
                        "responseMimeType", "application/json",
                        "responseSchema", responseSchema
                )
        );

        try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(properties.timeoutSeconds()))
                .build()) {
            URI uri = URI.create(properties.baseUrl().replaceAll("/+$", "")
                    + "/models/" + properties.model() + ":generateContent");
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(Duration.ofSeconds(properties.timeoutSeconds()))
                    .header("Content-Type", "application/json")
                    .header("x-goog-api-key", properties.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(jsonMapper.writeValueAsString(payload)))
                    .build();
            HttpResponse<String> response = client.send(
                    request,
                    HttpResponse.BodyHandlers.ofString()
            );
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return Optional.empty();
            }
            JsonNode root = jsonMapper.readTree(response.body());
            JsonNode text = root.path("candidates")
                    .path(0)
                    .path("content")
                    .path("parts")
                    .path(0)
                    .path("text");
            return text.isTextual() && !text.asString().isBlank()
                    ? Optional.of(text.asString())
                    : Optional.empty();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (IOException | IllegalArgumentException exception) {
            return Optional.empty();
        }
    }
}
