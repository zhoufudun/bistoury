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

package qunar.tc.bistoury.remoting.netty;

import com.google.common.collect.Maps;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.common.NamedThreadFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author zhenyu.nie created on 2019 2019/5/28 16:52
 */
public class DefaultTaskStore implements TaskStore {

    private static final Logger logger = LoggerFactory.getLogger(DefaultTaskStore.class);

    private static final ScheduledExecutorService clearExecutor =
            Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("bistoury-task-clear"));

    private static final int CLEAR_INTERVAL_SEC = 10;

    private final ConcurrentMap<String, WrapTask> tasks = Maps.newConcurrentMap();

    private volatile boolean close = false;

    public DefaultTaskStore() {
        clearExecutor.schedule(clearRunningTooLongTask, CLEAR_INTERVAL_SEC, TimeUnit.SECONDS);
    }

    private final Runnable clearRunningTooLongTask = new Runnable() {
        @Override
        public void run() {
            if (close) {
                return;
            }

            try {
                long currentTime = System.currentTimeMillis();
                for (Map.Entry<String, WrapTask> entry : tasks.entrySet()) {
                    WrapTask wrapTask = entry.getValue();
                    RunnableTask task = wrapTask.getTask();
                    if (currentTime - wrapTask.getTimestamp() > task.getMaxRunningMs()) {
                        logger.warn("try cancel task [{}], running too long times", task.getId());
                        task.cancel();
                        tasks.remove(entry.getKey()); //任务超过最大执行时间，取消任务
                    }
                }
            } finally {
                clearExecutor.schedule(clearRunningTooLongTask, CLEAR_INTERVAL_SEC, TimeUnit.SECONDS);
            }
        }
    };

    @Override
    public boolean register(RunnableTask task) {
        synchronized (this) {
            if (close) {
                return false;
            }
            // 执行任务之前，先记录，超过一定时间需要取消任务，防止有些任务时间太长或者执行失败没有从tasks删除
            WrapTask old = tasks.putIfAbsent(task.getId(), new WrapTask(task, System.currentTimeMillis()));
            return old == null;
        }
    }

    @Override
    public void finish(String id) {
        tasks.remove(id);
    }

    @Override
    public void pause(String id) {
        WrapTask task = tasks.get(id);
        if (task != null) {
            task.getTask().pause();
        }
    }

    @Override
    public void resume(String id) {
        WrapTask task = tasks.get(id);
        if (task != null) {
            task.getTask().resume();
        }
    }

    @Override
    public void cancel(String id) {
        WrapTask task = tasks.get(id);
        if (task != null) {
            task.getTask().cancel();
            tasks.remove(id);
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            close = true;
        }

        logger.warn("close task store, cancel all task");
        for (WrapTask wrapTask : tasks.values()) {
            wrapTask.getTask().cancel();
        }
    }

    private static class WrapTask {

        private final RunnableTask task;

        private final long timestamp;

        private WrapTask(RunnableTask task, long timestamp) {
            this.task = task; // DefaultRunningTask
            this.timestamp = timestamp; // 任务开始执行时间
        }

        public RunnableTask getTask() {
            return task;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }
}
