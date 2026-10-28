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

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClient;

class McpDiscoveryClientAutoConfigurationTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(McpDiscoveryClientAutoConfiguration.class));

    @Test
    void bindsYamlProperties() {
        runner.withPropertyValues(
                        "mcp-discovery.client.registry-url=http://discovery:8761",
                        "mcp-discovery.client.server-name=jira-mcp",
                        "mcp-discovery.client.metadata.team=platform",
                        "mcp-discovery.client.tools[0].name=search_issues",
                        "mcp-discovery.client.tools[0].description=Search Jira issues",
                        "mcp-discovery.client.tools[0].input-schema.type=object")
                .run(context -> {
                    McpDiscoveryClientProperties props = context.getBean(McpDiscoveryClientProperties.class);
                    assertEquals("http://discovery:8761", props.getRegistryUrl());
                    assertEquals("platform", props.getMetadata().get("team"));
                    assertEquals("search_issues", props.getTools().get(0).getName());
                    assertEquals("object", props.getTools().get(0).getInputSchema().get("type"));
                    assertEquals(1, context.getBeanNamesForType(McpDiscoveryClient.class).length);
                    assertEquals(1, context.getBeanNamesForType(McpDiscoveryRegistrar.class).length);
                });
    }

    @Test
    void searchOnlyClientsDoNotRegister() {
        runner.withPropertyValues("mcp-discovery.client.register=false")
                .run(context -> {
                    assertEquals(1, context.getBeanNamesForType(McpDiscoveryClient.class).length);
                    assertEquals(0, context.getBeanNamesForType(McpDiscoveryRegistrar.class).length);
                });
    }

    @Test
    void canBeDisabled() {
        runner.withPropertyValues("mcp-discovery.client.enabled=false")
                .run(context -> assertEquals(0, context.getBeanNamesForType(McpDiscoveryClient.class).length));
    }

    @Test
    void usesTheApplicationNameByDefault() {
        runner.withPropertyValues("spring.application.name=jira-mcp")
                .run(context -> assertEquals(1, context.getBeanNamesForType(McpDiscoveryRegistrar.class).length));
    }
}
