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

import java.util.List;
import java.util.Map;

/**
 * Payload sent by an MCP server (or its sidecar) when it registers.
 *
 * @param serverName logical server name, shared by all instances (e.g. {@code jira-mcp})
 * @param instanceId optional stable instance id; generated when absent
 * @param endpoint   Streamable HTTP endpoint of the instance (http or https)
 * @param version    optional server version
 * @param status     optional initial status, defaults to {@link InstanceStatus#UP}
 * @param metadata   optional free-form metadata (team, region, ...). Never put secrets here:
 *                   metadata is readable by every registry client.
 * @param tools      optional tools exposed by the instance
 */
public record RegistrationRequest(
        String serverName,
        String instanceId,
        String endpoint,
        String version,
        InstanceStatus status,
        Map<String, String> metadata,
        List<ToolDescriptor> tools) {
}
