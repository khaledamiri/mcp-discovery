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

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import io.github.khaledamiri.mcpdiscovery.probe.ProbeException;
import io.github.khaledamiri.mcpdiscovery.probe.ProbingDisabledException;
import io.github.khaledamiri.mcpdiscovery.registry.InstanceNotFoundException;
import io.github.khaledamiri.mcpdiscovery.registry.InvalidRegistrationException;

/** Maps domain exceptions to RFC 9457 problem details. */
@RestControllerAdvice(assignableTypes = {ServerController.class, ToolController.class})
public class ApiExceptionHandler {

    @ExceptionHandler(InvalidRegistrationException.class)
    ProblemDetail invalid(InvalidRegistrationException e) {
        return problem(HttpStatus.BAD_REQUEST, e.getMessage());
    }

    @ExceptionHandler(InstanceNotFoundException.class)
    ProblemDetail notFound(InstanceNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(ProbingDisabledException.class)
    ProblemDetail conflict(ProbingDisabledException e) {
        return problem(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(ProbeException.class)
    ProblemDetail probe(ProbeException e) {
        return problem(HttpStatus.BAD_GATEWAY, e.getMessage());
    }

    private static ProblemDetail problem(HttpStatus status, String detail) {
        return ProblemDetail.forStatusAndDetail(status, detail);
    }
}
