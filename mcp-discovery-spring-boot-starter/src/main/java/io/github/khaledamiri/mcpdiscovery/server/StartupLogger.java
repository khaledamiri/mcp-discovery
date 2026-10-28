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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.web.context.WebServerInitializedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.Environment;

/** Logs where the registry, its dashboard and its API can be reached once the web server is up. */
public class StartupLogger implements ApplicationListener<WebServerInitializedEvent> {

    private static final Logger log = LoggerFactory.getLogger(StartupLogger.class);

    private final Environment environment;
    private final McpDiscoveryProperties properties;

    public StartupLogger(Environment environment, McpDiscoveryProperties properties) {
        this.environment = environment;
        this.properties = properties;
    }

    @Override
    public void onApplicationEvent(WebServerInitializedEvent event) {
        if ("management".equals(event.getApplicationContext().getServerNamespace())) {
            return; // separate actuator port
        }
        String scheme = environment.getProperty("server.ssl.enabled", Boolean.class, false) ? "https" : "http";
        String contextPath = trimSlash(environment.getProperty("server.servlet.context-path", ""));
        String root = scheme + "://localhost:" + event.getWebServer().getPort() + contextPath;

        StringBuilder message = new StringBuilder("\n\n  MCP Discovery registry is running\n");
        if (properties.dashboard().enabled()) {
            String path = properties.dashboard().path();
            message.append("    Dashboard     ").append(root).append("/".equals(path) ? "/" : path).append('\n');
        }
        message.append("    Registry URL  ").append(root).append("   (mcp-discovery.client.registry-url)\n")
                .append("    REST API      ").append(root).append("/api/v1\n")
                .append("    API key       ").append(hasText(properties.security().apiKey())
                        ? "required for writes (X-Mcp-Discovery-Key)" : "NOT SET: anyone can register servers")
                .append('\n');
        log.info(message.toString());
    }

    private static String trimSlash(String path) {
        return path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
