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

package qunar.tc.bistoury.agent.common.pid.impl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.agent.common.pid.PidHandler;

/**
 * @author leix.xie
 * @date 2019/7/11 20:19
 * @describe
 */
public class PidBySystemPropertyHandler extends AbstractPidHandler implements PidHandler {
    private static final Logger logger = LoggerFactory.getLogger(PidByJpsHandler.class);

    @Override
    public int priority() {
        return Priority.FROM_SYSTEM_PROPERTY_PRIORITY;
    }
    // win环境下无法执行ps执行，因此，这里可以提前设置好需要监控的程序的pid，方便本地测试
    @Override
    protected int doGetPid() {
        logger.info("qunar.tc.bistoury.agent.common.pid.impl.PidBySystemPropertyHandler.doGetPid");
        return Integer.valueOf(System.getProperty("bistoury.user.pid", "-1"));
    }

}
