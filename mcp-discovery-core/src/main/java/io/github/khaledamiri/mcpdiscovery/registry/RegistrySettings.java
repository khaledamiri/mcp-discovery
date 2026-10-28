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

import java.time.Duration;
import java.util.Objects;

/**
 * Lease and eviction settings, modelled on Netflix Eureka defaults.
 *
 * @param leaseDuration            how long an instance stays registered without a heartbeat
 * @param renewalInterval          how often clients are expected to send a heartbeat
 * @param selfPreservationEnabled  suspend eviction when far fewer heartbeats arrive than expected
 * @param renewalPercentThreshold  fraction of expected heartbeats below which self-preservation starts
 */
public record RegistrySettings(Duration leaseDuration, Duration renewalInterval,
                               boolean selfPreservationEnabled, double renewalPercentThreshold) {

    public RegistrySettings {
        Objects.requireNonNull(leaseDuration, "leaseDuration");
        Objects.requireNonNull(renewalInterval, "renewalInterval");
        if (renewalInterval.toSeconds() < 1) {
            throw new IllegalArgumentException("renewalInterval must be at least 1 second");
        }
        if (leaseDuration.compareTo(renewalInterval) <= 0) {
            throw new IllegalArgumentException("leaseDuration must be greater than renewalInterval");
        }
        if (renewalPercentThreshold <= 0 || renewalPercentThreshold > 1) {
            throw new IllegalArgumentException("renewalPercentThreshold must be in (0, 1]");
        }
    }

    public static RegistrySettings defaults() {
        return new RegistrySettings(Duration.ofSeconds(90), Duration.ofSeconds(30), true, 0.85);
    }
}
