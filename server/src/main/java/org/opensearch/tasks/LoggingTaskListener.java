/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.tasks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.message.ParameterizedMessage;

/**
 * A TaskListener that just logs the response at the info level. Used when we
 * need a listener but aren't returning the result to the user.
 */
public final class LoggingTaskListener<Response> implements TaskListener<Response> {
    private static final Logger logger = LogManager.getLogger(LoggingTaskListener.class);

    /**
     * Get the instance of NoopActionListener cast appropriately.
     */
    @SuppressWarnings("unchecked") // Safe because we only toString the response
    public static <Response> TaskListener<Response> instance() {
        return (TaskListener<Response>) INSTANCE;
    }

    private static final LoggingTaskListener<Object> INSTANCE = new LoggingTaskListener<>();

    private LoggingTaskListener() {
    }

    @Override
    public void onResponse(Task task, Response response) {
        logger.info("{} finished with response {}", task.getId(), response);
    }

    @Override
    public void onFailure(Task task, Exception e) {
        logger.warn(() -> new ParameterizedMessage("{} failed with exception", task.getId()), e);
    }
}
