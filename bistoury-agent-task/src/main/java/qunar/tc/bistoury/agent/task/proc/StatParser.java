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

package qunar.tc.bistoury.agent.task.proc;


import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.io.Files;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.List;

/**
 * @author cai.wen
 * @date 19-1-17
 */
class StatParser {

    private static final Logger LOGGER = LoggerFactory.getLogger(StatParser.class);

    private static final Splitter SPACE_SPLITTER = Splitter.on(" ").omitEmptyStrings();
    private static final StatParser INSTANCE = new StatParser();
    private static final String PROC_PATH = "/proc";

    private StatParser() {
    }

    public CpuState parseCpuInfo() throws IOException {
        File procDir = new File(PROC_PATH);
        if (!procDir.exists()) {
            throw new IllegalStateException("can't get proc directory");
        }
        /**
         * 那么在linux下会有/proc/目录，这个目录下结构如下：
         *
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 741
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 77
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 779
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 78
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 8
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 9
         * dr-xr-xr-x.  9 root    root                  0 7月  25 20:39 904
         * dr-xr-xr-x.  2 root    root                  0 7月  25 20:52 acpi
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 buddyinfo
         * dr-xr-xr-x.  4 root    root                  0 7月  25 20:52 bus
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 cgroups
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 cmdline
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 consoles
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 cpuinfo
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 crypto
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 devices
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 diskstats
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 dma
         * dr-xr-xr-x.  2 root    root                  0 7月  25 20:52 driver
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 execdomains
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 fb
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 filesystems
         * dr-xr-xr-x.  4 root    root                  0 7月  25 20:52 fs
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 interrupts
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 iomem
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 ioports
         * dr-xr-xr-x. 56 root    root                  0 7月  25 20:52 irq
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 kallsyms
         * -r--------.  1 root    root    140737486266368 7月  25 20:52 kcore
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 keys
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 key-users
         * -r--------.  1 root    root                  0 7月  25 20:52 kmsg
         * -r--------.  1 root    root                  0 7月  25 20:52 kpagecount
         * -r--------.  1 root    root                  0 7月  25 20:52 kpageflags
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 loadavg
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 locks
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 mdstat
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 meminfo
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 misc
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 modules
         * lrwxrwxrwx.  1 root    root                 11 7月  25 20:52 mounts -> self/mounts
         * dr-xr-xr-x.  3 root    root                  0 7月  25 20:52 mpt
         * -rw-r--r--.  1 root    root                  0 7月  25 20:52 mtrr
         * lrwxrwxrwx.  1 root    root                  8 7月  25 20:52 net -> self/net
         * -r--------.  1 root    root                  0 7月  25 20:52 pagetypeinfo
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 partitions
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 sched_debug
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 schedstat
         * dr-xr-xr-x.  4 root    root                  0 7月  25 20:52 scsi
         * lrwxrwxrwx.  1 root    root                  0 7月  25 20:39 self -> 2389
         * -r--------.  1 root    root                  0 7月  25 20:52 slabinfo
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 softirqs
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 stat
         * -r--r--r--.  1 root    root                  0 7月  25 20:39 swaps
         * dr-xr-xr-x.  1 root    root                  0 7月  25 20:39 sys
         * --w-------.  1 root    root                  0 7月  25 20:52 sysrq-trigger
         * dr-xr-xr-x.  2 root    root                  0 7月  25 20:52 sysvipc
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 timer_list
         * -rw-r--r--.  1 root    root                  0 7月  25 20:52 timer_stats
         * dr-xr-xr-x.  4 root    root                  0 7月  25 20:52 tty
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 uptime
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 version
         * -r--------.  1 root    root                  0 7月  25 20:52 vmallocinfo
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 vmstat
         * -r--r--r--.  1 root    root                  0 7月  25 20:52 zoneinfo
         *
         *
         * stat文件内容为：
         cpu  1398 0 1771 372726 1898 0 75 0 0 0
         cpu0 330 0 425 93078 580 0 11 0 0 0
         cpu1 387 0 439 93268 353 0 4 0 0 0
         cpu2 299 0 379 93187 555 0 34 0 0 0
         cpu3 381 0 526 93191 409 0 25 0 0 0
         intr 369001 65 89 0 0 0 0 0 0 1 0 0 0 339 0 0 1017 3366 13356 63 1928 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 147 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0
         ctxt 549680
         btime 1690288753
         processes 2536
         procs_running 1
         procs_blocked 0
         softirq 237340 1 113458 198 1995 13487 0 285 57770 0 50146
         *
         *
         */
        List<String> stat = getInfoList(new File(procDir, "stat"));
        LOGGER.info("CpuStat={}",stat);
        return CpuState.parse(stat);
    }

