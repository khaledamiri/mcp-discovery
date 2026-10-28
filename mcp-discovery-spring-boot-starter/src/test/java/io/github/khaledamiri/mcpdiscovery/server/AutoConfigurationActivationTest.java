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
package io.github.khaledamiri.mcpdiscovery.server;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;

class AutoConfigurationActivationTest {

    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpDiscoveryServerAutoConfiguration.class));

    @Test
    void inactiveWithoutEnableMcpDiscoveryServer() {
        runner.run(context -> assertEquals(0, context.getBeanNamesForType(McpRegistry.class).length));
    }

    @Test
    void activeWithEnableMcpDiscoveryServer() {
        runner.withUserConfiguration(McpDiscoveryServerMarkerConfiguration.class)
                .run(context -> assertEquals(1, context.getBeanNamesForType(McpRegistry.class).length));
    }
}
