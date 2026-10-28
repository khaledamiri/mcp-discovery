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
package io.github.khaledamiri.mcpdiscovery.registry;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;

/**
 * In-memory registry of MCP server instances.
 *
 * <p>The lifecycle mirrors Netflix Eureka: an instance registers, renews its lease with periodic
 * heartbeats, and is evicted once its lease expires. When the registry receives far fewer
 * heartbeats than expected (typically a network partition between the registry and the
 * servers, not a mass outage), it enters <em>self-preservation</em> and stops evicting.
 *
 * <p>This class has no framework dependency and is thread-safe.
 */
public class McpRegistry {

    private static final Pattern SERVER_NAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,62}$");
    private static final Pattern INSTANCE_ID = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final ConcurrentMap<String, ConcurrentMap<String, ServerInstance>> servers =
            new ConcurrentHashMap<>();
    private final RegistrySettings settings;
    private final Clock clock;

    private final AtomicLong currentWindowRenewals = new AtomicLong();
    private volatile long lastWindowRenewals;
    private volatile boolean windowComplete;

    public McpRegistry(RegistrySettings settings, Clock clock) {
        this.settings = Objects.requireNonNull(settings);
        this.clock = Objects.requireNonNull(clock);
    }

    public RegistrySettings settings() {
        return settings;
    }

    // ------------------------------------------------------------------ lifecycle

    /** Registers (or re-registers) an instance. Re-registering the same id replaces it. */
    public ServerInstance register(RegistrationRequest request) {
        if (request == null) {
            throw new InvalidRegistrationException("Registration body is required");
        }
        String name = normalizeServerName(request.serverName());
        String endpoint = validateEndpoint(request.endpoint());
        String instanceId = request.instanceId() == null || request.instanceId().isBlank()
                ? UUID.randomUUID().toString()
                : request.instanceId().trim();
        if (!INSTANCE_ID.matcher(instanceId).matches()) {
            throw new InvalidRegistrationException(
                    "instanceId must match " + INSTANCE_ID.pattern());
        }
        validateMetadata(request.metadata());

        ServerInstance instance = new ServerInstance(instanceId, name, endpoint, request.version(),
                request.status(), request.metadata(), request.tools(), clock.instant());

        servers.compute(name, (key, instances) -> {
            ConcurrentMap<String, ServerInstance> map =
                    instances == null ? new ConcurrentHashMap<>() : instances;
            map.put(instanceId, instance);
            return map;
        });
        currentWindowRenewals.incrementAndGet();
        return instance;
    }

    /**
     * Renews the lease of an instance.
     *
     * @return the renewed instance, or empty when unknown: the client must then re-register
     */
    public Optional<ServerInstance> renew(String serverName, String instanceId) {
        Optional<ServerInstance> instance = find(serverName, instanceId);
        instance.ifPresent(i -> {
            i.renew(clock.instant());
            currentWindowRenewals.incrementAndGet();
        });
        return instance;
    }

    /** Removes an instance (graceful shutdown). */
    public Optional<ServerInstance> cancel(String serverName, String instanceId) {
        String name = normalizeLookupName(serverName);
        ServerInstance[] removed = new ServerInstance[1];
        servers.computeIfPresent(name, (key, instances) -> {
            removed[0] = instances.remove(instanceId);
            return instances.isEmpty() ? null : instances;
        });
        return Optional.ofNullable(removed[0]);
    }

    public ServerInstance updateStatus(String serverName, String instanceId, InstanceStatus status) {
        if (status == null) {
            throw new InvalidRegistrationException("status is required");
        }
        ServerInstance instance = require(serverName, instanceId);
        instance.setStatus(status);
        return instance;
    }

    public ServerInstance updateTools(String serverName, String instanceId, List<ToolDescriptor> tools) {
        ServerInstance instance = require(serverName, instanceId);
        instance.setTools(tools, clock.instant());
        return instance;
    }

    public void recordProbeError(String serverName, String instanceId, String error) {
        find(serverName, instanceId).ifPresent(i -> i.setLastProbeError(error));
    }

    // ------------------------------------------------------------------ queries

    public Optional<ServerInstance> find(String serverName, String instanceId) {
        if (serverName == null || instanceId == null) {
            return Optional.empty();
        }
        Map<String, ServerInstance> instances = servers.get(normalizeLookupName(serverName));
        return instances == null ? Optional.empty() : Optional.ofNullable(instances.get(instanceId));
    }

    public ServerInstance require(String serverName, String instanceId) {
        return find(serverName, instanceId)
                .orElseThrow(() -> new InstanceNotFoundException(serverName, instanceId));
    }

    /** Instances of one server, optionally filtered by status, sorted by instance id. */
    public List<ServerInstance> getInstances(String serverName, InstanceStatus statusFilter) {
        Map<String, ServerInstance> instances = servers.get(normalizeLookupName(serverName));
        if (instances == null) {
            return List.of();
        }
        return instances.values().stream()
                .filter(i -> statusFilter == null || i.getStatus() == statusFilter)
                .sorted(Comparator.comparing(ServerInstance::getInstanceId))
                .toList();
    }

    public List<ServerInstance> allInstances() {
        List<ServerInstance> all = new ArrayList<>();
        servers.values().forEach(m -> all.addAll(m.values()));
        all.sort(Comparator.comparing(ServerInstance::getServerName)
                .thenComparing(ServerInstance::getInstanceId));
        return all;
    }

    public List<ServerSummary> getServers() {
        return servers.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    var instances = e.getValue().values();
                    int up = (int) instances.stream().filter(i -> i.getStatus() == InstanceStatus.UP).count();
                    var versions = new TreeSet<String>();
                    var toolNames = new TreeSet<String>();
                    for (ServerInstance i : instances) {
                        if (i.getVersion() != null) {
                            versions.add(i.getVersion());
                        }
                        i.getTools().forEach(t -> toolNames.add(t.name()));
                    }
                    return new ServerSummary(e.getKey(), instances.size(), up, versions, toolNames.size());
                })
                .toList();
    }

    public int instanceCount() {
        return servers.values().stream().mapToInt(Map::size).sum();
    }

    // ------------------------------------------------------------------ eviction

    /**
     * Closes the current one-minute renewal window. Must be called once per minute; the
     * self-preservation threshold is expressed in renewals per minute.
     */
    public void rotateRenewalWindow() {
        lastWindowRenewals = currentWindowRenewals.getAndSet(0);
        windowComplete = true;
    }

    /** Minimum renewals per minute expected from the currently registered instances. */
    public long renewalThreshold() {
        double perInstancePerMinute = 60.0 / settings.renewalInterval().toSeconds();
        return (long) Math.floor(instanceCount() * perInstancePerMinute * settings.renewalPercentThreshold());
    }

    public boolean isSelfPreservationActive() {
        if (!settings.selfPreservationEnabled() || !windowComplete || instanceCount() == 0) {
            return false;
        }
        return lastWindowRenewals < renewalThreshold();
    }

    /**
     * Evicts every instance whose lease expired, unless self-preservation is active.
     *
     * @return the evicted instances
     */
    public List<ServerInstance> evictExpired() {
        if (isSelfPreservationActive()) {
            return List.of();
        }
        Instant now = clock.instant();
        List<ServerInstance> evicted = new ArrayList<>();
        for (ServerInstance instance : allInstances()) {
            if (instance.isLeaseExpired(now, settings.leaseDuration())) {
                // Re-check under the map lock: a heartbeat may have arrived meanwhile.
                servers.computeIfPresent(instance.getServerName(), (key, instances) -> {
                    ServerInstance current = instances.get(instance.getInstanceId());
                    if (current != null && current.isLeaseExpired(now, settings.leaseDuration())) {
                        instances.remove(instance.getInstanceId());
                        evicted.add(current);
                    }
                    return instances.isEmpty() ? null : instances;
                });
            }
        }
        return evicted;
    }

    public RegistryStatus status() {
        List<ServerInstance> all = allInstances();
        int up = (int) all.stream().filter(i -> i.getStatus() == InstanceStatus.UP).count();
        return new RegistryStatus(all.size(), up, lastWindowRenewals, renewalThreshold(),
                isSelfPreservationActive(), settings.leaseDuration().toSeconds(),
                settings.renewalInterval().toSeconds(), clock.instant());
    }

    // ------------------------------------------------------------------ validation

    private static String normalizeServerName(String serverName) {
        if (serverName == null || serverName.isBlank()) {
            throw new InvalidRegistrationException("serverName is required");
        }
        String name = serverName.trim().toLowerCase(Locale.ROOT);
        if (!SERVER_NAME.matcher(name).matches()) {
            throw new InvalidRegistrationException("serverName must match " + SERVER_NAME.pattern());
        }
        return name;
    }

    private static String normalizeLookupName(String serverName) {
        return serverName == null ? "" : serverName.trim().toLowerCase(Locale.ROOT);
    }

    private static String validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank()) {
            throw new InvalidRegistrationException("endpoint is required");
        }
        try {
            URI uri = new URI(endpoint.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                throw new InvalidRegistrationException("endpoint must be an http(s) URL "
                        + "(stdio servers cannot be registered)");
            }
            if (uri.getHost() == null) {
                throw new InvalidRegistrationException("endpoint must include a host");
            }
            if (uri.getUserInfo() != null) {
                throw new InvalidRegistrationException("endpoint must not contain credentials");
            }
            return uri.toString();
        } catch (URISyntaxException e) {
            throw new InvalidRegistrationException("endpoint is not a valid URL: " + e.getMessage());
        }
    }

    private static void validateMetadata(Map<String, String> metadata) {
        if (metadata == null) {
            return;
        }
        if (metadata.size() > 64) {
            throw new InvalidRegistrationException("metadata accepts at most 64 entries");
        }
        metadata.forEach((k, v) -> {
            if (k == null || k.isBlank() || v == null) {
                throw new InvalidRegistrationException("metadata keys and values must not be null");
            }
        });
    }
}
