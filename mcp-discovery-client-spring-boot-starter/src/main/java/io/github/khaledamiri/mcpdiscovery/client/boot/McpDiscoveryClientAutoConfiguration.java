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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClient;

/**
 * Creates a {@link McpDiscoveryClient} from {@code mcp-discovery.client.*} and registers the application
 * once it is ready. Set {@code mcp-discovery.client.register=false} to only use the client for search.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnProperty(prefix = "mcp-discovery.client", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties
public class McpDiscoveryClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    @ConfigurationProperties("mcp-discovery.client")
    McpDiscoveryClientProperties mcpDiscoveryClientProperties() {
        return new McpDiscoveryClientProperties();
    }

    /** Closing the client stops heartbeats and deregisters the application. */
    @Bean(destroyMethod = "close")
    @ConditionalOnMissingBean
    McpDiscoveryClient mcpDiscoveryClient(McpDiscoveryClientProperties properties, ObjectProvider<ObjectMapper> objectMapper) {
        McpDiscoveryClient.Builder builder = McpDiscoveryClient.builder(properties.getRegistryUrl())
                .apiKey(properties.getApiKey())
                .timeout(properties.getTimeout())
                .heartbeatInterval(properties.getHeartbeatInterval());
        objectMapper.ifAvailable(builder::objectMapper);
        return builder.build();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mcp-discovery.client", name = "register", havingValue = "true", matchIfMissing = true)
    McpDiscoveryRegistrar mcpDiscoveryRegistrar(McpDiscoveryClient client, McpDiscoveryClientProperties properties, Environment environment) {
        return new McpDiscoveryRegistrar(client, properties, environment.getProperty("spring.application.name"));
    }

    /** Registers only once the web server is listening, so agents never get a dead endpoint. */
    @Bean
    ApplicationListener<ApplicationReadyEvent> mcpDiscoveryRegistrationTrigger(ObjectProvider<McpDiscoveryRegistrar> registrar,
                                                                         Environment environment) {
        return event -> registrar.ifAvailable(r -> {
            Integer port = environment.getProperty("local.server.port", Integer.class);
            if (port == null) {
                port = environment.getProperty("server.port", Integer.class, 8080);
            }
            r.register(port);
        });
    }
}
