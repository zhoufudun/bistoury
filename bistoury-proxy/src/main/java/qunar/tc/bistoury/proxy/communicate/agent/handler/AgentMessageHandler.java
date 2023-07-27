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

package qunar.tc.bistoury.proxy.communicate.agent.handler;

import com.google.common.collect.ImmutableMap;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.remoting.protocol.Datagram;

import java.util.List;
import java.util.Map;

/**
 * @author zhenyu.nie created on 2019 2019/5/14 17:13
 */
@ChannelHandler.Sharable
public class AgentMessageHandler extends SimpleChannelInboundHandler<Datagram> {

    private static final Logger logger = LoggerFactory.getLogger(AgentMessageHandler.class);

    private final Map<Integer, AgentMessageProcessor> processorMap;

    public AgentMessageHandler(List<AgentMessageProcessor> processors) {
        ImmutableMap.Builder<Integer, AgentMessageProcessor> builder = new ImmutableMap.Builder<>();
        for (AgentMessageProcessor processor : processors) {
            for (int code: processor.codes()) {
                builder.put(code, processor);
                /**
                 * AgentMessageHandler init code=1, processor=[1]
                 * AgentMessageHandler init code=504, processor=[504, 505, 507, 506]
                 * AgentMessageHandler init code=505, processor=[504, 505, 507, 506]
                 * AgentMessageHandler init code=507, processor=[504, 505, 507, 506]
                 * AgentMessageHandler init code=506, processor=[504, 505, 507, 506]
                 * AgentMessageHandler init code=-2, processor=[-2, -1, -3]
                 * AgentMessageHandler init code=-1, processor=[-2, -1, -3]
                 * AgentMessageHandler init code=-3, processor=[-2, -1, -3]
                 * AgentMessageHandler init code=0, processor=[0]
                 */
                logger.info("AgentMessageHandler init code={}, processor={}",code,processor.codes());
            }
        }
        processorMap = builder.build();
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Datagram message) throws Exception {
        int code = message.getHeader().getCode();
        logger.info("proxy receive agent request, code={}, connect with agent={}",code,ctx.channel().toString());
        AgentMessageProcessor messageProcessor = processorMap.get(code);
        if (messageProcessor == null) {
            message.release();
            logger.warn("can not process message code [{}], {}", code, ctx.channel());
            return;
        }
        logger.info("proxy trigger channelRead0, messageProcessor name={}",messageProcessor.getClass().getCanonicalName());

        messageProcessor.process(ctx, message);
    }
}
