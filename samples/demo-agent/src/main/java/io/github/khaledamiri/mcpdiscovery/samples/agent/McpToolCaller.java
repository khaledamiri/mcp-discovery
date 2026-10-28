/*
 * Copyright 2026 YOUR_NAME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.khaledamiri.mcpdiscovery.samples.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * Calls one tool on an MCP server over Streamable HTTP: initialize, notifications/initialized,
 * tools/call, then closes the session. Accepts both response formats of the transport: plain JSON
 * and a Server-Sent Events stream (which the Spring AI server uses for requests other than initialize).
 * A production agent would use the MCP Java SDK or Spring AI's MCP client instead.
 */
@Component
public class McpToolCaller {

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ObjectMapper mapper;
    private final AtomicLong ids = new AtomicLong();

    public McpToolCaller(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    /** @return the {@code result} of the tools/call response */
    public JsonNode call(String endpoint, String tool, Map<String, Object> arguments) throws IOException, InterruptedException {
        URI uri = URI.create(endpoint);
        long initId = ids.incrementAndGet();
        HttpResponse<Stream<String>> init = post(uri, null, Map.of("jsonrpc", "2.0", "id", initId,
                "method", "initialize", "params", Map.of("protocolVersion", "2025-06-18", "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "demo-agent", "version", "1.0"))));
        readMessage(init, initId, endpoint);
        String session = init.headers().firstValue("Mcp-Session-Id")
                .orElseThrow(() -> new IOException("No session id from " + endpoint));
        try {
            post(uri, session, Map.of("jsonrpc", "2.0", "method", "notifications/initialized")).body().close();
            long id = ids.incrementAndGet();
            JsonNode body = readMessage(post(uri, session, Map.of("jsonrpc", "2.0", "id", id,
                    "method", "tools/call", "params", Map.of("name", tool, "arguments", arguments))), id, endpoint);
            if (body.has("error")) {
                throw new IOException("JSON-RPC error: " + body.path("error").path("message").asText());
            }
            return body.path("result");
        } finally {
            http.send(HttpRequest.newBuilder(uri).header("Mcp-Session-Id", session).DELETE().build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }

    /**
     * Returns the JSON-RPC response with the given id, from a JSON body or from an SSE stream.
     * The stream is read line by line and closed as soon as the response arrives.
     */
    private JsonNode readMessage(HttpResponse<Stream<String>> response, long id, String endpoint) throws IOException {
        try (Stream<String> lines = response.body()) {
            if (response.statusCode() >= 300) {
                throw new IOException("HTTP " + response.statusCode() + " from " + endpoint);
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("");
            if (!contentType.startsWith("text/event-stream")) {
                return mapper.readTree(lines.collect(Collectors.joining("\n")));
            }
            StringBuilder data = new StringBuilder();
            Iterator<String> it = lines.iterator();
            while (it.hasNext()) {
                String line = it.next();
                if (line.startsWith("data:")) {
                    data.append(line.substring(5).trim());
                } else if (line.isEmpty() && !data.isEmpty()) {
                    JsonNode message = mapper.readTree(data.toString());
                    data.setLength(0);
                    if (message.path("id").asLong(-1) == id) {
                        return message;
                    }
                }
            }
            if (!data.isEmpty()) {
                JsonNode message = mapper.readTree(data.toString());
                if (message.path("id").asLong(-1) == id) {
                    return message;
                }
            }
            throw new IOException("No response with id " + id + " from " + endpoint);
        }
    }

    private HttpResponse<Stream<String>> post(URI uri, String session, Object body) throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
        if (session != null) {
            request.header("Mcp-Session-Id", session).header("MCP-Protocol-Version", "2025-06-18");
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofLines());
    }
}
