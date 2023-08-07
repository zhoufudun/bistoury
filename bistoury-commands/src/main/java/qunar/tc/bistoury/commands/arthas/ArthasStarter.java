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

package qunar.tc.bistoury.commands.arthas;

import com.google.common.base.Strings;
import com.sun.tools.attach.VirtualMachine;
import com.sun.tools.attach.VirtualMachineDescriptor;
import com.taobao.arthas.core.config.Configure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FilenameFilter;
import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * @author zhenyu.nie created on 2018 2018/11/19 19:12
 */
public class ArthasStarter {

    private static final Logger logger = LoggerFactory.getLogger(ArthasStarter.class);

    private static final String DEFAULT_AGENT_JAR_PATH;
    private static final String DEFAULT_CORE_JAR_PATH;

    static {
        String libDirPath = System.getProperty("bistoury.lib.dir");
        File libDir;
        if (Strings.isNullOrEmpty(libDirPath)) {
//            Configure类所在的包路径
            libDir = new File(Configure.class.getProtectionDomain().getCodeSource().getLocation().getPath()).getParentFile();
        } else {
            libDir = new File(libDirPath);
        }
        logger.info("ArthasStarter static code, libDir={}",libDir);
        DEFAULT_AGENT_JAR_PATH = new File(libDir, "bistoury-instrument-agent.jar").getPath();
        DEFAULT_CORE_JAR_PATH = new File(libDir, "arthas-core.jar").getPath();
        logger.info("ArthasStarter static code, DEFAULT_AGENT_JAR_PATH={}",DEFAULT_AGENT_JAR_PATH);
        logger.info("ArthasStarter static code, DEFAULT_CORE_JAR_PATH={}",DEFAULT_CORE_JAR_PATH);
    }

    public synchronized static void start(int pid) throws Exception {
        Configure configure = getConfigure(pid);
        attachAgent(configure);
    }

    private static void attachAgent(Configure configure) throws Exception {
        logger.info("start attach to arthas agent");
        VirtualMachineDescriptor virtualMachineDescriptor = null;
        for (VirtualMachineDescriptor descriptor : VirtualMachine.list()) {
            String pid = descriptor.id();
            if (pid.equals(String.valueOf(configure.getJavaPid()))) {
                virtualMachineDescriptor = descriptor;
            }
        }
        VirtualMachine virtualMachine = null;
        try {
            if (virtualMachineDescriptor == null) {
                virtualMachine = VirtualMachine.attach(String.valueOf(configure.getJavaPid()));
            } else {
                virtualMachine = VirtualMachine.attach(virtualMachineDescriptor);
            }

            Properties targetSystemProperties = virtualMachine.getSystemProperties();
            String targetJavaVersion = targetSystemProperties.getProperty("java.specification.version");
            String currentJavaVersion = System.getProperty("java.specification.version");
            if (targetJavaVersion != null && currentJavaVersion != null) {
                if (!targetJavaVersion.equals(currentJavaVersion)) {
                    logger.warn("Current VM java version: {} do not match target VM java version: {}, attach may fail.",
                            currentJavaVersion, targetJavaVersion);
                    logger.warn("Target VM JAVA_HOME is {}, try to set the same JAVA_HOME.",
                            targetSystemProperties.getProperty("java.home"));
                }
            }

            String arthasAgent = configure.getArthasAgent(); // D:\maven\repository\com\taobao\arthas\arthas-core\3.1.4\bistoury-instrument-agent.jar
            File agentFile = new File(arthasAgent); // D:\maven\repository\com\taobao\arthas\arthas-core\3.1.4\bistoury-instrument-agent.jar
            String name = agentFile.getName(); // bistoury-instrument-agent.jar
            String prefix = name.substring(0, name.indexOf('.'));//bistoury-instrument-agent
            File dir = agentFile.getParentFile(); // D:\maven\repository\com\taobao\arthas\arthas-core\3.1.4
            logger.info("ArthasStarter.attachAgent dir={} prefix={}",dir.getAbsolutePath(),prefix);
            File realAgentFile = getFileWithPrefix(dir, prefix);

            logger.info("start load arthas agent, arthasAgent {}, realAgentFile {}", arthasAgent, realAgentFile.getCanonicalPath());
            final String delimiter = "$|$";
            /**
             * System.getProperty("bistoury.app.lib.class") 是为了获取用户进程（需要诊断问题的java用户进程）所有的jar包所在路路径
             */
            String args = configure.getArthasCore() + delimiter + ";;" + configure.toString() + delimiter + System.getProperty("bistoury.app.lib.class");
            logger.info("begin loadAgent, realAgentFile={}, second args {}", realAgentFile.getCanonicalPath(), args);
            virtualMachine.loadAgent(realAgentFile.getCanonicalPath(),args);
//            virtualMachine.loadAgent("D:\\code\\study-demo\\target\\agent-shade.jar");
            // D:\code\arthas-zfd-3.6.9\packaging\target\arthas-3.1.4-bin\arthas-agent.jar
        } finally {
            if (virtualMachine != null) {
                virtualMachine.detach();
            }
        }
    }

    private static File getFileWithPrefix(File dir, final String prefix) {
        File[] files = dir.listFiles(new FilenameFilter() {
            @Override
            public boolean accept(File dir, String name) {
                return name.startsWith(prefix);
            }
        });
        if (files == null || files.length != 1) {
            throw new IllegalStateException("get files, " + Arrays.toString(files));
        }
        return files[0];
    }

    private static Configure getConfigure(int pid) {
        String agentJar = System.getProperty("bistoury.agent.jar.path", DEFAULT_AGENT_JAR_PATH);
        String coreJar = System.getProperty("bistoury.arthas.core.jar.path", DEFAULT_CORE_JAR_PATH);

        String property = System.getProperty("os.name");
        if(property.startsWith("Window")){
            // window平台下调试，需要指定用户进程的lib位置, 参数内容根据自己的用户进程自己设置
//            org.springframework.web.servlet.DispatcherServlet
//            System.setProperty("bistoury.app.lib.class", "org.springframework.web.servlet.DispatcherServlet");
            System.setProperty("bistoury.app.lib.class", "com.example.webdemo.WebdemoApplication");
        }

        Configure configure = new Configure();
        configure.setJavaPid(pid);
        configure.setArthasAgent(agentJar);
        configure.setArthasCore(coreJar);
        configure.setIp(TelnetConstants.TELNET_CONNECTION_IP);
        configure.setTelnetPort(TelnetConstants.TELNET_CONNECTION_PORT);
        configure.setHttpPort(TelnetConstants.DEFAULT_HTTP_PORT);
        logger.info("Configure, pid={}, arthasAgent={}, arthasCore={}. telentIp={}, telentPort={}. httpPort={}",
                configure.getJavaPid(),configure.getArthasAgent(),configure.getArthasCore(),configure.getIp(),configure.getTelnetPort(),configure.getHttpPort());
        logger.info("print all SystemProperties");
        Set<String> strings = System.getProperties().stringPropertyNames();
        strings.stream().forEach(new Consumer<String>() {
            @Override
            public void accept(String s) {
                logger.info("key={}, value={}",s,System.getProperty(s));
            }
        });

        return configure;
    }
}
