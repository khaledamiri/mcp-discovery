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

import java.net.http.HttpClient;
import java.time.Clock;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import io.github.khaledamiri.mcpdiscovery.maintenance.RegistryMaintenance;
import io.github.khaledamiri.mcpdiscovery.probe.StreamableHttpToolsProbe;
import io.github.khaledamiri.mcpdiscovery.probe.ToolsProbe;
import io.github.khaledamiri.mcpdiscovery.probe.ToolsRefresher;
import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrySettings;
import io.github.khaledamiri.mcpdiscovery.search.KeywordToolSearchEngine;
import io.github.khaledamiri.mcpdiscovery.search.ToolSearchEngine;

/**
 * Auto-configuration of the MCP Discovery registry, active when the application is annotated with
 * {@link EnableMcpDiscoveryServer}. Every core bean can be replaced by declaring your own.
 */
@AutoConfiguration(after = JacksonAutoConfiguration.class)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(McpDiscoveryServerMarkerConfiguration.Marker.class)
@EnableConfigurationProperties(McpDiscoveryProperties.class)
public class McpDiscoveryServerAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(McpRegistry.class)
    McpRegistry mcpRegistry(McpDiscoveryProperties properties, ObjectProvider<Clock> clock) {
        McpDiscoveryProperties.Registry r = properties.registry();
        return new McpRegistry(new RegistrySettings(r.leaseDuration(), r.renewalInterval(),
                r.selfPreservation(), r.renewalPercentThreshold()), clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnMissingBean(ToolSearchEngine.class)
    ToolSearchEngine toolSearchEngine() {
        return new KeywordToolSearchEngine();
    }

    @Bean
    @ConditionalOnMissingBean(ToolsProbe.class)
    ToolsProbe toolsProbe(ObjectProvider<ObjectMapper> objectMapper, McpDiscoveryProperties properties) {
        HttpClient http = HttpClient.newBuilder()
                .connectTimeout(properties.probe().timeout())
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        String version = McpDiscoveryServerAutoConfiguration.class.getPackage().getImplementationVersion();
        return new StreamableHttpToolsProbe(http, objectMapper.getIfAvailable(ObjectMapper::new),
                properties.probe().timeout(), version == null ? "dev" : version);
    }

    @Bean
    @ConditionalOnMissingBean(ToolsRefresher.class)
    ToolsRefresher toolsRefresher(McpRegistry registry, ToolsProbe probe, McpDiscoveryProperties properties) {
        return new ToolsRefresher(registry, probe, properties.probe().enabled());
    }

    @Bean(initMethod = "start", destroyMethod = "close")
    @ConditionalOnMissingBean(RegistryMaintenance.class)
    RegistryMaintenance registryMaintenance(McpRegistry registry, ToolsRefresher refresher,
                                            McpDiscoveryProperties properties) {
        return new RegistryMaintenance(registry, properties.registry().evictionInterval(),
                refresher, properties.probe().interval());
    }

    @Bean
    @ConditionalOnMissingBean
    ServerController mcpDiscoveryServerController(McpRegistry registry, ToolsRefresher refresher) {
        return new ServerController(registry, refresher);
    }

    @Bean
    @ConditionalOnMissingBean
    ToolController mcpDiscoveryToolController(McpRegistry registry, ToolSearchEngine searchEngine) {
        return new ToolController(registry, searchEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    ApiExceptionHandler mcpDiscoveryApiExceptionHandler() {
        return new ApiExceptionHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnProperty(prefix = "mcp-discovery.dashboard", name = "enabled", havingValue = "true", matchIfMissing = true)
    DashboardController mcpDiscoveryDashboardController() {
        return new DashboardController();
    }

    @Bean
    StartupLogger mcpDiscoveryStartupLogger(Environment environment, McpDiscoveryProperties properties) {
        return new StartupLogger(environment, properties);
    }

    @Bean
    FilterRegistrationBean<ApiKeyFilter> mcpDiscoveryApiKeyFilter(McpDiscoveryProperties properties) {
        McpDiscoveryProperties.Security security = properties.security();
        FilterRegistrationBean<ApiKeyFilter> registration = new FilterRegistrationBean<>(
                new ApiKeyFilter(security.apiKey(), security.protectReads()));
        registration.addUrlPatterns("/api/*");
        registration.setName("mcpDiscoveryApiKeyFilter");
        return registration;
    }
}
