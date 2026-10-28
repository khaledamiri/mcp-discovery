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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.khaledamiri.mcpdiscovery.registry.InstanceStatus;
import io.github.khaledamiri.mcpdiscovery.registry.ServerInstance;
import io.github.khaledamiri.mcpdiscovery.registry.ToolDescriptor;

/**
 * Weighted keyword search over tool name, title, server name and description.
 *
 * <p>Each query term scores the weight of the best field it appears in (exact token match),
 * or half of it for a prefix match. The sum is multiplied by the fraction of query terms that
 * matched, so tools matching every term rank first. Good enough for a few hundred tools; swap
 * in an embedding-based {@link ToolSearchEngine} for cross-lingual or fuzzy needs.
 */
public class KeywordToolSearchEngine implements ToolSearchEngine {

    private static final double TOOL_NAME_WEIGHT = 3.0;
    private static final double TITLE_WEIGHT = 2.0;
    private static final double SERVER_NAME_WEIGHT = 2.0;
    private static final double DESCRIPTION_WEIGHT = 1.0;
    private static final double PREFIX_FACTOR = 0.5;

    @Override
    public List<ToolMatch> search(String query, Collection<ServerInstance> instances, int limit) {
        List<String> queryTerms = new ArrayList<>(new LinkedHashSet<>(Tokenizer.tokenize(query)));
        if (queryTerms.isEmpty() || limit <= 0) {
            return List.of();
        }

        List<ToolMatch> matches = new ArrayList<>();
        for (Candidate candidate : candidates(instances).values()) {
            double score = score(queryTerms, candidate);
            if (score > 0) {
                ToolDescriptor tool = candidate.tool;
                matches.add(new ToolMatch(candidate.serverName, tool.name(), tool.title(),
                        tool.description(), Math.round(score * 1000) / 1000.0, tool.inputSchema(),
                        List.copyOf(candidate.endpoints)));
            }
        }
        matches.sort(Comparator.comparingDouble(ToolMatch::score).reversed()
                .thenComparing(ToolMatch::serverName)
                .thenComparing(ToolMatch::toolName));
        return matches.size() > limit ? List.copyOf(matches.subList(0, limit)) : matches;
    }

    /** Groups tools of UP instances by (server, tool), keeping the most recently updated descriptor. */
    private static Map<String, Candidate> candidates(Collection<ServerInstance> instances) {
        Map<String, Candidate> byKey = new LinkedHashMap<>();
        for (ServerInstance instance : instances) {
            if (instance.getStatus() != InstanceStatus.UP) {
                continue;
            }
            for (ToolDescriptor tool : instance.getTools()) {
                String key = instance.getServerName() + "\u0000" + tool.name();
                Candidate c = byKey.computeIfAbsent(key,
                        k -> new Candidate(instance.getServerName(), tool, instance));
                c.endpoints.add(instance.getEndpoint());
                if (instance.getToolsUpdatedAt().isAfter(c.source.getToolsUpdatedAt())) {
                    c.tool = tool;
                    c.source = instance;
                }
            }
        }
        return byKey;
    }

    private static double score(List<String> queryTerms, Candidate c) {
        List<Field> fields = List.of(
                new Field(Tokenizer.tokenize(c.tool.name()), TOOL_NAME_WEIGHT),
                new Field(Tokenizer.tokenize(c.tool.title()), TITLE_WEIGHT),
                new Field(Tokenizer.tokenize(c.serverName), SERVER_NAME_WEIGHT),
                new Field(Tokenizer.tokenize(c.tool.description()), DESCRIPTION_WEIGHT));

        double total = 0;
        int matchedTerms = 0;
        for (String term : queryTerms) {
            double best = 0;
            for (Field field : fields) {
                best = Math.max(best, field.score(term));
            }
            if (best > 0) {
                matchedTerms++;
                total += best;
            }
        }
        return total * matchedTerms / queryTerms.size();
    }

    private record Field(Set<String> tokens, double weight) {
        Field(List<String> tokens, double weight) {
            this(new HashSet<>(tokens), weight);
        }

        double score(String term) {
            if (tokens.contains(term)) {
                return weight;
            }
            if (term.length() >= 3) {
                for (String token : tokens) {
                    if (token.length() >= 3 && (token.startsWith(term) || term.startsWith(token))) {
                        return weight * PREFIX_FACTOR;
                    }
                }
            }
            return 0;
        }
    }

    private static final class Candidate {
        final String serverName;
        final Set<String> endpoints = new LinkedHashSet<>();
        ToolDescriptor tool;
        ServerInstance source;

        Candidate(String serverName, ToolDescriptor tool, ServerInstance source) {
            this.serverName = serverName;
            this.tool = tool;
            this.source = source;
        }
    }
}
