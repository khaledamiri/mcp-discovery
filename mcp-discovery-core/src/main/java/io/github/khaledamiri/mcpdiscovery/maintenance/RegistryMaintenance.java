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
package io.github.khaledamiri.mcpdiscovery.maintenance;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import io.github.khaledamiri.mcpdiscovery.probe.ToolsRefresher;
import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.ServerInstance;

/**
 * Background housekeeping for a {@link McpRegistry}: rotates the per-minute renewal window,
 * evicts expired instances and, optionally, refreshes tools. Runs on its own daemon threads,
 * so it needs no scheduling framework.
 *
 * <pre>{@code
 * try (var maintenance = new RegistryMaintenance(registry, Duration.ofSeconds(60), null, null)) {
 *     maintenance.start();
 *     ...
 * }
 * }</pre>
 */
public class RegistryMaintenance implements AutoCloseable {

    private static final System.Logger LOG = System.getLogger(RegistryMaintenance.class.getName());
    private static final Duration PROBE_INITIAL_DELAY = Duration.ofSeconds(20);

    private final McpRegistry registry;
    private final Duration evictionInterval;
    private final ToolsRefresher refresher;
    private final Duration probeInterval;

    private ScheduledExecutorService executor;
    private boolean selfPreservationLogged;

    /**
     * @param registry         registry to maintain
     * @param evictionInterval how often expired instances are evicted
     * @param refresher        optional tools refresher, may be null
     * @param probeInterval    how often tools are refreshed, ignored when {@code refresher} is null
     */
    public RegistryMaintenance(McpRegistry registry, Duration evictionInterval,
                               ToolsRefresher refresher, Duration probeInterval) {
        this.registry = Objects.requireNonNull(registry);
        this.evictionInterval = Objects.requireNonNull(evictionInterval);
        this.refresher = refresher;
        this.probeInterval = probeInterval;
        if (evictionInterval.toMillis() <= 0) {
            throw new IllegalArgumentException("evictionInterval must be positive");
        }
    }

    /** Starts the background tasks. Calling it twice has no effect. */
    public synchronized void start() {
        if (executor != null) {
            return;
        }
        AtomicInteger counter = new AtomicInteger();
        executor = Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "mcp-discovery-maintenance-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        });
        // The self-preservation threshold is expressed per minute: keep this at one minute.
        executor.scheduleAtFixedRate(safely(registry::rotateRenewalWindow), 1, 1, TimeUnit.MINUTES);
        long eviction = evictionInterval.toMillis();
        executor.scheduleWithFixedDelay(safely(this::evictOnce), eviction, eviction, TimeUnit.MILLISECONDS);
        if (refresher != null && refresher.isEnabled() && probeInterval != null && probeInterval.toMillis() > 0) {
            executor.scheduleWithFixedDelay(safely(refresher::refreshAll),
                    PROBE_INITIAL_DELAY.toMillis(), probeInterval.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    /**
     * Runs one eviction pass.
     *
     * @return the evicted instances (empty while self-preservation is active)
     */
    public synchronized List<ServerInstance> evictOnce() {
        if (registry.isSelfPreservationActive()) {
            if (!selfPreservationLogged) {
                LOG.log(System.Logger.Level.WARNING,
                        "Self-preservation active, eviction suspended: {0}", registry.status());
                selfPreservationLogged = true;
            }
            return List.of();
        }
        if (selfPreservationLogged) {
            LOG.log(System.Logger.Level.INFO, "Self-preservation lifted, eviction resumed");
            selfPreservationLogged = false;
        }
        List<ServerInstance> evicted = registry.evictExpired();
        for (ServerInstance instance : evicted) {
            LOG.log(System.Logger.Level.INFO, "Evicted {0} (last heartbeat {1})",
                    instance, instance.getLastRenewal());
        }
        return evicted;
    }

    /** Stops the background tasks. */
    @Override
    public synchronized void close() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static Runnable safely(Runnable task) {
        return () -> {
            try {
                task.run();
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.ERROR, "Maintenance task failed", e);
            }
        };
    }
}
