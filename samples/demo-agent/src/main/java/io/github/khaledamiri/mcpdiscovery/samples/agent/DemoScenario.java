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
package io.github.khaledamiri.mcpdiscovery.samples.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClient;
import io.github.khaledamiri.mcpdiscovery.client.McpDiscoveryClientException;
import io.github.khaledamiri.mcpdiscovery.search.ToolMatch;

/**
 * The demo: for each need, ask the registry which tool fits, pick a healthy instance at random,
 * call the tool directly on that MCP server, and print who answered.
 */
@Component
public class DemoScenario implements CommandLineRunner {

    /** A need expressed in words, and the arguments to pass to whichever tool is found. */
    private record Step(String need, Map<String, Map<String, Object>> argumentsByTool) {
    }

    private static final List<Step> STEPS = List.of(
            new Step("recherche jira", Map.of("search_issues", Map.of("jql", "project = DEMO AND status = Open"))),
            new Step("get jira issue", Map.of("get_issue", Map.of("key", "DEMO-4"))),
            new Step("create jira issue", Map.of(
                    "create_issue", Map.of("project", "DEMO", "summary", "Created by the demo agent", "priority", "High"))),
            new Step("pull requests github", Map.of("list_pull_requests", Map.of("repo", "acme/payments-service"))),
            new Step("search repositories java", Map.of("search_repositories", Map.of("query", "java"))));

    private final McpDiscoveryClient discovery;
    private final McpToolCaller caller;
    private final ObjectMapper mapper;

    public DemoScenario(McpDiscoveryClient discovery, McpToolCaller caller, ObjectMapper mapper) {
        this.discovery = discovery;
        this.caller = caller;
        this.mapper = mapper;
    }

    @Override
    public void run(String... args) throws Exception {
        waitForTools(Duration.ofSeconds(90));
        for (Step step : STEPS) {
            runStep(step);
        }
        loadBalancing();
    }

    /** The registry learns tools by probing, about 20 s after it starts: wait for them. */
    private void waitForTools(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        System.out.println("\nWaiting for the registry to know the mock tools...");
        while (System.nanoTime() < deadline) {
            try {
                if (!discovery.searchTools("search jira issues", 1).isEmpty()
                        && !discovery.searchTools("pull requests", 1).isEmpty()) {
                    System.out.println("Tools are available.\n");
                    return;
                }
            } catch (McpDiscoveryClientException e) {
                System.out.println("  registry not reachable yet (" + e.getMessage() + ")");
            }
            Thread.sleep(3000);
        }
        throw new IllegalStateException("No tools found after " + timeout.toSeconds() + " s: are the mock servers registered?");
    }

    private void runStep(Step step) throws Exception {
        System.out.println("=".repeat(78));
        System.out.println("Need: \"" + step.need() + "\"");
        List<ToolMatch> matches = discovery.searchTools(step.need(), 3);
        if (matches.isEmpty()) {
            System.out.println("  no tool found\n");
            return;
        }
        for (ToolMatch m : matches) {
            System.out.printf("  candidate  %-12s %-22s score %5.2f  %d instance(s)%n",
                    m.serverName(), m.toolName(), m.score(), m.endpoints().size());
        }
        ToolMatch best = matches.get(0);
        Map<String, Object> arguments = step.argumentsByTool().getOrDefault(best.toolName(), Map.of());
        String endpoint = pick(best.endpoints());
        System.out.println("  chosen     " + best.serverName() + " / " + best.toolName() + " at " + endpoint);
        System.out.println("  arguments  " + mapper.writeValueAsString(arguments));

        JsonNode result = callWithFailover(best, arguments, endpoint);
        JsonNode payload = payload(result);
        System.out.println("  served by  " + payload.path("servedBy").asText("?")
                + (result.path("isError").asBoolean() ? "  (tool error)" : ""));
        System.out.println(indent(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(
                payload.has("result") ? payload.get("result") : payload)));
        System.out.println();
    }

    /** Several calls to the same tool: with two Jira instances, both should answer. */
    private void loadBalancing() throws Exception {
        System.out.println("=".repeat(78));
        System.out.println("Load balancing: 6 calls to search_issues");
        ToolMatch match = discovery.searchTools("search jira issues", 1).get(0);
        Map<String, Integer> servedBy = new LinkedHashMap<>();
        for (int i = 0; i < 6; i++) {
            JsonNode result = callWithFailover(match, Map.of("jql", "project = OPS"), pick(match.endpoints()));
            servedBy.merge(payload(result).path("servedBy").asText("?"), 1, Integer::sum);
        }
        servedBy.forEach((instance, count) -> System.out.println("  " + instance + " answered " + count + " time(s)"));
        System.out.println();
    }

    /** Tries the chosen endpoint, then the other instances: one may have died before its eviction. */
    private JsonNode callWithFailover(ToolMatch match, Map<String, Object> arguments, String first) throws Exception {
        List<String> candidates = new ArrayList<>(match.endpoints());
        candidates.remove(first);
        Collections.shuffle(candidates);
        candidates.add(0, first);
        Exception last = null;
        for (String endpoint : candidates) {
            try {
                return caller.call(endpoint, match.toolName(), arguments);
            } catch (java.io.IOException e) {
                System.out.println("  " + endpoint + " failed (" + e.getMessage() + "), trying the next instance");
                last = e;
            }
        }
        throw last;
    }

    /**
     * The tool's answer: structuredContent when the server sends it, otherwise the JSON text of
     * the first content block (Spring AI serialises the tool's return value there).
     */
    private JsonNode payload(JsonNode result) {
        if (result.hasNonNull("structuredContent")) {
            return result.get("structuredContent");
        }
        String text = result.path("content").path(0).path("text").asText("");
        try {
            return mapper.readTree(text);
        } catch (Exception e) {
            return mapper.createObjectNode().put("text", text);
        }
    }

    private static String pick(List<String> endpoints) {
        return endpoints.get(java.util.concurrent.ThreadLocalRandom.current().nextInt(endpoints.size()));
    }

    private static String indent(String text) {
        return "  | " + text.replace("\n", "\n  | ");
    }
}
