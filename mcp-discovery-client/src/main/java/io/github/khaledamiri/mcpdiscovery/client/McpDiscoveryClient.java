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

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;
import io.github.khaledamiri.mcpdiscovery.search.ToolMatch;

/**
 * Client for an MCP Discovery registry, used by MCP servers to register themselves and by agents
 * to discover tools.
 *
 * <pre>{@code
 * McpDiscoveryClient discovery = McpDiscoveryClient.builder("http://discovery:8761").apiKey(key).build();
 *
 * // MCP server side: register and keep the lease alive
 * Registration registration = discovery.register(new RegistrationRequest(
 *         "jira-mcp", null, "http://10.0.2.15:8080/mcp", "1.2.0", null, null, tools));
 *
 * // Agent side: find a tool
 * List<ToolMatch> matches = discovery.searchTools("search jira issues", 5);
 * }</pre>
 *
 * <p>Heartbeats run on a daemon thread. When the registry answers a heartbeat with 404 (lease
 * expired, registry restarted), the client registers the instance again with the same id.
 * {@link #close()} deregisters every active registration.
 */
public final class McpDiscoveryClient implements AutoCloseable {

    /** Header carrying the registry API key. */
    public static final String API_KEY_HEADER = "X-Mcp-Discovery-Key";
    private static final System.Logger LOG = System.getLogger(McpDiscoveryClient.class.getName());
    private static final TypeReference<List<ToolMatch>> TOOL_MATCHES = new TypeReference<>() {
    };
    private static final TypeReference<List<InstanceInfo>> INSTANCES = new TypeReference<>() {
    };

    private final URI baseUri;
    private final String apiKey;
    private final HttpClient http;
    private final ObjectMapper mapper;
    private final Duration timeout;
    private final Duration heartbeatOverride;
    private final Set<Registration> registrations = ConcurrentHashMap.newKeySet();
    private final Set<CompletableFuture<Registration>> pending = ConcurrentHashMap.newKeySet();
    private final ScheduledExecutorService scheduler;
    private volatile boolean closed;

    private McpDiscoveryClient(Builder builder) {
        String url = builder.registryUrl.endsWith("/") ? builder.registryUrl : builder.registryUrl + "/";
        this.baseUri = URI.create(url);
        this.apiKey = builder.apiKey;
        this.timeout = builder.timeout;
        this.heartbeatOverride = builder.heartbeatInterval;
        this.http = builder.httpClient != null
                ? builder.httpClient
                : HttpClient.newBuilder().connectTimeout(builder.timeout).build();
        ObjectMapper base = builder.objectMapper != null ? builder.objectMapper.copy() : new ObjectMapper();
        this.mapper = base.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "mcp-discovery-heartbeat");
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * @param registryUrl base URL of the registry, e.g. {@code http://discovery:8761}
     * @return a new builder
     */
    public static Builder builder(String registryUrl) {
        return new Builder(registryUrl);
    }

    // ------------------------------------------------------------------ server side

    /**
     * Registers an instance and starts sending heartbeats.
     *
     * @param request registration payload
     * @return the active registration; close it on shutdown to deregister
     */
    public Registration register(RegistrationRequest request) {
        Objects.requireNonNull(request, "request");
        JsonNode response = registerOnce(request);
        String instanceId = response.path("instance").path("instanceId").asText(null);
        String serverName = response.path("instance").path("serverName").asText(request.serverName());
        if (instanceId == null) {
            throw new McpDiscoveryClientException("Registry response has no instanceId", -1, null);
        }
        RegistrationRequest stable = new RegistrationRequest(serverName, instanceId, request.endpoint(),
                request.version(), request.status(), request.metadata(), request.tools());
        Duration interval = heartbeatOverride != null
                ? heartbeatOverride
                : Duration.ofSeconds(Math.max(1, response.path("renewalIntervalSeconds").asLong(30)));

        Registration registration = new Registration(this, stable, interval);
        registrations.add(registration);
        long millis = interval.toMillis();
        registration.heartbeat(scheduler.scheduleWithFixedDelay(() -> heartbeat(registration),
                millis, millis, TimeUnit.MILLISECONDS));
        return registration;
    }

    /**
     * Registers in the background, retrying until the registry accepts the instance. Use it at
     * application startup so that an unavailable registry does not prevent the server from
     * starting. Client errors that a retry cannot fix (400, 401, 403) stop the retries.
     *
     * @param request    registration payload
     * @param retryDelay delay between attempts
     * @return a future completed with the active registration
     */
    public CompletableFuture<Registration> registerInBackground(RegistrationRequest request,
                                                                Duration retryDelay) {
        Objects.requireNonNull(request, "request");
        long delay = Math.max(100, retryDelay.toMillis());
        CompletableFuture<Registration> result = new CompletableFuture<>();
        pending.add(result);
        result.whenComplete((r, e) -> pending.remove(result));
        Runnable attempt = new Runnable() {
            @Override
            public void run() {
                if (closed || result.isDone()) {
                    return;
                }
                try {
                    result.complete(register(request));
                } catch (McpDiscoveryClientException e) {
                    int status = e.getStatusCode();
                    if (status == 400 || status == 401 || status == 403) {
                        LOG.log(System.Logger.Level.ERROR, "Registration of {0} rejected: {1}",
                                request.serverName(), e.getMessage());
                        result.completeExceptionally(e);
                        return;
                    }
                    LOG.log(System.Logger.Level.WARNING, "Registration of {0} failed, retrying in {1} ms: {2}",
                            request.serverName(), delay, e.getMessage());
                    scheduler.schedule(this, delay, TimeUnit.MILLISECONDS);
                } catch (RuntimeException e) {
                    result.completeExceptionally(e);
                }
            }
        };
        scheduler.execute(attempt);
        return result;
    }

    private JsonNode registerOnce(RegistrationRequest request) {
        HttpResponse<String> response = send("POST", "api/v1/servers", request);
        expect(response, 201, 200);
        return readTree(response.body());
    }

    void heartbeat(Registration registration) {
        if (registration.isClosed()) {
            return;
        }
        try {
            HttpResponse<String> response = send("PUT", instancePath(registration) + "/heartbeat", null);
            if (response.statusCode() == 404) {
                LOG.log(System.Logger.Level.INFO, "Lease of {0}/{1} expired, registering again",
                        registration.serverName(), registration.instanceId());
                registerOnce(registration.request());
            } else if (response.statusCode() >= 300) {
                LOG.log(System.Logger.Level.WARNING, "Heartbeat rejected with HTTP {0}", response.statusCode());
            }
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.WARNING, "Heartbeat failed: {0}", e.getMessage());
        }
    }

