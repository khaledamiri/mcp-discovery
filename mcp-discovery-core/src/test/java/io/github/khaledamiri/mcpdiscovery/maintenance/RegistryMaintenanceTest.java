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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.MutableClock;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrySettings;

class RegistryMaintenanceTest {

    @Test
    void evictsExpiredInstances() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
        McpRegistry registry = new McpRegistry(RegistrySettings.defaults(), clock);
        registry.register(new RegistrationRequest("jira-mcp", "i-1", "http://jira/mcp", null, null, null, List.of()));

        try (RegistryMaintenance maintenance = new RegistryMaintenance(registry, Duration.ofMinutes(1), null, null)) {
            maintenance.start();
            assertTrue(maintenance.evictOnce().isEmpty());

            clock.advance(Duration.ofSeconds(91));
            assertEquals(1, maintenance.evictOnce().size());
            assertEquals(0, registry.instanceCount());
        }
    }
}
