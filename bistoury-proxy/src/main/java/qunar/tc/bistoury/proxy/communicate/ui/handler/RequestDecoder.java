/*
 * Copyright (C) 2019 Qunar, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package qunar.tc.bistoury.proxy.communicate.ui.handler;

import com.google.common.base.Charsets;
import com.google.common.base.Optional;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.http.websocketx.BinaryWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import qunar.tc.bistoury.remoting.protocol.RequestData;
import qunar.tc.bistoury.proxy.communicate.ui.UiResponses;
import qunar.tc.bistoury.serverside.common.encryption.RequestEncryption;
import qunar.tc.bistoury.remoting.protocol.CommandCode;

import java.io.IOException;
import java.util.List;

/**
 * @author zhenyu.nie created on 2019 2019/5/16 15:43
 */
@ChannelHandler.Sharable
public class RequestDecoder extends MessageToMessageDecoder<WebSocketFrame> {

    private final RequestEncryption encryption;

    public RequestDecoder(RequestEncryption encryption) {
        this.encryption = encryption;
    }
    // 将WebSocketFrame转化为RequestData
    @Override
    protected void decode(ChannelHandlerContext ctx, WebSocketFrame msg, List<Object> out) throws Exception {
        RequestData<String> data = parse(msg); // Request{app='bistoury_demo_app', type=10, agentServerInfos=null, command='', token='5a9f3556960290d9a62fdba1a1255b9b', user='admin'}
        if (data != null) {
            //code转换，将老agent的code转换为新agent能识别的code
            Optional<CommandCode> optional = CommandCode.valueOfOldCode(data.getType());
            if (optional.isPresent()) {
                data.setType(optional.get().getCode());
                out.add(data); // WebSocketFrame转为RequestData消息后, 传递给下一个handler，下一个handler能直接获取RequestData兑现
            } else {
                ctx.writeAndFlush(UiResponses.createWrongFrameResponse());
            }
        } else {
            ctx.writeAndFlush(UiResponses.createWrongFrameResponse());
        }
    }

    private RequestData<String> parse(WebSocketFrame msg) throws IOException {
        if (msg instanceof TextWebSocketFrame) {
            String text = ((TextWebSocketFrame) msg).text(); // demo:   {"0":"rWyLwiZBc4ew63Ck79Sr0crZ3P5YRuyFZgnMfJA0esg3ZjeR2zLAFYQUIcF+26c55e1ATc3q6qSDhwXrLE2IEIyrJdMA0iBFQ8gXJmtJ7Y8U7ZsdavGqH1Nyf+XCJxcpgHjO1sQiEXkrmCtqJifaoncehJV+p5vIjDaAOTn8iHk=","1":"Sz4A8LYtvz4y7zSHqd2TXuELgu/bC+dVErh+NCGB74YbMaV7o1ZhF3ork+SjOXetWhzBBNwXIXw8Oso+AyHR28q6/KkBSH1vUVsSTgXfvwlOqFy2X5vLyhk0zXMh+l9gFRI3aEcNXE6M3sVGYHA4y9J76d8wgA0MRgBpXmTXXYPC/DTsqL7Myw=="}
            return encryption.decrypt(text);
        } else if (msg instanceof BinaryWebSocketFrame) {
            ByteBuf content = msg.content();
            byte[] data = new byte[content.readableBytes()];
            content.readBytes(data);
            return encryption.decrypt(new String(data, Charsets.UTF_8));
        } else {
            return null;
        }
    }
}
