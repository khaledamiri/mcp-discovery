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

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.github.khaledamiri.mcpdiscovery.registry.InvalidRegistrationException;
import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.RegistryStatus;
import io.github.khaledamiri.mcpdiscovery.registry.ServerInstance;
import io.github.khaledamiri.mcpdiscovery.search.ToolMatch;
import io.github.khaledamiri.mcpdiscovery.search.ToolSearchEngine;

/** Capability discovery: "which MCP server has a tool for X?" */
@RestController
@RequestMapping("/api/v1")
public class ToolController {

    private static final int MAX_LIMIT = 100;

    private final McpRegistry registry;
    private final ToolSearchEngine searchEngine;

    public ToolController(McpRegistry registry, ToolSearchEngine searchEngine) {
        this.registry = registry;
        this.searchEngine = searchEngine;
    }

    @GetMapping("/tools/search")
    public List<ToolMatch> search(@RequestParam("q") String query,
                                  @RequestParam(defaultValue = "10") int limit) {
        if (query.isBlank() || query.length() > 500) {
            throw new InvalidRegistrationException("q must contain 1 to 500 characters");
        }
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new InvalidRegistrationException("limit must be between 1 and " + MAX_LIMIT);
        }
        return searchEngine.search(query, registry.allInstances(), limit);
    }

    /** Every registered instance, whatever its status (used by the dashboard). */
    @GetMapping("/instances")
    public List<ServerInstance> instances() {
        return registry.allInstances();
    }

    @GetMapping("/registry/status")
    public RegistryStatus status() {
        return registry.status();
    }
}
