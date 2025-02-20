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

package org.opensearch.painless.action;

import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.io.stream.NamedWriteableRegistry;
import org.opensearch.common.io.stream.StreamInput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.settings.Settings;
import org.opensearch.common.xcontent.LoggingDeprecationHandler;
import org.opensearch.common.xcontent.NamedXContentRegistry;
import org.opensearch.common.xcontent.XContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.XContentType;
import org.opensearch.index.query.MatchAllQueryBuilder;
import org.opensearch.index.query.QueryBuilder;
import org.opensearch.painless.action.PainlessExecuteAction.Request.ContextSetup;
import org.opensearch.script.Script;
import org.opensearch.script.ScriptContext;
import org.opensearch.script.ScriptType;
import org.opensearch.search.SearchModule;
import org.opensearch.test.AbstractWireSerializingTestCase;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Collections;

import static org.hamcrest.Matchers.equalTo;

public class PainlessExecuteRequestTests extends AbstractWireSerializingTestCase<PainlessExecuteAction.Request> {

    // Testing XContent serialization manually here, because the xContentType field in ContextSetup determines
    // how the request needs to parse and the xcontent serialization framework randomizes that. The XContentType
    // is not known and accessable when the test request instance is created in the xcontent serialization framework.
    // Changing that is a big change. Writing a custom xcontent test here is the best option for now, because as far
    // as I know this request class is the only case where this is a problem.
    public final void testFromXContent() throws Exception {
        for (int i = 0; i < 20; i++) {
            PainlessExecuteAction.Request testInstance = createTestInstance();
            ContextSetup contextSetup = testInstance.getContextSetup();
            XContent xContent = randomFrom(XContentType.values()).xContent();
            if (contextSetup != null && contextSetup.getXContentType() != null) {
                xContent = contextSetup.getXContentType().xContent();
            }

            try (XContentBuilder builder = XContentBuilder.builder(xContent)) {
                builder.value(testInstance);
                StreamInput instanceInput = BytesReference.bytes(builder).streamInput();
                try (XContentParser parser = xContent.createParser(xContentRegistry(), LoggingDeprecationHandler.INSTANCE, instanceInput)) {
                    PainlessExecuteAction.Request result = PainlessExecuteAction.Request.parse(parser);
                    assertThat(result, equalTo(testInstance));
                }
            }
        }
    }

    @Override
    protected NamedWriteableRegistry getNamedWriteableRegistry() {
        return new NamedWriteableRegistry(new SearchModule(Settings.EMPTY, false, Collections.emptyList()).getNamedWriteables());
    }

    @Override
    protected NamedXContentRegistry xContentRegistry() {
        return new NamedXContentRegistry(new SearchModule(Settings.EMPTY, false, Collections.emptyList()).getNamedXContents());
    }

    @Override
    protected PainlessExecuteAction.Request createTestInstance() {
        Script script = new Script(randomAlphaOfLength(10));
        ScriptContext<?> context = randomBoolean() ? randomFrom(PainlessExecuteAction.Request.SUPPORTED_CONTEXTS.values()) : null;
        ContextSetup contextSetup = randomBoolean() ? randomContextSetup() : null;
        return new PainlessExecuteAction.Request(script, context != null ? context.name : null, contextSetup);
    }

    @Override
    protected Writeable.Reader<PainlessExecuteAction.Request> instanceReader() {
        return PainlessExecuteAction.Request::new;
    }

    public void testValidate() {
        Script script = new Script(ScriptType.STORED, null, randomAlphaOfLength(10), Collections.emptyMap());
        PainlessExecuteAction.Request request = new PainlessExecuteAction.Request(script, null, null);
        Exception e = request.validate();
        assertNotNull(e);
        assertEquals("Validation Failed: 1: only inline scripts are supported;", e.getMessage());
    }

    private static ContextSetup randomContextSetup()  {
        String index = randomBoolean() ? randomAlphaOfLength(4) : null;
        QueryBuilder query = randomBoolean() ? new MatchAllQueryBuilder() : null;
        BytesReference doc = null;
        XContentType xContentType = randomFrom(XContentType.values());
        if (randomBoolean()) {
            try {
                XContentBuilder xContentBuilder = XContentBuilder.builder(xContentType.xContent());
                xContentBuilder.startObject();
                xContentBuilder.endObject();
                doc = BytesReference.bytes(xContentBuilder);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        ContextSetup contextSetup = new ContextSetup(index, doc, query);
        contextSetup.setXContentType(xContentType);
        return contextSetup;
    }
}
