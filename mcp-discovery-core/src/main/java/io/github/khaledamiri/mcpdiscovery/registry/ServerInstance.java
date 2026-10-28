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
package io.github.khaledamiri.mcpdiscovery.registry;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * A registered instance of an MCP server. Identity fields are immutable; status, tools and
 * lease information are updated in place by {@link McpRegistry}.
 */
public final class ServerInstance {

    private final String instanceId;
    private final String serverName;
    private final String endpoint;
    private final String version;
    private final Map<String, String> metadata;
    private final Instant registeredAt;

    private volatile InstanceStatus status;
    private volatile List<ToolDescriptor> tools;
    private volatile Instant lastRenewal;
    private volatile Instant toolsUpdatedAt;
    private volatile String lastProbeError;

    ServerInstance(String instanceId, String serverName, String endpoint, String version,
                   InstanceStatus status, Map<String, String> metadata, List<ToolDescriptor> tools,
                   Instant now) {
        this.instanceId = Objects.requireNonNull(instanceId);
        this.serverName = Objects.requireNonNull(serverName);
        this.endpoint = Objects.requireNonNull(endpoint);
        this.version = version;
        this.status = status == null ? InstanceStatus.UP : status;
        this.metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.registeredAt = now;
        this.lastRenewal = now;
        this.toolsUpdatedAt = now;
    }

    public String getInstanceId() { return instanceId; }
    public String getServerName() { return serverName; }
    public String getEndpoint() { return endpoint; }
    public String getVersion() { return version; }
    public Map<String, String> getMetadata() { return metadata; }
    public Instant getRegisteredAt() { return registeredAt; }
    public InstanceStatus getStatus() { return status; }
    public List<ToolDescriptor> getTools() { return tools; }
    public Instant getLastRenewal() { return lastRenewal; }
    public Instant getToolsUpdatedAt() { return toolsUpdatedAt; }
    public String getLastProbeError() { return lastProbeError; }

    /** @return true when no heartbeat was received within {@code leaseDuration}. */
    public boolean isLeaseExpired(Instant now, Duration leaseDuration) {
        return now.isAfter(lastRenewal.plus(leaseDuration));
    }

    void renew(Instant now) { this.lastRenewal = now; }

    void setStatus(InstanceStatus status) { this.status = Objects.requireNonNull(status); }

    void setTools(List<ToolDescriptor> tools, Instant now) {
        this.tools = tools == null ? List.of() : List.copyOf(tools);
        this.toolsUpdatedAt = now;
        this.lastProbeError = null;
    }

    void setLastProbeError(String error) { this.lastProbeError = error; }

    @Override
    public String toString() {
        return serverName + "/" + instanceId + " (" + endpoint + ", " + status + ")";
    }
}
