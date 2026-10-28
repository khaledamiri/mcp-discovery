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

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Small, dependency-free tokenizer for English and French tool descriptions and queries:
 * splits camelCase and snake_case, strips accents, drops stop words, applies naive plural
 * stemming and maps a few French verbs to their English equivalent.
 */
final class Tokenizer {

    private static final Set<String> STOP_WORDS = Set.of(
            // English
            "a", "an", "the", "of", "for", "to", "in", "on", "and", "or", "with", "by", "from",
            "is", "are", "be", "it", "as", "at", "that", "this", "which", "can", "into", "using",
            "i", "me", "my", "we", "need", "want", "looking", "find", "some", "any",
            // French
            "un", "une", "le", "la", "les", "de", "des", "du", "pour", "dans", "sur", "et", "ou",
            "avec", "par", "qui", "que", "je", "j", "mon", "ma", "mes", "est", "sont", "en",
            "au", "aux", "cherche", "besoin", "veux", "possede", "permet", "permettant", "d", "l",
            // Domain words present in almost every query
            "mcp", "tool", "outil", "server", "serveur");

    private static final Map<String, String> SYNONYMS = Map.ofEntries(
            Map.entry("recherche", "search"), Map.entry("rechercher", "search"),
            Map.entry("chercher", "search"), Map.entry("query", "search"),
            Map.entry("creer", "create"), Map.entry("ajouter", "create"), Map.entry("add", "create"),
            Map.entry("lire", "read"), Map.entry("get", "read"), Map.entry("fetch", "read"),
            Map.entry("lister", "list"), Map.entry("liste", "list"),
            Map.entry("supprimer", "delete"), Map.entry("remove", "delete"),
            Map.entry("modifier", "update"), Map.entry("edit", "update"),
            Map.entry("ticket", "issue"), Map.entry("tache", "task"),
            Map.entry("fichier", "file"), Map.entry("document", "doc"),
            Map.entry("envoyer", "send"));

    private Tokenizer() {
    }

    static List<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String s = text.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT);
        s = Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        List<String> tokens = new ArrayList<>();
        for (String raw : s.split("[^a-z0-9]+")) {
            if (raw.length() < 2 || STOP_WORDS.contains(raw)) {
                continue;
            }
            tokens.add(normalize(raw));
        }
        return tokens;
    }

    private static String normalize(String token) {
        String t = token;
        if (t.length() > 3 && t.endsWith("s") && !t.endsWith("ss")) {
            t = t.substring(0, t.length() - 1);
        }
        String synonym = SYNONYMS.get(t);
        if (synonym == null) {
            synonym = SYNONYMS.get(token);
        }
        return synonym != null ? synonym : t;
    }
}
