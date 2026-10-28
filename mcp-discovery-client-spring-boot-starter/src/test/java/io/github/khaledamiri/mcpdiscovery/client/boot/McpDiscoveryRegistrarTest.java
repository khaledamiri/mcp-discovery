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
package io.github.khaledamiri.mcpdiscovery.client.boot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClient;
import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClientException;
import io.github.khaledamiri.mcpdiscovery.client.Registration;

class McpDiscoveryRegistrarTest {

    private final List<String> bodies = new CopyOnWriteArrayList<>();
    private final AtomicInteger attempts = new AtomicInteger();
    private volatile int failuresBeforeSuccess;
    private volatile int failureStatus = 503;
    private HttpServer server;
    private McpDiscoveryClient client;
    private McpDiscoveryClientProperties properties;

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        properties = new McpDiscoveryClientProperties();
        properties.setRegistryUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.setHostname("jira-host");
        properties.setRetryInterval(Duration.ofMillis(100));
        client = McpDiscoveryClient.builder(properties.getRegistryUrl()).heartbeatInterval(Duration.ofSeconds(30)).build();
    }

    @AfterEach
    void stop() {
        client.close();
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        }
        bodies.add(body);
        if (attempts.incrementAndGet() <= failuresBeforeSuccess) {
            exchange.sendResponseHeaders(failureStatus, -1);
            exchange.close();
            return;
        }
        byte[] bytes = ("{\"instance\":{\"instanceId\":\"jira-1\",\"serverName\":\"jira-mcp\"},"
                + "\"renewalIntervalSeconds\":30}").getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(201, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void registersWithTheDerivedEndpoint() throws Exception {
        McpDiscoveryRegistrar registrar = new McpDiscoveryRegistrar(client, properties, "jira-mcp");

        Registration registration = registrar.register(9090).get(5, TimeUnit.SECONDS);

        assertEquals("jira-1", registration.instanceId());
        assertTrue(bodies.get(0).contains("\"endpoint\":\"http://jira-host:9090/mcp\""), bodies.get(0));
        assertTrue(bodies.get(0).contains("\"serverName\":\"jira-mcp\""));
    }

    @Test
    void retriesWhileTheRegistryIsUnavailable() throws Exception {
        failuresBeforeSuccess = 2;
        McpDiscoveryRegistrar registrar = new McpDiscoveryRegistrar(client, properties, "jira-mcp");

        registrar.register(9090).get(5, TimeUnit.SECONDS);

        assertEquals(3, attempts.get());
    }

    @Test
    void stopsRetryingWhenTheKeyIsRejected() {
        failuresBeforeSuccess = 100;
        failureStatus = 401;
        McpDiscoveryRegistrar registrar = new McpDiscoveryRegistrar(client, properties, "jira-mcp");

        ExecutionException e = assertThrows(ExecutionException.class,
                () -> registrar.register(9090).get(5, TimeUnit.SECONDS));
        assertEquals(401, ((McpDiscoveryClientException) e.getCause()).getStatusCode());
        assertEquals(1, attempts.get());
    }

    @Test
    void registersOnlyOnce() {
        McpDiscoveryRegistrar registrar = new McpDiscoveryRegistrar(client, properties, "jira-mcp");

        assertSame(registrar.register(9090), registrar.register(9090));
    }

    @Test
    void failsFastWithoutServerName() {
        assertThrows(IllegalStateException.class, () -> new McpDiscoveryRegistrar(client, properties, null));
    }
}
