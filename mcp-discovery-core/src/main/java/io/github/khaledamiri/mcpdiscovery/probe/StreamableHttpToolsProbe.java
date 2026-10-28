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
package io.github.khaledamiri.mcpdiscovery.probe;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.github.khaledamiri.mcpdiscovery.registry.InvalidRegistrationException;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

/**
 * Lists the tools of an MCP server over the Streamable HTTP transport:
 * {@code initialize} → {@code notifications/initialized} → {@code tools/list} (paginated),
 * then closes the session. Responses may be plain JSON or a Server-Sent Events stream.
 */
public class StreamableHttpToolsProbe implements ToolsProbe {

    static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final String VERSION_HEADER = "MCP-Protocol-Version";
    private static final int MAX_PAGES = 50;
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final String clientVersion;
    private final AtomicLong ids = new AtomicLong();

    public StreamableHttpToolsProbe(HttpClient http, ObjectMapper mapper, Duration timeout,
                                    String clientVersion) {
        this.http = http;
        this.mapper = mapper;
        this.timeout = timeout;
        this.clientVersion = clientVersion;
    }

    @Override
    public List<ToolDescriptor> fetchTools(String endpoint) {
        URI uri;
        try {
            uri = URI.create(endpoint);
        } catch (IllegalArgumentException e) {
            throw new ProbeException("Invalid endpoint " + endpoint, e);
        }
        Session session = initialize(uri);
        try {
            List<ToolDescriptor> tools = new ArrayList<>();
            String cursor = null;
            int pages = 0;
            do {
                ObjectNode params = mapper.createObjectNode();
                if (cursor != null) {
                    params.put("cursor", cursor);
                }
                JsonNode result = call(uri, session, "tools/list", params);
                for (JsonNode tool : result.path("tools")) {
                    try {
                        tools.add(toDescriptor(tool));
                    } catch (InvalidRegistrationException e) {
                        // Skip malformed tool entries (e.g. missing name).
                    }
                }
                JsonNode next = result.get("nextCursor");
                cursor = next == null || next.isNull() || next.asText().isEmpty() ? null : next.asText();
            } while (cursor != null && ++pages < MAX_PAGES);
            return tools;
        } finally {
            close(uri, session);
        }
    }

    // ------------------------------------------------------------------ protocol steps

    private Session initialize(URI uri) {
        ObjectNode params = mapper.createObjectNode();
        params.put("protocolVersion", PROTOCOL_VERSION);
        params.putObject("capabilities");
        params.putObject("clientInfo").put("name", "mcp-discovery").put("version", clientVersion);

        long id = ids.incrementAndGet();
        HttpResponse<Stream<String>> response = post(uri, new Session(null, null), request(id, "initialize", params));
        String sessionId = response.headers().firstValue(SESSION_HEADER).orElse(null);
        JsonNode result = resultOf(readResponse(response, id));
        String version = result.path("protocolVersion").asText(PROTOCOL_VERSION);
        Session session = new Session(sessionId, version);

        ObjectNode notification = mapper.createObjectNode();
        notification.put("jsonrpc", "2.0");
        notification.put("method", "notifications/initialized");
        HttpResponse<Stream<String>> ack = post(uri, session, notification);
        ack.body().close();
        if (ack.statusCode() >= 300) {
            throw new ProbeException("notifications/initialized rejected with HTTP " + ack.statusCode());
        }
        return session;
    }

    private JsonNode call(URI uri, Session session, String method, JsonNode params) {
        long id = ids.incrementAndGet();
        return resultOf(readResponse(post(uri, session, request(id, method, params)), id));
    }

    private void close(URI uri, Session session) {
        if (session.id() == null) {
            return;
        }
        try {
            HttpRequest delete = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header(SESSION_HEADER, session.id())
                    .header(VERSION_HEADER, session.protocolVersion())
                    .DELETE()
                    .build();
            http.send(delete, HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            // Best effort: servers may not support explicit session termination (405).
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------------ transport

    private ObjectNode request(long id, String method, JsonNode params) {
        ObjectNode node = mapper.createObjectNode();
        node.put("jsonrpc", "2.0");
        node.put("id", id);
        node.put("method", method);
        node.set("params", params);
        return node;
    }

    private HttpResponse<Stream<String>> post(URI uri, Session session, JsonNode body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)));
            if (session.id() != null) {
                builder.header(SESSION_HEADER, session.id());
            }
            if (session.protocolVersion() != null) {
                builder.header(VERSION_HEADER, session.protocolVersion());
            }
            return http.send(builder.build(), HttpResponse.BodyHandlers.ofLines());
        } catch (IOException e) {
            throw new ProbeException("Cannot reach " + uri + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProbeException("Interrupted while probing " + uri, e);
        }
    }

    private JsonNode readResponse(HttpResponse<Stream<String>> response, long id) {
        try (Stream<String> lines = response.body()) {
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                throw new ProbeException("HTTP " + status + " from " + response.uri());
            }
            String contentType = response.headers().firstValue("Content-Type").orElse("")
                    .toLowerCase(Locale.ROOT);
            if (contentType.startsWith("text/event-stream")) {
                return readEventStream(lines.iterator(), id);
            }
            String body = lines.collect(Collectors.joining("\n"));
            JsonNode node = parse(body);
            if (node != null && node.isArray()) {
                for (JsonNode message : node) {
                    if (isResponseTo(message, id)) {
                        return message;
                    }
                }
            } else if (isResponseTo(node, id)) {
                return node;
            }
            throw new ProbeException("No JSON-RPC response with id " + id);
        } catch (UncheckedIOException e) {
            throw new ProbeException("Error reading response: " + e.getMessage(), e);
        }
    }

    /** Reads SSE events until the response matching {@code id} arrives, then stops. */
    private JsonNode readEventStream(Iterator<String> lines, long id) {
        StringBuilder data = new StringBuilder();
        while (lines.hasNext()) {
            String line = lines.next();
            if (line.isEmpty()) {
                JsonNode message = parse(data.toString());
                data.setLength(0);
                if (isResponseTo(message, id)) {
                    return message;
                }
            } else if (line.startsWith("data:")) {
                String value = line.substring(5);
                if (value.startsWith(" ")) {
                    value = value.substring(1);
                }
                if (!data.isEmpty()) {
                    data.append('\n');
                }
                data.append(value);
            }
            // Other fields (event:, id:, retry:, comments) are ignored.
        }
        JsonNode last = parse(data.toString());
        if (isResponseTo(last, id)) {
            return last;
        }
        throw new ProbeException("Event stream closed without a response to request " + id);
    }

    private JsonNode parse(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        try {
            return mapper.readTree(text);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private static boolean isResponseTo(JsonNode message, long id) {
        return message != null && message.isObject()
                && message.path("id").canConvertToLong() && message.path("id").asLong() == id
                && (message.has("result") || message.has("error"));
    }

    private static JsonNode resultOf(JsonNode message) {
        if (message.has("error")) {
            JsonNode error = message.get("error");
            throw new ProbeException("JSON-RPC error " + error.path("code").asText() + ": "
                    + error.path("message").asText());
        }
        return message.path("result");
    }

    private ToolDescriptor toDescriptor(JsonNode tool) {
        String title = tool.hasNonNull("title")
                ? tool.get("title").asText()
                : tool.path("annotations").path("title").asText(null);
        Map<String, Object> schema = tool.has("inputSchema")
                ? mapper.convertValue(tool.get("inputSchema"), MAP)
                : Map.of();
        return new ToolDescriptor(tool.path("name").asText(null), title,
                tool.path("description").asText(""), schema);
    }

    private record Session(String id, String protocolVersion) {
    }
}
