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
package io.github.khaledamiri.mcpdiscovery.search;

import java.util.List;
import java.util.Map;

/**
 * A tool matching a search query, with the endpoints of the healthy instances exposing it.
 *
 * @param serverName  logical MCP server name
 * @param toolName    tool name, to be passed to {@code tools/call}
 * @param title       optional human-readable title
 * @param description tool description
 * @param score       relevance score (higher is better, not normalised across queries)
 * @param inputSchema JSON Schema of the tool arguments
 * @param endpoints   endpoints of the UP instances exposing this tool
 */
public record ToolMatch(String serverName, String toolName, String title, String description,
                        double score, Map<String, Object> inputSchema, List<String> endpoints) {
}
