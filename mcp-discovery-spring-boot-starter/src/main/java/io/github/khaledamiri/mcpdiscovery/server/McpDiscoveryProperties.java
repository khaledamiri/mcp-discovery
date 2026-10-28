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

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Configuration under the {@code mcp-discovery.*} prefix. */
@ConfigurationProperties("mcp-discovery")
public record McpDiscoveryProperties(@DefaultValue Registry registry,
                               @DefaultValue Probe probe,
                               @DefaultValue Security security,
                               @DefaultValue Dashboard dashboard) {

    public record Registry(@DefaultValue("PT90S") Duration leaseDuration,
                           @DefaultValue("PT30S") Duration renewalInterval,
                           @DefaultValue("PT60S") Duration evictionInterval,
                           @DefaultValue("true") boolean selfPreservation,
                           @DefaultValue("0.85") double renewalPercentThreshold) {
    }

    public record Probe(@DefaultValue("false") boolean enabled,
                        @DefaultValue("PT5M") Duration interval,
                        @DefaultValue("PT10S") Duration timeout) {
    }

    /**
     * @param apiKey       when set, write operations require the {@code X-Mcp-Discovery-Key} header
     * @param protectReads also require the key for read operations
     */
    public record Security(String apiKey, @DefaultValue("false") boolean protectReads) {
    }

    /**
     * @param enabled serve the web dashboard
     * @param path    URL path of the dashboard ({@code /} in the standalone server)
     */
    public record Dashboard(@DefaultValue("true") boolean enabled,
                            @DefaultValue("/mcp-discovery") String path) {
    }
}
