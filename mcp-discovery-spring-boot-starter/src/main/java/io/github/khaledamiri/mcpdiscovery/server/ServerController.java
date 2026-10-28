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

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.khaledamiri.mcpdiscovery.probe.ToolsRefresher;
import io.github.khaledamiri.mcpdiscovery.registry.InstanceNotFoundException;
import io.github.khaledamiri.mcpdiscovery.registry.InstanceStatus;
import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;
import io.github.khaledamiri.mcpdiscovery.registry.ServerInstance;
import io.github.khaledamiri.mcpdiscovery.registry.ServerSummary;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

/** Registration, heartbeat and instance lookup endpoints. */
@RestController
@RequestMapping("/api/v1/servers")
public class ServerController {

    private final McpRegistry registry;
    private final ToolsRefresher refreshService;

    public ServerController(McpRegistry registry, ToolsRefresher refreshService) {
        this.registry = registry;
        this.refreshService = refreshService;
    }

    public record RegistrationResponse(ServerInstance instance, long renewalIntervalSeconds,
                                       long leaseDurationSeconds) {
    }

    public record StatusUpdate(InstanceStatus status) {
    }

    public record ToolsUpdate(List<ToolDescriptor> tools) {
    }

    @PostMapping
    public ResponseEntity<RegistrationResponse> register(@RequestBody RegistrationRequest request) {
        ServerInstance instance = registry.register(request);
        URI location = URI.create("/api/v1/servers/" + instance.getServerName()
                + "/instances/" + instance.getInstanceId());
        return ResponseEntity.created(location).body(new RegistrationResponse(instance,
                registry.settings().renewalInterval().toSeconds(),
                registry.settings().leaseDuration().toSeconds()));
    }

    @GetMapping
    public List<ServerSummary> servers() {
        return registry.getServers();
    }

    @GetMapping("/{serverName}/instances")
    public List<ServerInstance> instances(@PathVariable String serverName,
                                          @RequestParam(required = false) InstanceStatus status) {
        return registry.getInstances(serverName, status);
    }

    @GetMapping("/{serverName}/instances/{instanceId}")
    public ServerInstance instance(@PathVariable String serverName, @PathVariable String instanceId) {
        return registry.require(serverName, instanceId);
    }

    /** Heartbeat. A 404 tells the client its lease expired and it must register again. */
    @PutMapping("/{serverName}/instances/{instanceId}/heartbeat")
    public ResponseEntity<Void> heartbeat(@PathVariable String serverName, @PathVariable String instanceId) {
        registry.renew(serverName, instanceId)
                .orElseThrow(() -> new InstanceNotFoundException(serverName, instanceId));
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{serverName}/instances/{instanceId}/status")
    public ServerInstance status(@PathVariable String serverName, @PathVariable String instanceId,
                                 @RequestBody StatusUpdate update) {
        return registry.updateStatus(serverName, instanceId, update == null ? null : update.status());
    }

    @PutMapping("/{serverName}/instances/{instanceId}/tools")
    public ServerInstance tools(@PathVariable String serverName, @PathVariable String instanceId,
                                @RequestBody ToolsUpdate update) {
        return registry.updateTools(serverName, instanceId, update == null ? List.of() : update.tools());
    }

    @PostMapping("/{serverName}/instances/{instanceId}/tools/refresh")
    public ServerInstance refreshTools(@PathVariable String serverName, @PathVariable String instanceId) {
        return refreshService.refresh(serverName, instanceId);
    }

    @DeleteMapping("/{serverName}/instances/{instanceId}")
    public ResponseEntity<Void> cancel(@PathVariable String serverName, @PathVariable String instanceId) {
        registry.cancel(serverName, instanceId)
                .orElseThrow(() -> new InstanceNotFoundException(serverName, instanceId));
        return ResponseEntity.noContent().build();
    }
}
