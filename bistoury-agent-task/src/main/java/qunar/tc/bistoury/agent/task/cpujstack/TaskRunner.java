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

package qunar.tc.bistoury.agent.task.cpujstack;

import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.io.CharSource;
import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import qunar.tc.bistoury.agent.common.config.AgentConfig;
import qunar.tc.bistoury.agent.common.cpujstack.KvUtils;
import qunar.tc.bistoury.agent.common.cpujstack.ThreadInfo;
import qunar.tc.bistoury.agent.common.kv.KvDb;
import qunar.tc.bistoury.agent.common.pid.PidUtils;
import qunar.tc.bistoury.agent.common.util.DateUtils;
import qunar.tc.bistoury.agent.task.proc.ProcUtil;
import qunar.tc.bistoury.agent.task.proc.ProcessStateCalculator;
import qunar.tc.bistoury.common.JacksonSerializer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author zhenyu.nie created on 2019 2019/1/8 19:26
 */
public class TaskRunner implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(TaskRunner.class);

    private static final Splitter SPACE_SPLITTER = Splitter.on(' ').trimResults().omitEmptyStrings();

    private static final Joiner LINE_JOINER = Joiner.on('\n');

    private static final int THREAD_NAME_START_INDEX = "\"".length();

    private static final String THREAD_ID_PREFIX = " nid=";

    private static final String THREAD_STATE_PREFIX = "java.lang.Thread.State: ";

    private static final String NO_THREAD_NAME = "NoThreadName#";

    private final AgentConfig agentConfig;

    private final KvDb kvDb;

    private final PidExecutor jstackExecutor;

    private final PidRecordExecutor momentCpuTimeExecutor;

    public TaskRunner(AgentConfig agentConfig, KvDb kvDb, PidExecutor jstackExecutor, PidRecordExecutor momentCpuTimeExecutor) {
        this.agentConfig = agentConfig;
        this.kvDb = kvDb;
        this.jstackExecutor = jstackExecutor;
        this.momentCpuTimeExecutor = momentCpuTimeExecutor;
    }

    @Override
    public void run() {
        try {
            doRun();
        } catch (Throwable e) {
            logger.error("cpu jstack tast run error", e);
        }
    }

    private void doRun() throws Exception {
        if (!agentConfig.isCpuJStackOn()) {
            logger.debug("cpu jstack task not open");
            return;
        }

        int pid = PidUtils.getPid();
        final String timestamp = DateUtils.TIME_FORMATTER.print(DateTime.now());
        if (pid > 0) {
            logger.info("start cpu jstack task, pid {}, timestamp {}", pid, timestamp);
        } else {
            logger.warn("cannot cpu jstack task, pid {}, timestamp {}", pid, timestamp);
            return;
        }

        String successBefore = kvDb.get(KvUtils.getCollectSuccessKey(timestamp));
        if (Boolean.parseBoolean(successBefore)) {
            logger.warn("cpu jstack task success before, ignore run, timestamp {}", timestamp);
            return;
        }

        String jstackResult = jstackExecutor.execute(pid);
//        logger.info("jstackResult="+jstackResult);
        /**
         * 获取线程过去一分钟的cpu使用率
         */
        Map<String, Double> threadMinuteTimes = ProcUtil.transformHexThreadId(ProcessStateCalculator.getInstance().threadCpuMinuteUsage(pid));
        Map<String, ThreadInfo> threadInfos = parseThreadInfos(jstackResult);
        addThreadMinuteCpuTime(threadInfos, threadMinuteTimes);

        int totalTime = 0;
        Map<String, String> dbMinuteCpuTimes = new HashMap<>();
        for (Map.Entry<String, Double> entry : threadMinuteTimes.entrySet()) {
            Integer time = (int) (entry.getValue() * 10000);
            if (time > 0) {
                dbMinuteCpuTimes.put(KvUtils.getThreadMinuteCpuTimeKey(timestamp, entry.getKey()), String.valueOf(time));
                totalTime += time;
            }
        }
        /**
         * 统计数据插入key-value数据库
         */
        kvDb.putBatch(dbMinuteCpuTimes);

        kvDb.put(KvUtils.getThreadNumKey(timestamp), String.valueOf(threadMinuteTimes.size()));
        kvDb.put(KvUtils.getThreadMinuteCpuTimeKey(timestamp), String.valueOf(totalTime));
        kvDb.put(KvUtils.getJStackResultKey(timestamp), jstackResult);
        kvDb.put(KvUtils.getThreadInfoKey(timestamp), JacksonSerializer.serialize(threadInfos));
        Futures.addCallback(momentCpuTimeExecutor.execute(pid), momentCpuUsageCallback(timestamp));
    }

    private FutureCallback<Map<Integer, Double>> momentCpuUsageCallback(final String timestamp) {
        return new FutureCallback<Map<Integer, Double>>() {
            @Override
            public void onSuccess(Map<Integer, Double> momentCpuTime) {
                Map<String, Double> transformMomentCpuTime = ProcUtil.transformHexThreadId(momentCpuTime);
                int totalTime = 0;
                Map<String, String> dbMomentCpuTimes = new HashMap<>();
                for (Map.Entry<String, Double> entry : transformMomentCpuTime.entrySet()) {
                    int time = (int) (entry.getValue() * 10000);
                    dbMomentCpuTimes.put(KvUtils.getThreadMomentCpuTimeKey(timestamp, entry.getKey()), String.valueOf(time));
                    totalTime += time;
                }
                kvDb.putBatch(dbMomentCpuTimes);
                kvDb.put(KvUtils.getThreadMomentCpuTimeKey(timestamp), String.valueOf(totalTime));
                kvDb.put(KvUtils.getCollectSuccessKey(timestamp), "true");
            }

            @Override
            public void onFailure(Throwable throwable) {
                logger.error("timestamp : {},fail get moment cpu usage", timestamp, throwable);
                kvDb.put(KvUtils.getCollectSuccessKey(timestamp), "true");
            }
        };
    }

    private void addThreadMinuteCpuTime(Map<String, ThreadInfo> threadInfos, Map<String, Double> threadMinuteTimes) {
        for (Map.Entry<String, ThreadInfo> entry : threadInfos.entrySet()) {
            String threadId = entry.getKey();
            Double time = threadMinuteTimes.get(threadId);
            if (time == null) {
                time = 0.0;
            }
            Integer minuteCpuTime = (int) (time * 10000);
            entry.getValue().setMinuteCpuTime(minuteCpuTime);
        }
    }

    private Map<String, ThreadInfo> parseThreadInfos(String jstackResult) {
        try {
            Map<String, ThreadInfo> threadInfos = Maps.newHashMap();
            // 一行行读取jstackResult
            /**
             * jstackResult如下：
             * Full thread dump Java HotSpot(TM) 64-Bit Server VM (25.162-b12 mixed mode):
             *
             * "DestroyJavaVM" #46 prio=5 os_prio=0 tid=0x0000000022d79800 nid=0x574 waiting on condition [0x0000000000000000]
             *    java.lang.Thread.State: RUNNABLE
             *
             * "http-nio-8081-Acceptor" #45 daemon prio=5 os_prio=0 tid=0x0000000022d7c800 nid=0x5344 runnable [0x00000000256bf000]
             *    java.lang.Thread.State: RUNNABLE
             * 	at sun.nio.ch.ServerSocketChannelImpl.accept0(Native Method)
             * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:422)
             * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:250)
             * 	- locked <0x0000000771015d38> (a java.lang.Object)
             * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:546)
             * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:79)
             * 	at org.apache.tomcat.util.net.Acceptor.run(Acceptor.java:129)
             * 	at java.lang.Thread.run(Thread.java:748)
             *
             * "http-nio-8081-Poller" #44 daemon prio=5 os_prio=0 tid=0x0000000022d78000 nid=0x52e4 runnable [0x00000000255be000]
             *    java.lang.Thread.State: RUNNABLE
             * 	at sun.nio.ch.WindowsSelectorImpl$SubSelector.poll0(Native Method)
             * 	at sun.nio.ch.WindowsSelectorImpl$SubSelector.poll(WindowsSelectorImpl.java:296)
             * 	at sun.nio.ch.WindowsSelectorImpl$SubSelector.access$400(WindowsSelectorImpl.java:278)
             * 	at sun.nio.ch.WindowsSelectorImpl.doSelect(WindowsSelectorImpl.java:159)
             * 	at sun.nio.ch.SelectorImpl.lockAndDoSelect(SelectorImpl.java:86)
             * 	- locked <0x0000000771016580> (a sun.nio.ch.Util$3)
             * 	- locked <0x0000000771016570> (a java.util.Collections$UnmodifiableSet)
             * 	- locked <0x0000000771016400> (a sun.nio.ch.WindowsSelectorImpl)
             * 	at sun.nio.ch.SelectorImpl.select(SelectorImpl.java:97)
             * 	at org.apache.tomcat.util.net.NioEndpoint$Poller.run(NioEndpoint.java:807)
             * 	at java.lang.Thread.run(Thread.java:748)
             *
             */
            List<String> lines = CharSource.wrap(jstackResult).readLines();
            AtomicInteger index = new AtomicInteger(0);

            while (index.get() < lines.size()) {
                ThreadInfo threadInfo = parseThreadInfo(lines, index);
                if (threadInfo != null) {
                    threadInfos.put(threadInfo.getId(), threadInfo);
                }
            }
            return threadInfos;
        } catch (Exception e) {
            logger.error("parse thread info error, {}", jstackResult, e);
            throw new RuntimeException("parse thread info error");
        }
    }

    /**
     * Full thread dump Java HotSpot(TM) 64-Bit Server VM (25.162-b12 mixed mode):
     *
     * "DestroyJavaVM" #46 prio=5 os_prio=0 tid=0x0000000022d79800 nid=0x574 waiting on condition [0x0000000000000000]
     *    java.lang.Thread.State: RUNNABLE
     *
     * "http-nio-8081-Acceptor" #45 daemon prio=5 os_prio=0 tid=0x0000000022d7c800 nid=0x5344 runnable [0x00000000256bf000]
     *    java.lang.Thread.State: RUNNABLE
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept0(Native Method)
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:422)
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:250)
     * 	- locked <0x0000000771015d38> (a java.lang.Object)
     * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:546)
     * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:79)
     * 	at org.apache.tomcat.util.net.Acceptor.run(Acceptor.java:129)
     * 	at java.lang.Thread.run(Thread.java:748)
     *
     * "http-nio-8081-Poller" #44 daemon prio=5 os_prio=0 tid=0x0000000022d78000 nid=0x52e4 runnable [0x00000000255be000]
     *    java.lang.Thread.State: RUNNABLE
     * 	at sun.nio.ch.WindowsSelectorImpl$SubSelector.poll0(Native Method)
     * 	at sun.nio.ch.WindowsSelectorImpl$SubSelector.poll(WindowsSelectorImpl.java:296)
     * 	at sun.nio.ch.WindowsSelectorImpl$SubSelector.access$400(WindowsSelectorImpl.java:278)
     * 	at sun.nio.ch.WindowsSelectorImpl.doSelect(WindowsSelectorImpl.java:159)
     * 	at sun.nio.ch.SelectorImpl.lockAndDoSelect(SelectorImpl.java:86)
     * 	- locked <0x0000000771016580> (a sun.nio.ch.Util$3)
     * 	- locked <0x0000000771016570> (a java.util.Collections$UnmodifiableSet)
     * 	- locked <0x0000000771016400> (a sun.nio.ch.WindowsSelectorImpl)
     * 	at sun.nio.ch.SelectorImpl.select(SelectorImpl.java:97)
     * 	at org.apache.tomcat.util.net.NioEndpoint$Poller.run(NioEndpoint.java:807)
     * 	at java.lang.Thread.run(Thread.java:748)
     *
     * 	..................
     * 	..................
     * 	..................
     * @param lines
     * @param index
     * @return
     */
    private ThreadInfo parseThreadInfo(List<String> lines, AtomicInteger index) {
        int firstLine = findFirstLine(lines, index); // 获取第一行位置，这一行以“开头，并且包含nid
        if (firstLine < 0) {
            index.set(lines.size());
            return null;
        }

        String threadFirstLine = lines.get(firstLine);
        /**
         *  解析出线程信息，例如
         *  "http-nio-8081-Acceptor" #45 daemon prio=5 os_prio=0 tid=0x0000000022d7c800 nid=0x5344 runnable [0x00000000256bf000]
         *
         *  从这一行解析出线程信息
         */
        ThreadInfo threadInfo = parseThreadFirstLine(threadFirstLine);
        int secondLine = firstLine + 1;
        if (secondLine == lines.size()) {
            index.set(lines.size());
            return threadInfo;
        }

        String threadSecondLine = lines.get(secondLine);
        if (Strings.isNullOrEmpty(threadSecondLine)) {
            index.set(secondLine);
            return threadInfo;
        }
        /**
         *  threadSecondLine存在线程状态，解析出线程状态
         * "http-nio-8081-Acceptor" #45 daemon prio=5 os_prio=0 tid=0x0000000022d7c800 nid=0x5344 runnable [0x00000000256bf000]
         *    java.lang.Thread.State: RUNNABLE
         */
        String state = parseThreadState(threadSecondLine);
        threadInfo.setState(state);


        index.set(secondLine);
        while (index.get() < lines.size() && !Strings.isNullOrEmpty(lines.get(index.get()))) {
            index.incrementAndGet(); // 一直解析到空行位置，此时firstLine~index都是线程stack信息
        }
        /**
         * 解析出第二行以后的信息为stack信息
         *
         * "http-nio-8081-Acceptor" #45 daemon prio=5 os_prio=0 tid=0x0000000022d7c800 nid=0x5344 runnable [0x00000000256bf000]
         *    java.lang.Thread.State: RUNNABLE
         * 	    at sun.nio.ch.ServerSocketChannelImpl.accept0(Native Method)
         * 	    at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:422)
         * 	    at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:250)
         * 	    - locked <0x0000000771015d38> (a java.lang.Object)
         * 	    at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:546)
         * 	    at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:79)
         * 	    at org.apache.tomcat.util.net.Acceptor.run(Acceptor.java:129)
         * 	    at java.lang.Thread.run(Thread.java:748)
         */
        List<String> stackLines = lines.subList(firstLine, index.get());
        String stack = LINE_JOINER.join(stackLines);
        threadInfo.setStack(stack);
        /**
         * 解析出lock相关的信息
         *
         */
        List<String> lockOn = parseLockOn(stackLines);
        threadInfo.setLockOn(lockOn);

        return threadInfo;
    }

    /**
     *  解析出lock相关信息：
     *
     *  "http-nio-8081-Acceptor" #45 daemon prio=5 os_prio=0 tid=0x0000000022d7c800 nid=0x5344 runnable [0x00000000256bf000]
     *    java.lang.Thread.State: RUNNABLE
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept0(Native Method)
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:422)
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:250)
     * 	- locked <0x0000000771015d38> (a java.lang.Object)
     * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:546)
     * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:79)
     * 	at org.apache.tomcat.util.net.Acceptor.run(Acceptor.java:129)
     * 	at java.lang.Thread.run(Thread.java:748)
     *
     *
     * @param lines
     * @return
     */
    private List<String> parseLockOn(List<String> lines) {
        if (lines.size() < 3) {
            return ImmutableList.of();
        }

        Set<String> lock = Sets.newLinkedHashSet();
        for (int lineIndex = lines.size() - 1; lineIndex >= 2; lineIndex--) {
            String line = lines.get(lineIndex).trim();
            if (line.startsWith("-")) {
                List<String> strs = SPACE_SPLITTER.splitToList(line);
                int lockIdIndex = findLockIdIndex(strs);
                if (lockIdIndex < 0) {
                    continue;
                }

                for (int i = 0; i < lockIdIndex; ++i) {
                    String str = strs.get(i);
                    if (str.contains("lock")) {
                        lock.add(getLockId(strs.get(lockIdIndex)));
                        break;
                    } else if (str.contains("wait")) {
                        lock.remove(strs.get(lockIdIndex));
                        break;
                    }
                }
            }
        }
        return ImmutableList.copyOf(lock);
    }

    private String getLockId(String input) {
        return input.substring(1, input.length() - 1);
    }

    /**
     * at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:422)
     * 	at sun.nio.ch.ServerSocketChannelImpl.accept(ServerSocketChannelImpl.java:250)
     * 	- locked <0x0000000771015d38> (a java.lang.Object)
     * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:546)
     * 	at org.apache.tomcat.util.net.NioEndpoint.serverSocketAccept(NioEndpoint.java:79)
     *
     * @param strs
     * @return
     */
    private int findLockIdIndex(List<String> strs) {
        for (int i = 0; i < strs.size(); ++i) {
            String str = strs.get(i);
            if (str.startsWith("<") && str.endsWith(">")) {
                return i;
            }
        }
        return -1;
    }

    private String parseThreadState(String line) {
        int indexSymbol = line.indexOf(THREAD_STATE_PREFIX);
        if (indexSymbol < 0) {
            logger.error("illegal thread first line:\n{}", line);
            return "";
        }

        int indexOfStateStart = indexSymbol + THREAD_STATE_PREFIX.length();
        int indexOfStateEnd = line.indexOf(' ', indexOfStateStart);
        if (indexOfStateEnd < 0) {
            indexOfStateEnd = line.length();
        }
        return line.substring(indexOfStateStart, indexOfStateEnd);
    }

    private ThreadInfo parseThreadFirstLine(String line) {
        int indexAfterName = line.indexOf('"', THREAD_NAME_START_INDEX);
        if (indexAfterName < 0) {
            throw new IllegalArgumentException("illegal thread first line:\n" + line);
        }
        String name = line.substring(THREAD_NAME_START_INDEX, indexAfterName);
        int indexOfThreadIdPrefix = line.indexOf(THREAD_ID_PREFIX, indexAfterName);
        if (indexOfThreadIdPrefix < 0) {
            throw new IllegalArgumentException("illegal thread first line:\n" + line);
        }
        int endOfThreadId = line.indexOf(' ', indexOfThreadIdPrefix + THREAD_ID_PREFIX.length());
        if (endOfThreadId < 0) {
            endOfThreadId = line.length();
        }
        String threadId = line.substring(indexOfThreadIdPrefix + THREAD_ID_PREFIX.length(), endOfThreadId);

        if (Strings.isNullOrEmpty(name)) {
            name = NO_THREAD_NAME + threadId;
        }

        Preconditions.checkArgument(!Strings.isNullOrEmpty(name) && !Strings.isNullOrEmpty(threadId), "illegal thread first line:\n%s", line);
        ThreadInfo threadInfo = new ThreadInfo();
        threadInfo.setId(threadId);
        threadInfo.setName(name);
        threadInfo.setState("");
        threadInfo.setStack("");
        threadInfo.setLockOn(ImmutableList.<String>of());
        return threadInfo;
    }

    private int findFirstLine(List<String> lines, AtomicInteger index) {
        while (index.get() < lines.size()) {
            String line = lines.get(index.get());
            if (line.startsWith("\"") && line.contains("nid=")) {
                return index.get();
            }
            index.incrementAndGet();
        }
        return -1;
    }
}
