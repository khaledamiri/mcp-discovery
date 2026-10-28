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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;

class RegistrationRequestFactoryTest {

    @Test
    void derivesEndpointFromHostPortAndPath() {
        McpDiscoveryClientProperties props = new McpDiscoveryClientProperties();

        RegistrationRequest request = RegistrationRequestFactory.build(props, "jira-mcp", 8080, () -> "10.0.2.15");

        assertEquals("jira-mcp", request.serverName());
        assertEquals("http://10.0.2.15:8080/mcp", request.endpoint());
        assertNull(request.instanceId());
    }

    @Test
    void explicitValuesWin() {
        McpDiscoveryClientProperties props = new McpDiscoveryClientProperties();
        props.setServerName("jira-mcp");
        props.setEndpoint("https://jira-mcp.example.com/mcp");
        props.setInstanceId("jira-1");
        props.setVersion("1.2.0");
        props.setMetadata(Map.of("team", "platform"));
        McpDiscoveryClientProperties.Tool tool = new McpDiscoveryClientProperties.Tool();
        tool.setName("search_issues");
        tool.setDescription("Search Jira issues");
        props.setTools(List.of(tool));

        RegistrationRequest request = RegistrationRequestFactory.build(props, "ignored-app-name", 8080, () -> "unused");

        assertEquals("jira-mcp", request.serverName());
        assertEquals("https://jira-mcp.example.com/mcp", request.endpoint());
        assertEquals("jira-1", request.instanceId());
        assertEquals("1.2.0", request.version());
        assertEquals("search_issues", request.tools().get(0).name());
    }

    @Test
    void hostnameAndPathOverrides() {
        McpDiscoveryClientProperties props = new McpDiscoveryClientProperties();
        props.setHostname("jira-mcp");
        props.setPath("api/mcp");
        props.setScheme("https");

        RegistrationRequest request = RegistrationRequestFactory.build(props, "jira-mcp", 8443, () -> "unused");

        assertEquals("https://jira-mcp:8443/api/mcp", request.endpoint());
    }

    @Test
    void requiresAServerName() {
        assertThrows(IllegalStateException.class,
                () -> RegistrationRequestFactory.serverName(new McpDiscoveryClientProperties(), null));
    }

    @Test
    void hostResolverReturnsSomething() {
        assertEquals(false, HostResolver.resolve(true).isBlank());
    }
}
