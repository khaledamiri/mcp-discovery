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

import java.time.Instant;

/**
 * Health of the registry itself.
 *
 * @param instances               number of registered instances
 * @param upInstances             number of instances with status UP
 * @param renewalsLastMinute      heartbeats (and registrations) received during the last full minute
 * @param renewalThreshold        minimum renewals per minute below which self-preservation kicks in
 * @param selfPreservationActive  true when eviction is currently suspended
 * @param leaseDurationSeconds    lease duration, after which a silent instance is evicted
 * @param renewalIntervalSeconds  expected heartbeat interval
 * @param serverTime              registry clock, to compute heartbeat ages without clock skew
 */
public record RegistryStatus(int instances, int upInstances, long renewalsLastMinute,
                             long renewalThreshold, boolean selfPreservationActive,
                             long leaseDurationSeconds, long renewalIntervalSeconds,
                             Instant serverTime) {
}
