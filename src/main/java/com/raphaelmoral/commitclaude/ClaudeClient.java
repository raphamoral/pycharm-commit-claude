package com.raphaelmoral.commitclaude;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Cliente mínimo da Messages API da Anthropic (sem SDK, usando java.net.http).
 */
public final class ClaudeClient {

    private static final String ENDPOINT = "https://api.anthropic.com/v1/messages";
    private static final String ANTHROPIC_VERSION = "2023-06-01";
    private static final int MAX_DIFF_CHARS = 100_000;

    private ClaudeClient() {
    }

    public static String generateCommitMessage(String diff, ClaudeSettings.State settings, String apiKey)
            throws Exception {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "API key da Anthropic não configurada. Vá em Settings > Tools > Claude Commit Message.");
        }

        String trimmedDiff = diff.length() > MAX_DIFF_CHARS
                ? diff.substring(0, MAX_DIFF_CHARS) + "\n\n[... diff truncado ...]"
                : diff;

        JsonObject userMsg = new JsonObject();
        userMsg.addProperty("role", "user");
        userMsg.addProperty("content", Prompts.user(trimmedDiff));

        JsonArray messages = new JsonArray();
        messages.add(userMsg);

        JsonObject body = new JsonObject();
        body.addProperty("model", settings.model);
        body.addProperty("max_tokens", settings.maxTokens);
        body.addProperty("system", Prompts.system(settings));
        body.add("messages", messages);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ENDPOINT))
                .timeout(Duration.ofSeconds(120))
                .header("content-type", "application/json")
                .header("x-api-key", apiKey)
                .header("anthropic-version", ANTHROPIC_VERSION)
                .POST(HttpRequest.BodyPublishers.ofString(new Gson().toJson(body)))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() / 100 != 2) {
            throw new RuntimeException("Anthropic API retornou " + response.statusCode() + ": " + response.body());
        }

        JsonObject json = JsonParser.parseString(response.body()).getAsJsonObject();
        JsonArray content = json.getAsJsonArray("content");
        StringBuilder text = new StringBuilder();
        if (content != null) {
            for (JsonElement el : content) {
                JsonObject block = el.getAsJsonObject();
                JsonElement type = block.get("type");
                if (type != null && "text".equals(type.getAsString())) {
                    text.append(block.get("text").getAsString());
                }
            }
        }
        return text.toString().trim();
    }
}
