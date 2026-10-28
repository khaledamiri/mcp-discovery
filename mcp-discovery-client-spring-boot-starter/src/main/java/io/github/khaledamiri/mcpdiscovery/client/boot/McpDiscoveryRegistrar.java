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

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClient;
import io.github.khaledamiri.mcpdiscovery.client.Registration;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;

/**
 * Registers the application once it is ready to serve requests. The registration is retried in
 * the background, so an unreachable registry never prevents the application from starting.
 */
public class McpDiscoveryRegistrar {

    private static final System.Logger LOG = System.getLogger(McpDiscoveryRegistrar.class.getName());

    private final McpDiscoveryClient client;
    private final McpDiscoveryClientProperties properties;
    private final String applicationName;
    private volatile CompletableFuture<Registration> registration;

    /**
     * @param client          the client used to register
     * @param properties      client configuration
     * @param applicationName value of spring.application.name, may be null
     * @throws IllegalStateException when no server name can be determined
     */
    public McpDiscoveryRegistrar(McpDiscoveryClient client, McpDiscoveryClientProperties properties, String applicationName) {
        this.client = Objects.requireNonNull(client);
        this.properties = Objects.requireNonNull(properties);
        this.applicationName = applicationName;
        RegistrationRequestFactory.serverName(properties, applicationName); // fail fast
    }

    /**
     * Starts the registration. Calling it again has no effect.
     *
     * @param port port the application listens on, used when the endpoint is derived
     * @return the pending registration
     */
    public synchronized CompletableFuture<Registration> register(int port) {
        if (registration == null) {
            RegistrationRequest request = RegistrationRequestFactory.build(properties, applicationName, port,
                    () -> HostResolver.resolve(properties.isPreferIpAddress()));
            LOG.log(System.Logger.Level.INFO, "Registering {0} at {1} with registry {2}",
                    request.serverName(), request.endpoint(), properties.getRegistryUrl());
            registration = client.registerInBackground(request, properties.getRetryInterval());
            registration.thenAccept(r -> LOG.log(System.Logger.Level.INFO,
                    "Registered {0}/{1}", r.serverName(), r.instanceId()));
        }
        return registration;
    }

    /** @return the pending or active registration, or null before {@link #register(int)} */
    public CompletableFuture<Registration> registration() {
        return registration;
    }
}
