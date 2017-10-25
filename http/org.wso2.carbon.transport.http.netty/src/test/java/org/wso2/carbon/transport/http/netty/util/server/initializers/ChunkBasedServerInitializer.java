/*
 * Copyright (c) 2017, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.transport.http.netty.util.server.initializers;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.LastHttpContent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.wso2.carbon.transport.http.netty.common.Constants;

import static io.netty.handler.codec.http.HttpHeaders.Names.CONNECTION;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static io.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static io.netty.handler.codec.http.HttpHeaders.Names.TRANSFER_ENCODING;
import static io.netty.handler.codec.http.HttpHeaders.is100ContinueExpected;
import static io.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static io.netty.handler.codec.http.HttpResponseStatus.CONTINUE;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;

/**
 * An initializer class for HTTP Server Which set Transfer-encoding headers.
 */
public class ChunkBasedServerInitializer extends HTTPServerInitializer {

    private static final Logger logger = LoggerFactory.getLogger(ChunkBasedServerInitializer.class);

    private String stringContent;
    private String contentType;
    private int responseStatusCode = 200;
    private HttpRequest req;

    public ChunkBasedServerInitializer(String stringContent, String contentType, int responseStatusCode) {
        this.stringContent = stringContent;
        this.contentType = contentType;
        this.responseStatusCode = responseStatusCode;
    }

    protected void addBusinessLogicHandler(Channel channel) {
        channel.pipeline().addLast("handler", new ChunkBasedServerHandler());
    }

    private class ChunkBasedServerHandler extends ChannelInboundHandlerAdapter {
        private String length;
        private String encoding;

        @Override
        public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
            if (stringContent != null) {
                ByteBuf content = Unpooled.wrappedBuffer(stringContent.getBytes("UTF-8"));
                if (msg instanceof HttpRequest) {
                    req = (HttpRequest) msg;
                } else if (msg instanceof LastHttpContent) {
                    if (is100ContinueExpected(req)) {
                        ctx.writeAndFlush(new DefaultFullHttpResponse(HTTP_1_1, CONTINUE));
                    }
                    boolean keepAlive = isKeepAlive(req);
                    HttpResponseStatus httpResponseStatus = new HttpResponseStatus(responseStatusCode,
                            HttpResponseStatus.valueOf(responseStatusCode).reasonPhrase());
                    FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, httpResponseStatus, content);
                    response.headers().set(CONTENT_TYPE, contentType);
                    length = req.headers().get(Constants.HTTP_CONTENT_LENGTH);
                    encoding = req.headers().get(Constants.HTTP_TRANSFER_ENCODING);
                    if (length != null) {
                        response.headers().set(CONTENT_LENGTH, content.readableBytes());
                    } else if (encoding != null) {
                        response.headers().set(TRANSFER_ENCODING, Constants.CHUNKED);
                    }
                    if (!keepAlive) {
                        ctx.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
                        logger.debug("Writing response with data to client-connector");
                        logger.debug("Closing the client-connector connection");
                    } else {
                        response.headers().set(CONNECTION, HttpHeaders.Values.KEEP_ALIVE);
                        ctx.writeAndFlush(response);
                        logger.debug("Writing response with data to client-connector");
                    }
                }
            }
        }
    }
}
