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
import com.google.common.collect.Maps;
import com.google.common.io.Files;
import com.google.common.io.LineProcessor;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * @author cai.wen
 * @date 19-1-17
 */
public class ProcUtil {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProcUtil.class);

    private static final int HZ = 100;
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("mm:ss.SS");

    private static final int CPU_NUM;
    private static final String cpuInfoFilePath = "/proc/cpuinfo";

    static {
        int cpuNum = 0;
        try {
            /**
             * 获取cpu核心数
             *
             * /proc/cpuinfo如下：
             *
             * processor       : 0
             * vendor_id       : GenuineIntel
             * cpu family      : 6
             * model           : 165
             * model name      : Intel(R) Core(TM) i7-10700 CPU @ 2.90GHz
             * stepping        : 5
             * microcode       : 0xc8
             * cpu MHz         : 2903.997
             * cache size      : 16384 KB
             * physical id     : 0
             * siblings        : 2
             * core id         : 0
             * cpu cores       : 2
             * apicid          : 0
             * initial apicid  : 0
             * fpu             : yes
             * fpu_exception   : yes
             * cpuid level     : 22
             * wp              : yes
             * flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ss ht syscall nx pdpe1gb rdtscp lm constant_tsc arch_perfmon nopl xtopology tsc_reliable nonstop_tsc eagerfpu pni pclmulqdq ssse3 fma cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt tsc_deadline_timer aes xsave avx f16c rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ssbd rsb_ctxsw ibrs ibpb stibp ibrs_enhanced fsgsbase tsc_adjust bmi1 avx2 smep bmi2 invpcid rdseed adx smap clflushopt xsaveopt xsavec xgetbv1 arat pku ospke md_clear spec_ctrl intel_stibp flush_l1d arch_capabilities
             * bogomips        : 5807.99
             * clflush size    : 64
             * cache_alignment : 64
             * address sizes   : 45 bits physical, 48 bits virtual
             * power management:
             *
             * processor       : 1
             * vendor_id       : GenuineIntel
             * cpu family      : 6
             * model           : 165
             * model name      : Intel(R) Core(TM) i7-10700 CPU @ 2.90GHz
             * stepping        : 5
             * microcode       : 0xc8
             * cpu MHz         : 2903.997
             * cache size      : 16384 KB
             * physical id     : 0
             * siblings        : 2
             * core id         : 1
             * cpu cores       : 2
             * apicid          : 1
             * initial apicid  : 1
             * fpu             : yes
             * fpu_exception   : yes
             * cpuid level     : 22
             * wp              : yes
             * flags           : fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ss ht syscall nx pdpe1gb rdtscp lm constant_tsc arch_perfmon nopl xtopology tsc_reliable nonstop_tsc eagerfpu pni pclmulqdq ssse3 fma cx16 pcid sse4_1 sse4_2 x2apic movbe popcnt tsc_deadline_timer aes xsave avx f16c rdrand hypervisor lahf_lm abm 3dnowprefetch invpcid_single ssbd rsb_ctxsw ibrs ibpb stibp ibrs_enhanced fsgsbase tsc_adjust bmi1 avx2 smep bmi2 invpcid rdseed adx smap clflushopt xsaveopt xsavec xgetbv1 arat pku ospke md_clear spec_ctrl intel_stibp flush_l1d arch_capabilities
             * bogomips        : 5807.99
             * clflush size    : 64
             * cache_alignment : 64
             * address sizes   : 45 bits physical, 48 bits virtual
             * power management:
             *
             *
             */

            // 一行一行读取：
            cpuNum = Files.readLines(new File(cpuInfoFilePath), Charsets.UTF_8, new LineProcessor<Integer>() {
                private int cpuNum = 0;

                @Override
                public boolean processLine(String s) throws IOException {
                    if (s.startsWith("processor")) {
                        cpuNum++;
                    }
                    return true;
                }

                @Override
                public Integer getResult() {
                    return cpuNum;
                }
            });
        } catch (IOException e) {
            LOGGER.error("can not get the number of CPUs/cores ");
        } finally {
            CPU_NUM = cpuNum;
        }
    }

    public static String formatJiffies(long jiffies) {
        long milliSeconds = jiffies * 1000 / HZ;
        return TimeUnit.MILLISECONDS.toHours(milliSeconds) + ":"
                + DATE_TIME_FORMATTER.print(milliSeconds);
    }

    public static <T> Map<String, T> transformHexThreadId(Map<Integer, T> value) {
        if (value == null || value.isEmpty()) {
            return new HashMap<>(0);
        }
        Map<String, T> result = Maps.newHashMapWithExpectedSize(value.size());
        for (Map.Entry<Integer, T> entry : value.entrySet()) {
            String threadId = "0x" + Integer.toHexString(entry.getKey());
            result.put(threadId, entry.getValue());
        }
        return result;
    }

    public static int getCpuNum() {
        return CPU_NUM;
    }
}