    void deregister(Registration registration) {
        registrations.remove(registration);
        try {
            send("DELETE", instancePath(registration), null);
        } catch (RuntimeException e) {
            LOG.log(System.Logger.Level.DEBUG, "Deregistration failed: {0}", e.getMessage());
        }
    }

    // ------------------------------------------------------------------ agent side

    /**
     * Searches tools by capability among healthy instances.
     *
     * @param query natural-language need, e.g. "search jira issues"
     * @param limit maximum number of results (1 to 100)
     * @return matching tools, best first
     */
    public List<ToolMatch> searchTools(String query, int limit) {
        String path = "api/v1/tools/search?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8)
                + "&limit=" + limit;
        HttpResponse<String> response = send("GET", path, null);
        expect(response, 200);
        return read(response.body(), TOOL_MATCHES);
    }

    /**
     * @param serverName logical server name
     * @return the UP instances of that server
     */
    public List<InstanceInfo> getInstances(String serverName) {
        HttpResponse<String> response = send("GET",
                "api/v1/servers/" + encodePath(serverName) + "/instances?status=UP", null);
        expect(response, 200);
        return read(response.body(), INSTANCES);
    }

    /** Deregisters every active registration and stops the heartbeat thread. */
    @Override
    public void close() {
        closed = true;
        for (CompletableFuture<Registration> future : List.copyOf(pending)) {
            future.cancel(false);
        }
        for (Registration registration : List.copyOf(registrations)) {
            registration.close();
        }
        scheduler.shutdownNow();
    }

    // ------------------------------------------------------------------ HTTP

    private String instancePath(Registration registration) {
        return "api/v1/servers/" + encodePath(registration.serverName())
                + "/instances/" + encodePath(registration.instanceId());
    }

    private HttpResponse<String> send(String method, String path, Object body) {
        try {
            HttpRequest.BodyPublisher publisher = body == null
                    ? HttpRequest.BodyPublishers.noBody()
                    : HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body));
            HttpRequest.Builder request = HttpRequest.newBuilder(baseUri.resolve(path))
                    .timeout(timeout)
                    .header("Accept", "application/json")
                    .method(method, publisher);
            if (body != null) {
                request.header("Content-Type", "application/json");
            }
            if (apiKey != null) {
                request.header(API_KEY_HEADER, apiKey);
            }
            return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new McpDiscoveryClientException("Cannot reach registry: " + e.getMessage(), -1, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpDiscoveryClientException("Interrupted", -1, e);
        }
    }

    private static void expect(HttpResponse<String> response, int... accepted) {
        for (int status : accepted) {
            if (response.statusCode() == status) {
                return;
            }
        }
        throw new McpDiscoveryClientException("Registry returned HTTP " + response.statusCode() + ": "
                + response.body(), response.statusCode(), null);
    }

    private JsonNode readTree(String body) {
        try {
            return mapper.readTree(body);
        } catch (JsonProcessingException e) {
            throw new McpDiscoveryClientException("Invalid registry response", -1, e);
        }
    }

    private <T> T read(String body, TypeReference<T> type) {
        try {
            return mapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new McpDiscoveryClientException("Invalid registry response", -1, e);
        }
    }

    private static String encodePath(String segment) {
        return URLEncoder.encode(segment, StandardCharsets.UTF_8).replace("+", "%20");
    }

    /** Builder for {@link McpDiscoveryClient}. */
    public static final class Builder {
        private final String registryUrl;
        private String apiKey;
        private HttpClient httpClient;
        private ObjectMapper objectMapper;
        private Duration timeout = Duration.ofSeconds(10);
        private Duration heartbeatInterval;

        private Builder(String registryUrl) {
            this.registryUrl = Objects.requireNonNull(registryUrl, "registryUrl");
        }

        /**
         * @param apiKey shared secret sent as {@value McpDiscoveryClient#API_KEY_HEADER}
         * @return this builder
         */
        public Builder apiKey(String apiKey) {
            this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey;
            return this;
        }

        /**
         * @param httpClient custom HTTP client (proxy, TLS, ...)
         * @return this builder
         */
        public Builder httpClient(HttpClient httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        /**
         * @param objectMapper base mapper; it is copied, not modified
         * @return this builder
         */
        public Builder objectMapper(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
            return this;
        }

        /**
         * @param timeout HTTP request timeout
         * @return this builder
         */
        public Builder timeout(Duration timeout) {
            this.timeout = Objects.requireNonNull(timeout);
            return this;
        }

        /**
         * @param heartbeatInterval overrides the interval suggested by the registry
         * @return this builder
         */
        public Builder heartbeatInterval(Duration heartbeatInterval) {
            this.heartbeatInterval = heartbeatInterval;
            return this;
        }

        /** @return the client */
        public McpDiscoveryClient build() {
            return new McpDiscoveryClient(this);
        }
    }
}
