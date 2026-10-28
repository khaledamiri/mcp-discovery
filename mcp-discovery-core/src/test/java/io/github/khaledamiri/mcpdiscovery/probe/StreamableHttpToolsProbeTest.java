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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

class StreamableHttpToolsProbeTest {

    private static final String SESSION = "session-42";

    private final ObjectMapper mapper = new ObjectMapper();
    private final List<String> calls = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private StreamableHttpToolsProbe probe;
    private String endpoint;
    private volatile boolean failToolsList;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/mcp", this::handle);
        server.start();
        endpoint = "http://127.0.0.1:" + server.getAddress().getPort() + "/mcp";
        probe = new StreamableHttpToolsProbe(HttpClient.newHttpClient(), mapper, Duration.ofSeconds(5), "test");
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    /** Fake MCP server: JSON for initialize, SSE for tools/list, two pages of tools. */
    private void handle(HttpExchange exchange) throws IOException {
        if ("DELETE".equals(exchange.getRequestMethod())) {
            calls.add("DELETE " + exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        JsonNode request = mapper.readTree(exchange.getRequestBody());
        String method = request.path("method").asText();
        String session = exchange.getRequestHeaders().getFirst("Mcp-Session-Id");
        calls.add(method + " session=" + session);

        switch (method) {
            case "initialize" -> {
                exchange.getResponseHeaders().add("Mcp-Session-Id", SESSION);
                json(exchange, """
                        {"jsonrpc":"2.0","id":%d,"result":{"protocolVersion":"2025-06-18",
                         "capabilities":{"tools":{}},"serverInfo":{"name":"fake","version":"1"}}}
                        """.formatted(request.get("id").asLong()));
            }
            case "notifications/initialized" -> {
                exchange.sendResponseHeaders(202, -1);
                exchange.close();
            }
            case "tools/list" -> {
                long id = request.get("id").asLong();
                if (failToolsList) {
                    json(exchange, """
                            {"jsonrpc":"2.0","id":%d,"error":{"code":-32601,"message":"Method not found"}}
                            """.formatted(id));
                    return;
                }
                boolean secondPage = "page-2".equals(request.path("params").path("cursor").asText());
                String result = secondPage
                        ? """
                          {"tools":[{"name":"create_issue","description":"Create an issue","inputSchema":{"type":"object"}}]}"""
                        : """
                          {"tools":[{"name":"search_issues","title":"Search issues","description":"Search Jira issues","inputSchema":{"type":"object","properties":{"jql":{"type":"string"}}}}],"nextCursor":"page-2"}""";
                sse(exchange, ": keep-alive comment\n\n"
                        + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"method\":\"notifications/message\",\"params\":{}}\n\n"
                        + "event: message\ndata: {\"jsonrpc\":\"2.0\",\"id\":" + id + ",\"result\":" + result + "}\n\n");
            }
            default -> {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
            }
        }
    }

    private static void json(HttpExchange exchange, String body) throws IOException {
        send(exchange, "application/json", body);
    }

    private static void sse(HttpExchange exchange, String body) throws IOException {
        send(exchange, "text/event-stream", body);
    }

    private static void send(HttpExchange exchange, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(200, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void listsToolsAcrossPagesAndSseResponses() {
        List<ToolDescriptor> tools = probe.fetchTools(endpoint);

        assertEquals(List.of("search_issues", "create_issue"), tools.stream().map(ToolDescriptor::name).toList());
        assertEquals("Search issues", tools.get(0).title());
        assertEquals("object", tools.get(0).inputSchema().get("type"));
    }

    @Test
    void followsTheMcpHandshakeAndClosesTheSession() {
        probe.fetchTools(endpoint);

        assertEquals(List.of(
                "initialize session=null",
                "notifications/initialized session=" + SESSION,
                "tools/list session=" + SESSION,
                "tools/list session=" + SESSION,
                "DELETE " + SESSION), calls);
    }

    @Test
    void surfacesJsonRpcErrors() {
        failToolsList = true;

        ProbeException e = assertThrows(ProbeException.class, () -> probe.fetchTools(endpoint));
        assertTrue(e.getMessage().contains("Method not found"));
    }

    @Test
    void failsCleanlyWhenServerIsUnreachable() {
        server.stop(0);

        assertThrows(ProbeException.class, () -> probe.fetchTools(endpoint));
    }
}
