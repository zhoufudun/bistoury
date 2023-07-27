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

package qunar.tc.bistoury.agent;

import com.google.common.collect.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.agent.common.task.AgentGlobalTaskFactory;

import java.util.List;
import java.util.ServiceLoader;

/**
 * @author zhenyu.nie created on 2019 2019/1/8 17:15
 */
public class AgentGlobalTaskInitializer {
    private static final Logger logger = LoggerFactory.getLogger(AgentGlobalTaskInitializer.class);

    private static boolean init = false;

    /**
     * 根据SPI加载所有的AgentGlobalTaskFactory实现类，然后调用他们的start方法
     */
    public static synchronized void init() {
        if (!init) {
            List<AgentGlobalTaskFactory> tasks = ImmutableList.copyOf(ServiceLoader.load(AgentGlobalTaskFactory.class));
            for (AgentGlobalTaskFactory task : tasks) {
                task.start();
                logger.info("method AgentGlobalTaskInitializer.init start task success, task name={}",task.getClass().getCanonicalName());
            }
            init = true;
        }
    }
}
