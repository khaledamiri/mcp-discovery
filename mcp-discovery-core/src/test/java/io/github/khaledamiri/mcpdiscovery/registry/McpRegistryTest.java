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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class McpRegistryTest {

    private MutableClock clock;
    private McpRegistry registry;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        registry = new McpRegistry(RegistrySettings.defaults(), clock);
    }

    private ServerInstance register(String name, String id) {
        return registry.register(new RegistrationRequest(name, id, "http://10.0.0.1:8080/mcp", "1.0",
                null, Map.of("team", "platform"), List.of()));
    }

    @Test
    void registersAndNormalizesServerName() {
        ServerInstance instance = register("Jira-MCP", "i-1");

        assertEquals("jira-mcp", instance.getServerName());
        assertEquals(InstanceStatus.UP, instance.getStatus());
        assertEquals(1, registry.getInstances("JIRA-mcp", null).size());
    }

    @Test
    void generatesInstanceIdWhenMissing() {
        ServerInstance instance = register("jira-mcp", null);

        assertNotNull(instance.getInstanceId());
        assertTrue(registry.find("jira-mcp", instance.getInstanceId()).isPresent());
    }

    @Test
    void rejectsInvalidRegistrations() {
        assertThrows(InvalidRegistrationException.class, () -> register("bad name!", "i-1"));
        assertThrows(InvalidRegistrationException.class, () -> registry.register(
                new RegistrationRequest("jira", "i-1", "stdio://local", null, null, null, null)));
        assertThrows(InvalidRegistrationException.class, () -> registry.register(
                new RegistrationRequest("jira", "i-1", "https://user:pwd@host/mcp", null, null, null, null)));
    }

    @Test
    void renewUnknownInstanceReturnsEmpty() {
        assertTrue(registry.renew("jira-mcp", "nope").isEmpty());
    }

    @Test
    void evictsInstancesWhoseLeaseExpired() {
        register("jira-mcp", "stale");
        register("jira-mcp", "alive");

        clock.advance(Duration.ofSeconds(60));
        registry.renew("jira-mcp", "alive");
        clock.advance(Duration.ofSeconds(40)); // stale: 100 s without heartbeat, alive: 40 s

        List<ServerInstance> evicted = registry.evictExpired();

        assertEquals(1, evicted.size());
        assertEquals("stale", evicted.get(0).getInstanceId());
        assertEquals(List.of("alive"),
                registry.getInstances("jira-mcp", null).stream().map(ServerInstance::getInstanceId).toList());
    }

    @Test
    void selfPreservationSuspendsEviction() {
        for (int i = 0; i < 10; i++) {
            register("jira-mcp", "i-" + i);
        }
        registry.rotateRenewalWindow(); // 10 registrations counted in the first window

        // Simulated network partition: no heartbeat for a full minute.
        clock.advance(Duration.ofSeconds(120));
        registry.rotateRenewalWindow();

        // 10 instances * 2 heartbeats/min * 0.85 = 17 expected, 0 received.
        assertEquals(17, registry.renewalThreshold());
        assertTrue(registry.isSelfPreservationActive());
        assertTrue(registry.evictExpired().isEmpty());
        assertEquals(10, registry.instanceCount());
    }

    @Test
    void selfPreservationCanBeDisabled() {
        registry = new McpRegistry(new RegistrySettings(Duration.ofSeconds(90), Duration.ofSeconds(30),
                false, 0.85), clock);
        register("jira-mcp", "i-1");
        registry.rotateRenewalWindow();
        clock.advance(Duration.ofSeconds(120));
        registry.rotateRenewalWindow();

        assertFalse(registry.isSelfPreservationActive());
        assertEquals(1, registry.evictExpired().size());
    }

    @Test
    void cancelRemovesEmptyServers() {
        register("jira-mcp", "i-1");

        assertTrue(registry.cancel("jira-mcp", "i-1").isPresent());
        assertTrue(registry.getServers().isEmpty());
    }

    @Test
    void summariesAggregateInstances() {
        register("jira-mcp", "i-1");
        register("jira-mcp", "i-2");
        registry.updateStatus("jira-mcp", "i-2", InstanceStatus.DOWN);
        registry.updateTools("jira-mcp", "i-1",
                List.of(new ToolDescriptor("search_issues", null, "Search Jira issues", null)));

        ServerSummary summary = registry.getServers().get(0);

        assertEquals(2, summary.instances());
        assertEquals(1, summary.upInstances());
        assertEquals(1, summary.toolCount());
    }

    @Test
    void updatingUnknownInstanceFails() {
        assertThrows(InstanceNotFoundException.class,
                () -> registry.updateStatus("jira-mcp", "nope", InstanceStatus.DOWN));
    }
}
