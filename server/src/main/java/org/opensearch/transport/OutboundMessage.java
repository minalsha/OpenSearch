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

package org.opensearch.transport;

import org.opensearch.LegacyESVersion;
import org.opensearch.Version;
import org.opensearch.common.bytes.BytesArray;
import org.opensearch.common.bytes.BytesReference;
import org.opensearch.common.bytes.CompositeBytesReference;
import org.opensearch.common.io.stream.BytesStreamOutput;
import org.opensearch.common.io.stream.StreamOutput;
import org.opensearch.common.io.stream.Writeable;
import org.opensearch.common.util.concurrent.ThreadContext;

import java.io.IOException;
import java.util.Set;

abstract class OutboundMessage extends NetworkMessage {

    private final Writeable message;

    OutboundMessage(ThreadContext threadContext, Version version, byte status, long requestId, Writeable message) {
        super(threadContext, version, status, requestId);
        this.message = message;
    }

    BytesReference serialize(BytesStreamOutput bytesStream) throws IOException {
        bytesStream.setVersion(version);
        bytesStream.skip(TcpHeader.headerSize(version));

        // The compressible bytes stream will not close the underlying bytes stream
        BytesReference reference;
        int variableHeaderLength = -1;
        final long preHeaderPosition = bytesStream.position();

        if (version.onOrAfter(TcpHeader.VERSION_WITH_HEADER_SIZE)) {
            writeVariableHeader(bytesStream);
            variableHeaderLength = Math.toIntExact(bytesStream.position() - preHeaderPosition);
        }

        try (CompressibleBytesOutputStream stream =
                 new CompressibleBytesOutputStream(bytesStream, TransportStatus.isCompress(status))) {
            stream.setVersion(version);
            stream.setFeatures(bytesStream.getFeatures());

            if (variableHeaderLength == -1) {
                writeVariableHeader(stream);
            }
            reference = writeMessage(stream);
        }

        bytesStream.seek(0);
        final int contentSize = reference.length() - TcpHeader.headerSize(version);
        TcpHeader.writeHeader(bytesStream, requestId, status, version, contentSize, variableHeaderLength);
        return reference;
    }

    protected void writeVariableHeader(StreamOutput stream) throws IOException {
        threadContext.writeTo(stream);
    }

    protected BytesReference writeMessage(CompressibleBytesOutputStream stream) throws IOException {
        final BytesReference zeroCopyBuffer;
        if (message instanceof BytesTransportRequest) {
            BytesTransportRequest bRequest = (BytesTransportRequest) message;
            bRequest.writeThin(stream);
            zeroCopyBuffer = bRequest.bytes;
        } else if (message instanceof RemoteTransportException) {
            stream.writeException((RemoteTransportException) message);
            zeroCopyBuffer = BytesArray.EMPTY;
        } else {
            message.writeTo(stream);
            zeroCopyBuffer = BytesArray.EMPTY;
        }
        // we have to call materializeBytes() here before accessing the bytes. A CompressibleBytesOutputStream
        // might be implementing compression. And materializeBytes() ensures that some marker bytes (EOS marker)
        // are written. Otherwise we barf on the decompressing end when we read past EOF on purpose in the
        // #validateRequest method. this might be a problem in deflate after all but it's important to write
        // the marker bytes.
        final BytesReference message = stream.materializeBytes();
        if (zeroCopyBuffer.length() == 0) {
            return message;
        } else {
            return CompositeBytesReference.of(message, zeroCopyBuffer);
        }
    }

    static class Request extends OutboundMessage {

        private final String[] features;
        private final String action;

        Request(ThreadContext threadContext, String[] features, Writeable message, Version version, String action, long requestId,
                boolean isHandshake, boolean compress) {
            super(threadContext, version, setStatus(compress, isHandshake, message), requestId, message);
            this.features = features;
            this.action = action;
        }

        @Override
        protected void writeVariableHeader(StreamOutput stream) throws IOException {
            super.writeVariableHeader(stream);
            if (version.onOrAfter(LegacyESVersion.V_6_3_0)) {
                stream.writeStringArray(features);
            }
            stream.writeString(action);
        }

        private static byte setStatus(boolean compress, boolean isHandshake, Writeable message) {
            byte status = 0;
            status = TransportStatus.setRequest(status);
            if (compress && OutboundMessage.canCompress(message)) {
                status = TransportStatus.setCompress(status);
            }
            if (isHandshake) {
                status = TransportStatus.setHandshake(status);
            }

            return status;
        }
    }

    static class Response extends OutboundMessage {

        private final Set<String> features;

        Response(ThreadContext threadContext, Set<String> features, Writeable message, Version version, long requestId,
                 boolean isHandshake, boolean compress) {
            super(threadContext, version, setStatus(compress, isHandshake, message), requestId, message);
            this.features = features;
        }

        @Override
        protected void writeVariableHeader(StreamOutput stream) throws IOException {
            super.writeVariableHeader(stream);
            stream.setFeatures(features);
        }

        private static byte setStatus(boolean compress, boolean isHandshake, Writeable message) {
            byte status = 0;
            status = TransportStatus.setResponse(status);
            if (message instanceof RemoteTransportException) {
                status = TransportStatus.setError(status);
            }
            if (compress) {
                status = TransportStatus.setCompress(status);
            }
            if (isHandshake) {
                status = TransportStatus.setHandshake(status);
            }

            return status;
        }
    }

    private static boolean canCompress(Writeable message) {
        return message instanceof BytesTransportRequest == false;
    }
}
