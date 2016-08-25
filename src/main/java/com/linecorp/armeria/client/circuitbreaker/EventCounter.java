/*
 * Copyright 2016 LINE Corporation
 *
 * LINE Corporation licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.linecorp.armeria.client.circuitbreaker;

import java.util.Optional;

interface EventCounter {

    /**
     * Returns the current {@link EventCount}.
     */
    EventCount count();

    /**
     * Counts success events.
     *
     * @return An {@link Optional} containing the current {@link EventCount} if it has been updated,
     *         or else an empty {@link Optional}.
     */
    Optional<EventCount> onSuccess();

    /**
     * Counts failure events.
     *
     * @return An {@link Optional} containing the current {@link EventCount} if it has been updated,
     *         or else an empty {@link Optional}.
     */
    Optional<EventCount> onFailure();
}
