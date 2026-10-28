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
package io.github.khaledamiri.mcpdiscovery.client;

import java.util.List;
import java.util.Map;

import io.github.khaledamiri.mcpdiscovery.registry.InstanceStatus;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

/**
 * Client-side view of a registered instance.
 *
 * @param instanceId instance id
 * @param serverName logical server name
 * @param endpoint   Streamable HTTP endpoint
 * @param version    server version, may be null
 * @param status     current status
 * @param metadata   free-form metadata
 * @param tools      tools exposed by the instance
 */
public record InstanceInfo(String instanceId, String serverName, String endpoint, String version,
                           InstanceStatus status, Map<String, String> metadata,
                           List<ToolDescriptor> tools) {
}