    /**
     * 获取指定进行下指定线程id的运行情况
     * @param pid
     * @param tid
     * @return
     * @throws IOException
     */
    public ThreadState parseThreadInfo(int pid, int tid) throws IOException {
        File pidDir = new File(PROC_PATH, String.valueOf(pid));
        if (!pidDir.exists()) {
            throw new IllegalStateException("can not open pid directory : " + pidDir.getAbsoluteFile());
        }
        File tidDir = new File(pidDir, "task/" + String.valueOf(tid));
        if (!tidDir.exists()) {
            LOGGER.info("can not open tid directory : {}", tidDir.getAbsoluteFile());
            return null;
        }
        /**
         * 线程的信息目录如下:
         * [root@localhost 2319]# pwd
         * /proc/2287/task/2319
         * [root@localhost 2319]# ll
         * 总用量 0
         * dr-xr-xr-x. 2 root root 0 7月  26 09:44 attr
         * -r--------. 1 root root 0 7月  26 09:44 auxv
         * -r--r--r--. 1 root root 0 7月  26 09:44 cgroup
         * -r--r--r--. 1 root root 0 7月  26 09:44 children
         * --w-------. 1 root root 0 7月  26 09:44 clear_refs
         * -r--r--r--. 1 root root 0 7月  26 09:44 cmdline
         * -rw-r--r--. 1 root root 0 7月  26 09:44 comm
         * -r--r--r--. 1 root root 0 7月  26 09:44 cpuset
         * lrwxrwxrwx. 1 root root 0 7月  26 09:44 cwd -> /root/export/web-test
         * -r--------. 1 root root 0 7月  26 09:44 environ
         * lrwxrwxrwx. 1 root root 0 7月  26 09:44 exe -> /root/jdk1.8/jdk1.8.0_171/bin/java
         * dr-x------. 2 root root 0 7月  26 09:44 fd
         * dr-x------. 2 root root 0 7月  26 09:44 fdinfo
         * -rw-r--r--. 1 root root 0 7月  26 09:44 gid_map
         * -r--------. 1 root root 0 7月  26 09:44 io
         * -r--r--r--. 1 root root 0 7月  26 09:44 limits
         * -rw-r--r--. 1 root root 0 7月  26 09:44 loginuid
         * -r--r--r--. 1 root root 0 7月  26 09:44 maps
         * -rw-------. 1 root root 0 7月  26 09:44 mem
         * -r--r--r--. 1 root root 0 7月  26 09:44 mountinfo
         * -r--r--r--. 1 root root 0 7月  26 09:44 mounts
         * dr-xr-xr-x. 5 root root 0 7月  26 09:44 net
         * dr-x--x--x. 2 root root 0 7月  26 09:44 ns
         * -r--r--r--. 1 root root 0 7月  26 09:44 numa_maps
         * -rw-r--r--. 1 root root 0 7月  26 09:44 oom_adj
         * -r--r--r--. 1 root root 0 7月  26 09:44 oom_score
         * -rw-r--r--. 1 root root 0 7月  26 09:44 oom_score_adj
         * -r--r--r--. 1 root root 0 7月  26 09:44 pagemap
         * -r--------. 1 root root 0 7月  26 09:44 patch_state
         * -r--r--r--. 1 root root 0 7月  26 09:44 personality
         * -rw-r--r--. 1 root root 0 7月  26 09:44 projid_map
         * lrwxrwxrwx. 1 root root 0 7月  26 09:44 root -> /
         * -rw-r--r--. 1 root root 0 7月  26 09:44 sched
         * -r--r--r--. 1 root root 0 7月  26 09:44 schedstat
         * -r--r--r--. 1 root root 0 7月  26 09:44 sessionid
         * -rw-r--r--. 1 root root 0 7月  26 09:44 setgroups
         * -r--r--r--. 1 root root 0 7月  26 09:44 smaps
         * -r--r--r--. 1 root root 0 7月  26 09:44 stack
         * -r--r--r--. 1 root root 0 7月  25 20:47 stat
         * -r--r--r--. 1 root root 0 7月  26 09:44 statm
         * -r--r--r--. 1 root root 0 7月  26 09:44 status
         * -r--r--r--. 1 root root 0 7月  26 09:44 syscall
         * -rw-r--r--. 1 root root 0 7月  26 09:44 uid_map
         * -r--r--r--. 1 root root 0 7月  26 09:44 wchan
         *
         *
         * /proc/2287/task/2319/stat文件为该线程的执行情况
         *
         * 2319 (java) S 2286 2285 2257 34816 6457 4202560 24 0 0 0 0 0 0 0 20 0 31 0 13328 4081807360 68888 18446744073709551615 4194304 4196468 140737298375456 140189265249728 140190990461405 0 4 3 16800972 18446744072598951120 0 0 -1 0 0 0 0 0 0 6293624 6294260 6459392 140737298384300 140737298384694 140737298384694 140737298386901 0
         *
         */
        return ThreadState.parse(getInfoList(new File(tidDir, "stat")));
    }

