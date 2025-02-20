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

package org.opensearch.rest.action.document;

import org.opensearch.rest.RestRequest.Method;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.test.rest.RestActionTestCase;
import org.junit.Before;

public class RestGetActionTests extends RestActionTestCase {

    @Before
    public void setUpAction() {
        controller().registerHandler(new RestGetAction());
    }

    public void testTypeInPathWithGet() {
        // We're not actually testing anything to do with the client, but need to set this so it doesn't fail the test for being unset.
        verifyingClient.setExecuteVerifier((arg1, arg2) -> null);

        FakeRestRequest.Builder deprecatedRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withPath("/some_index/some_type/some_id");
        dispatchRequest(deprecatedRequest.withMethod(Method.GET).build());
        assertWarnings(RestGetAction.TYPES_DEPRECATION_MESSAGE);

        FakeRestRequest.Builder validRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withPath("/some_index/_doc/some_id");
        dispatchRequest(validRequest.withMethod(Method.GET).build());
    }

    public void testTypeInPathWithHead() {
        // We're not actually testing anything to do with the client, but need to set this so it doesn't fail the test for being unset.
        verifyingClient.setExecuteVerifier((arg1, arg2) -> null);

        FakeRestRequest.Builder deprecatedRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withPath("/some_index/some_type/some_id");
        dispatchRequest(deprecatedRequest.withMethod(Method.HEAD).build());
        assertWarnings(RestGetAction.TYPES_DEPRECATION_MESSAGE);

        FakeRestRequest.Builder validRequest = new FakeRestRequest.Builder(xContentRegistry())
            .withPath("/some_index/_doc/some_id");
        dispatchRequest(validRequest.withMethod(Method.HEAD).build());
    }
}
