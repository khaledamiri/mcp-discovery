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
package io.github.khaledamiri.mcpdiscovery.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.khaledamiri.mcpdiscovery.registry.InstanceStatus;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;
import io.github.khaledamiri.mcpdiscovery.search.ToolMatch;

class McpDiscoveryClientTest {

    private final List<String> calls = new CopyOnWriteArrayList<>();
    private final AtomicInteger heartbeats = new AtomicInteger();
    private HttpServer server;
    private McpDiscoveryClient client;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        client = McpDiscoveryClient.builder("http://127.0.0.1:" + server.getAddress().getPort())
                .apiKey("secret")
                .heartbeatInterval(Duration.ofMillis(100))
                .build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    /** Fake registry: the first heartbeat answers 404 to simulate an expired lease. */
    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getRawPath();
        String query = exchange.getRequestURI().getRawQuery();
        String key = exchange.getRequestHeaders().getFirst("X-Mcp-Discovery-Key");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        calls.add(method + " " + path + (query == null ? "" : "?" + query) + " key=" + key
                + (body.isEmpty() ? "" : " " + body));

        if (method.equals("POST") && path.equals("/api/v1/servers")) {
            reply(exchange, 201, """
                    {"instance":{"instanceId":"generated-1","serverName":"jira-mcp","endpoint":"http://jira/mcp",
                     "status":"UP","registeredAt":"2026-01-01T00:00:00Z"},
                     "renewalIntervalSeconds":30,"leaseDurationSeconds":90}""");
        } else if (method.equals("PUT") && path.endsWith("/heartbeat")) {
            reply(exchange, heartbeats.getAndIncrement() == 0 ? 404 : 204, null);
        } else if (method.equals("DELETE")) {
            reply(exchange, 204, null);
        } else if (method.equals("GET") && path.equals("/api/v1/tools/search")) {
            reply(exchange, 200, """
                    [{"serverName":"jira-mcp","toolName":"search_issues","description":"Search Jira issues",
                      "score":8.0,"inputSchema":{"type":"object"},"endpoints":["http://jira/mcp"]}]""");
        } else if (method.equals("GET") && path.equals("/api/v1/servers/jira-mcp/instances")) {
            reply(exchange, 200, """
                    [{"instanceId":"generated-1","serverName":"jira-mcp","endpoint":"http://jira/mcp",
                      "status":"UP","metadata":{},"tools":[],"lastRenewal":"2026-01-01T00:00:00Z"}]""");
        } else {
            reply(exchange, 500, "{\"detail\":\"unexpected\"}");
        }
    }

    private static void reply(HttpExchange exchange, int status, String body) throws IOException {
        if (body == null) {
            exchange.sendResponseHeaders(status, -1);
            exchange.close();
            return;
        }
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private RegistrationRequest request() {
        return new RegistrationRequest("jira-mcp", null, "http://jira/mcp", "1.0", null, null, null);
    }

    @Test
    void registersSendsHeartbeatsAndReRegistersAfterExpiry() throws Exception {
        Registration registration = client.register(request());

        assertEquals("generated-1", registration.instanceId());
        waitUntil(() -> heartbeats.get() >= 3);

        List<String> registrations = calls.stream().filter(c -> c.startsWith("POST")).toList();
        assertEquals(2, registrations.size(), calls.toString());
        assertTrue(registrations.get(0).contains("\"serverName\":\"jira-mcp\""));
        assertTrue(registrations.get(1).contains("\"instanceId\":\"generated-1\""),
                "re-registration must reuse the instance id");
        assertTrue(calls.stream().allMatch(c -> c.contains("key=secret")));
    }

    @Test
    void closeStopsHeartbeatsAndDeregisters() throws Exception {
        Registration registration = client.register(request());
        registration.close();
        int count = heartbeats.get();
        Thread.sleep(300);

        assertEquals(count, heartbeats.get());
        assertTrue(calls.contains("DELETE /api/v1/servers/jira-mcp/instances/generated-1 key=secret"));
    }

    @Test
    void searchesTools() {
        List<ToolMatch> matches = client.searchTools("recherche jira", 5);

        assertEquals("search_issues", matches.get(0).toolName());
        assertEquals(List.of("http://jira/mcp"), matches.get(0).endpoints());
        assertTrue(calls.get(0).startsWith("GET /api/v1/tools/search?q=recherche+jira&limit=5"));
    }

    @Test
    void listsInstances() {
        List<InstanceInfo> instances = client.getInstances("jira-mcp");

        assertEquals(1, instances.size());
        assertEquals(InstanceStatus.UP, instances.get(0).status());
    }

    @Test
    void surfacesRegistryErrors() {
        McpDiscoveryClientException e = assertThrows(McpDiscoveryClientException.class, () -> client.getInstances("unknown"));
        assertEquals(500, e.getStatusCode());
    }

    private static void waitUntil(java.util.function.BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() > deadline) {
                throw new AssertionError("Condition not met in time");
            }
            Thread.sleep(20);
        }
    }
}