    public ProcessState parseProcessInfo(int pid) throws IOException {
        File pidDir = new File(PROC_PATH, String.valueOf(pid));
        /**
         * 假设pid=2297
         * pidDir目录内容:
         * dr-xr-xr-x.  2 root root 0 7月  25 20:41 attr
         * -rw-r--r--.  1 root root 0 7月  25 20:41 autogroup
         * -r--------.  1 root root 0 7月  25 20:41 auxv
         * -r--r--r--.  1 root root 0 7月  25 20:41 cgroup
         * --w-------.  1 root root 0 7月  25 20:41 clear_refs
         * -r--r--r--.  1 root root 0 7月  25 20:41 cmdline
         * -rw-r--r--.  1 root root 0 7月  25 20:41 comm
         * -rw-r--r--.  1 root root 0 7月  25 20:41 coredump_filter
         * -r--r--r--.  1 root root 0 7月  25 20:41 cpuset
         * lrwxrwxrwx.  1 root root 0 7月  25 20:41 cwd -> /root/export/web-test
         * -r--------.  1 root root 0 7月  25 20:41 environ
         * lrwxrwxrwx.  1 root root 0 7月  25 20:41 exe -> /root/jdk1.8/jdk1.8.0_171/bin/java
         * dr-x------.  2 root root 0 7月  25 20:41 fd
         * dr-x------.  2 root root 0 7月  25 20:41 fdinfo
         * -rw-r--r--.  1 root root 0 7月  25 20:41 gid_map
         * -r--------.  1 root root 0 7月  25 20:41 io
         * -r--r--r--.  1 root root 0 7月  25 20:41 limits
         * -rw-r--r--.  1 root root 0 7月  25 20:41 loginuid
         * dr-x------.  2 root root 0 7月  25 20:41 map_files
         * -r--r--r--.  1 root root 0 7月  25 20:41 maps
         * -rw-------.  1 root root 0 7月  25 20:41 mem
         * -r--r--r--.  1 root root 0 7月  25 20:41 mountinfo
         * -r--r--r--.  1 root root 0 7月  25 20:41 mounts
         * -r--------.  1 root root 0 7月  25 20:41 mountstats
         * dr-xr-xr-x.  5 root root 0 7月  25 20:41 net
         * dr-x--x--x.  2 root root 0 7月  25 20:41 ns
         * -r--r--r--.  1 root root 0 7月  25 20:41 numa_maps
         * -rw-r--r--.  1 root root 0 7月  25 20:41 oom_adj
         * -r--r--r--.  1 root root 0 7月  25 20:41 oom_score
         * -rw-r--r--.  1 root root 0 7月  25 20:41 oom_score_adj
         * -r--r--r--.  1 root root 0 7月  25 20:41 pagemap
         * -r--------.  1 root root 0 7月  25 20:41 patch_state
         * -r--r--r--.  1 root root 0 7月  25 20:41 personality
         * -rw-r--r--.  1 root root 0 7月  25 20:41 projid_map
         * lrwxrwxrwx.  1 root root 0 7月  25 20:41 root -> /
         * -rw-r--r--.  1 root root 0 7月  25 20:41 sched
         * -r--r--r--.  1 root root 0 7月  25 20:41 schedstat
         * -r--r--r--.  1 root root 0 7月  25 20:41 sessionid
         * -rw-r--r--.  1 root root 0 7月  25 20:41 setgroups
         * -r--r--r--.  1 root root 0 7月  25 20:41 smaps
         * -r--r--r--.  1 root root 0 7月  25 20:41 stack
         * -r--r--r--.  1 root root 0 7月  25 20:41 stat
         * -r--r--r--.  1 root root 0 7月  25 20:41 statm
         * -r--r--r--.  1 root root 0 7月  25 20:41 status
         * -r--r--r--.  1 root root 0 7月  25 20:41 syscall
         * dr-xr-xr-x. 33 root root 0 7月  25 20:41 task
         * -r--r--r--.  1 root root 0 7月  25 20:41 timers
         * -rw-r--r--.  1 root root 0 7月  25 20:41 uid_map
         * -r--r--r--.  1 root root 0 7月  25 20:41 wchan
         *
         */
        if (!pidDir.exists()) {
            throw new IllegalStateException("can not open pid directory : " + pidDir.getAbsoluteFile());
        }
        /**
         * stat文件内容为：
         * 2287 (java) S 2286 2285 2257 34816 2411 1077944320 61897 0 20 0 508 276 0 0 20 0 31 0 12879 4081807360 68788 18446744073709551615 4194304 4196468 140737298375456 140737298358000 140190990438423 0 0 3 16800972 18446744073709551615 0 0 17 2 0 0 20 0 0 6293624 6294260 6459392 140737298384300 140737298384694 140737298384694 140737298386901
         */
        return ProcessState.parse(getInfoList(new File(pidDir, "stat")));
    }

    private List<String> getInfoList(File file) throws IOException {
        return SPACE_SPLITTER.splitToList(Files.toString(file, Charsets.UTF_8));
    }

    public static StatParser getInstance() {
        return INSTANCE;
    }
}
