/*******************************************************************************
 * Copyright 2009-2020 Exactpro (Exactpro Systems Limited)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/
package com.exactpro.sf.externalapi.codec

import com.exactpro.sf.common.messages.IMessage
import java.io.Closeable

interface IExternalCodec : Closeable {
    /**
     * Encodes provided [message] into a byte array
     * @param message message to encode
     * @return encoded message as a byte array
     * @throws [com.exactpro.sf.externalapi.codec.impl.MissingContextException] the codec needs context to encode the [message].
     *          In this case use should use [encodeContextual] method instead
     */
    fun encode(message: IMessage): ByteArray

    /**
     * Encodes provided [message] into a byte array using information from provided [context].
     *
     * By default this method delegates call to the [encode] method.
     * @param message message to encode
     * @param context the encoding context
     * @return encoded message as a byte array
     */
    fun encode(message: IMessage, context: IExternalCodecContext): ByteArray {
        return encode(message)
    }

    /**
     * Decodes provided byte array into a list of messages
     * @param data byte array to decode
     * @return list of decoded messages
     * @throws [com.exactpro.sf.externalapi.codec.impl.MissingContextException] the codec needs context to decode the [data].
     *          In this case use should use [decodeContextual] method instead
     */
    fun decode(data: ByteArray): List<IMessage>

    /**
     * Decodes provided byte array into a list of messages using information from provided [context].
     *
     * By default this method delegates call to the [decode] method.
     * @param data byte array to decode
     * @param context the decoding context
     * @return list of decoded messages
     */
    fun decode(data: ByteArray, context: IExternalCodecContext): List<IMessage> {
        return decode(data)
    }
}

