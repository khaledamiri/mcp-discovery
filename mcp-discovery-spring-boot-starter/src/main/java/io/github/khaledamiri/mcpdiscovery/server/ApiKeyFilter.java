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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Minimal shared-secret protection for the {@code /api/} endpoints. Disabled when
 * {@code mcp-discovery.security.api-key} is empty. Use a real identity provider (OAuth2, mTLS)
 * for production deployments.
 */
public class ApiKeyFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Mcp-Discovery-Key";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final byte[] apiKey;
    private final boolean protectReads;

    /**
     * @param apiKey       shared secret, or null/blank to disable the filter
     * @param protectReads also require the key for GET requests
     */
    public ApiKeyFilter(String apiKey, boolean protectReads) {
        this.apiKey = apiKey == null || apiKey.isBlank() ? null : apiKey.getBytes(StandardCharsets.UTF_8);
        this.protectReads = protectReads;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (apiKey == null || !request.getRequestURI().startsWith("/api/")) {
            return true;
        }
        return !protectReads && !WRITE_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String provided = request.getHeader(HEADER);
        if (provided != null && MessageDigest.isEqual(apiKey, provided.getBytes(StandardCharsets.UTF_8))) {
            chain.doFilter(request, response);
            return;
        }
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write("{\"title\":\"Unauthorized\",\"status\":401,"
                + "\"detail\":\"Missing or invalid " + HEADER + " header\"}");
    }
}
