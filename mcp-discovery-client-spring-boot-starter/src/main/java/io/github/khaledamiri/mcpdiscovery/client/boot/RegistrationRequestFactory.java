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

import java.util.List;
import java.util.function.Supplier;

import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

/** Turns {@link McpDiscoveryClientProperties} into a {@link RegistrationRequest}. */
final class RegistrationRequestFactory {

    private RegistrationRequestFactory() {
    }

    static String serverName(McpDiscoveryClientProperties properties, String applicationName) {
        String name = firstNonBlank(properties.getServerName(), applicationName);
        if (name == null) {
            throw new IllegalStateException(
                    "MCP Discovery: set mcp-discovery.client.server-name or spring.application.name");
        }
        return name;
    }

    static RegistrationRequest build(McpDiscoveryClientProperties properties, String applicationName,
                                     int port, Supplier<String> host) {
        String endpoint = properties.getEndpoint();
        if (endpoint == null || endpoint.isBlank()) {
            String hostname = firstNonBlank(properties.getHostname(), null);
            endpoint = properties.getScheme() + "://" + (hostname != null ? hostname : host.get())
                    + ":" + port + normalizePath(properties.getPath());
        }
        List<ToolDescriptor> tools = properties.getTools().stream()
                .map(t -> new ToolDescriptor(t.getName(), t.getTitle(), t.getDescription(), t.getInputSchema()))
                .toList();
        return new RegistrationRequest(
                serverName(properties, applicationName),
                firstNonBlank(properties.getInstanceId(), null),
                endpoint.trim(),
                firstNonBlank(properties.getVersion(), null),
                properties.getInitialStatus(),
                properties.getMetadata(),
                tools);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "";
        }
        return path.startsWith("/") ? path : "/" + path;
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.trim();
        }
        return second != null && !second.isBlank() ? second.trim() : null;
    }
}
