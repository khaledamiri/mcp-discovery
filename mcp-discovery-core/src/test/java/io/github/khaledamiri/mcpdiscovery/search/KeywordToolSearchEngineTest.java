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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.khaledamiri.mcpdiscovery.registry.InstanceStatus;
import io.github.khaledamiri.mcpdiscovery.registry.McpRegistry;
import io.github.khaledamiri.mcpdiscovery.registry.MutableClock;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrationRequest;
import io.github.khaledamiri.mcpdiscovery.registry.RegistrySettings;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

class KeywordToolSearchEngineTest {

    private final KeywordToolSearchEngine engine = new KeywordToolSearchEngine();
    private McpRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new McpRegistry(RegistrySettings.defaults(),
                new MutableClock(Instant.parse("2026-01-01T00:00:00Z")));
        register("jira-mcp", "j1", "http://jira-1/mcp",
                new ToolDescriptor("search_issues", "Search issues", "Search Jira issues using JQL", null),
                new ToolDescriptor("create_issue", null, "Create a new Jira issue", null));
        register("jira-mcp", "j2", "http://jira-2/mcp",
                new ToolDescriptor("search_issues", "Search issues", "Search Jira issues using JQL", null));
        register("gitlab-mcp", "g1", "http://gitlab-1/mcp",
                new ToolDescriptor("searchMergeRequests", null, "Search GitLab merge requests", null));
        register("opensearch-mcp", "o1", "http://os-1/mcp",
                new ToolDescriptor("query_index", null, "Run a query against an OpenSearch index", null));
    }

    private void register(String server, String id, String endpoint, ToolDescriptor... tools) {
        registry.register(new RegistrationRequest(server, id, endpoint, "1.0", null, null, List.of(tools)));
    }

    @Test
    void findsJiraSearchToolFirst() {
        List<ToolMatch> matches = engine.search("search jira issues", registry.allInstances(), 5);

        ToolMatch best = matches.get(0);
        assertEquals("jira-mcp", best.serverName());
        assertEquals("search_issues", best.toolName());
        assertEquals(List.of("http://jira-1/mcp", "http://jira-2/mcp"), best.endpoints());
    }

    @Test
    void understandsFrenchQueries() {
        List<ToolMatch> matches = engine.search(
                "Je cherche un MCP qui possède un tool de recherche Jira", registry.allInstances(), 5);

        assertEquals("search_issues", matches.get(0).toolName());
    }

    @Test
    void splitsCamelCaseToolNames() {
        List<ToolMatch> matches = engine.search("merge requests", registry.allInstances(), 5);

        assertEquals("searchMergeRequests", matches.get(0).toolName());
    }

    @Test
    void ignoresInstancesThatAreNotUp() {
        registry.updateStatus("jira-mcp", "j1", InstanceStatus.DOWN);

        List<ToolMatch> matches = engine.search("create jira issue", registry.allInstances(), 5);

        assertTrue(matches.stream().noneMatch(m -> m.toolName().equals("create_issue")));
        ToolMatch search = matches.stream().filter(m -> m.toolName().equals("search_issues")).findFirst().orElseThrow();
        assertEquals(List.of("http://jira-2/mcp"), search.endpoints());
    }

    @Test
    void returnsNothingForStopWordsOnly() {
        assertTrue(engine.search("je cherche un mcp", registry.allInstances(), 5).isEmpty());
    }

    @Test
    void respectsLimit() {
        assertEquals(1, engine.search("search", registry.allInstances(), 1).size());
    }
}
