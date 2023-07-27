
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

package qunar.tc.bistoury.serverside.configuration.local;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.common.NamedThreadFactory;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author keli.wang
 * @since 2018-11-27
 */
class ConfigWatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ConfigWatcher.class);

    private final CopyOnWriteArrayList<Watch> watches;
    private final ScheduledExecutorService watcherExecutor;

    ConfigWatcher() {
        this.watches = new CopyOnWriteArrayList<>();
        this.watcherExecutor = Executors.newSingleThreadScheduledExecutor(new NamedThreadFactory("local-config-watcher"));

        start();
    }
    // 定时检查配置文件是否有更新，10s检查一次
    private void start() {
        watcherExecutor.scheduleWithFixedDelay(new Runnable() {
            @Override
            public void run() {
                checkAllWatches();
            }
        }, 10, 10, TimeUnit.SECONDS);
    }

    private void checkAllWatches() {
        for (Watch watch : watches) {
            try {
                checkWatch(watch);
            } catch (Exception e) {
                LOG.error("check config failed. config: {}", watch.getConfig(), e);
            }
        }
    }

    private void checkWatch(final Watch watch) {
        final LocalDynamicConfig config = watch.getConfig();
        final long lastModified = config.getLastModified();
        if (lastModified == watch.getLastModified()) {
            return;
        }

        watch.setLastModified(lastModified);
        config.onConfigModified(); // 文件由修改触发配置更新逻辑
    }

    void addWatch(final LocalDynamicConfig config) {
        final Watch watch = new Watch(config);
        watch.setLastModified(config.getLastModified());
        watches.add(watch);
    }

    private static final class Watch {
        private final LocalDynamicConfig config; // 举例：LocalDynamicConfig{name='server.properties'}
        private volatile long lastModified; // 该配置对象的文件最近修改时间

        private Watch(final LocalDynamicConfig config) {
            this.config = config;
        }

        public LocalDynamicConfig getConfig() {
            return config;
        }

        long getLastModified() {
            return lastModified;
        }

        void setLastModified(final long lastModified) {
            this.lastModified = lastModified;
        }
    }
}
