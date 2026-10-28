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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Description of an MCP tool, as returned by a server's {@code tools/list} method.
 *
 * @param name        unique tool name within its server
 * @param title       optional human-readable title
 * @param description natural-language description (used by tool search)
 * @param inputSchema JSON Schema of the tool arguments
 */
public record ToolDescriptor(String name, String title, String description, Map<String, Object> inputSchema) {

    public ToolDescriptor {
        if (name == null || name.isBlank()) {
            throw new InvalidRegistrationException("Tool name must not be blank");
        }
        name = name.trim();
        description = description == null ? "" : description;
        inputSchema = inputSchema == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(inputSchema));
    }
}
