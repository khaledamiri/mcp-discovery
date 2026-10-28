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
package io.github.khaledamiri.mcpdiscovery.probe;

import java.util.List;
import java.util.Objects;

import io.github.khaledamiri.mcpdiscovery.registry.InstanceStatus;
import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.ServerInstance;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

/**
 * Keeps the tool catalogue fresh by calling {@code tools/list} on registered instances.
 * Instances can opt out with the metadata entry {@code mcp-discovery.probe=false}.
 */
public class ToolsRefresher {

    /** Metadata key an instance sets to {@code "false"} to opt out of probing. */
    public static final String OPT_OUT_METADATA = "mcp-discovery.probe";
    private static final System.Logger LOG = System.getLogger(ToolsRefresher.class.getName());

    private final McpRegistry registry;
    private final ToolsProbe probe;
    private final boolean enabled;

    /**
     * @param registry the registry to update
     * @param probe    the probe used to list tools
     * @param enabled  whether probing is allowed at all
     */
    public ToolsRefresher(McpRegistry registry, ToolsProbe probe, boolean enabled) {
        this.registry = Objects.requireNonNull(registry);
        this.probe = Objects.requireNonNull(probe);
        this.enabled = enabled;
    }

    /** @return whether probing is enabled */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Probes one instance now and stores its tools.
     *
     * @param serverName server name
     * @param instanceId instance id
     * @return the updated instance
     * @throws ProbingDisabledException when probing is disabled
     * @throws ProbeException           when the instance cannot be probed
     */
    public ServerInstance refresh(String serverName, String instanceId) {
        if (!enabled) {
            throw new ProbingDisabledException();
        }
        ServerInstance instance = registry.require(serverName, instanceId);
        try {
            List<ToolDescriptor> tools = probe.fetchTools(instance.getEndpoint());
            return registry.updateTools(instance.getServerName(), instance.getInstanceId(), tools);
        } catch (ProbeException e) {
            registry.recordProbeError(instance.getServerName(), instance.getInstanceId(), e.getMessage());
            throw e;
        }
    }

    /** Probes every UP instance that did not opt out. Failures are logged, never thrown. */
    public void refreshAll() {
        if (!enabled) {
            return;
        }
        for (ServerInstance instance : registry.allInstances()) {
            if (instance.getStatus() != InstanceStatus.UP
                    || "false".equalsIgnoreCase(instance.getMetadata().get(OPT_OUT_METADATA))) {
                continue;
            }
            try {
                refresh(instance.getServerName(), instance.getInstanceId());
            } catch (RuntimeException e) {
                LOG.log(System.Logger.Level.WARNING, "Probe failed for {0}: {1}", instance, e.getMessage());
            }
        }
    }
}
