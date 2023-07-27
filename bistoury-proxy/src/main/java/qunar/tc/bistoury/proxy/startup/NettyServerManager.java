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

package qunar.tc.bistoury.proxy.startup;

import org.apache.curator.framework.state.ConnectionState;
import org.apache.curator.utils.ZKPaths;
import org.apache.zookeeper.KeeperException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import qunar.tc.bistoury.application.api.AppServerService;
import qunar.tc.bistoury.proxy.communicate.Connection;
import qunar.tc.bistoury.proxy.communicate.SessionManager;
import qunar.tc.bistoury.proxy.communicate.agent.AgentConnection;
import qunar.tc.bistoury.proxy.communicate.agent.AgentConnectionStore;
import qunar.tc.bistoury.proxy.communicate.agent.NettyServerForAgent;
import qunar.tc.bistoury.proxy.communicate.agent.handler.AgentMessageHandler;
import qunar.tc.bistoury.proxy.communicate.agent.handler.AgentMessageProcessor;
import qunar.tc.bistoury.proxy.communicate.ui.NettyServerForUi;
import qunar.tc.bistoury.proxy.communicate.ui.UiConnectionStore;
import qunar.tc.bistoury.proxy.communicate.ui.command.CommunicateCommandStore;
import qunar.tc.bistoury.proxy.generator.IdGenerator;
import qunar.tc.bistoury.serverside.agile.Conf;
import qunar.tc.bistoury.remoting.util.LocalHost;
import qunar.tc.bistoury.serverside.common.ZKClient;
import qunar.tc.bistoury.serverside.common.ZKClientCache;
import qunar.tc.bistoury.serverside.configuration.DynamicConfigLoader;
import qunar.tc.bistoury.serverside.store.RegistryStore;
import qunar.tc.bistoury.serverside.util.ServerManager;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author leix.xie
 * @date 2019-07-18 11:32
 * @describe
 */
@Component
public class NettyServerManager {

    private static final Logger logger = LoggerFactory.getLogger(NettyServerManager.class);

    @Autowired
    private RegistryStore registryStore;

    @Autowired
    private CommunicateCommandStore commandStore;

    @Autowired
    private UiConnectionStore uiConnectionStore;

    @Autowired
    private AgentConnectionStore agentConnectionStore;

    @Autowired
    private SessionManager sessionManager;

    @Autowired
    private AppServerService appServerService;

    @Autowired
    private IdGenerator sessionIdGenerator;

    @Autowired
    private List<AgentMessageProcessor> agentMessageProcessors;

    private volatile String uiNode;
    private ZKClient zkClient;
    private Conf conf;

    int websocketPort = -1;
    int tomcatPort = -1;

    private NettyServerForAgent nettyServerForAgent;

    private NettyServerForUi nettyServerForUi;

    @PostConstruct
    public void start() {
        zkClient = ZKClientCache.get(registryStore.getZkAddress());
        conf = Conf.fromMap(DynamicConfigLoader.load("global.properties").asMap()); // 读取global.properties配置文件

        websocketPort = conf.getInt("server.port", -1); // server.port 最为websocket端口
        tomcatPort = ServerManager.getTomcatPort();

        nettyServerForAgent = startAgentServer(conf); // 接受agent的netty请求
        nettyServerForUi = startUiServer(conf); // 接收ui的ws请求

        online(); // proxy节点信息注册到zk
    }

    @PreDestroy
    public void stop() {
        offline();
        nettyServerForUi.stop();
        nettyServerForAgent.stop();
        zkClient.close();
    }

    private NettyServerForUi startUiServer(Conf conf) {
        NettyServerForUi serverForUi = new NettyServerForUi(
                conf,
                sessionIdGenerator,
                commandStore,
                uiConnectionStore,
                agentConnectionStore,
                sessionManager,
                appServerService);
        serverForUi.start();
        return serverForUi;
    }

    private NettyServerForAgent startAgentServer(Conf conf) {
        AgentMessageHandler handler = new AgentMessageHandler(agentMessageProcessors); // agentMessageProcessors包括：AgentInfoRefreshProcessor、AgentProfilerFileProcessor、AgentResponseProcessor、ProxyHeartBeatProcessor
        NettyServerForAgent serverForAgent = new NettyServerForAgent(conf, handler);
        serverForAgent.start();
        return serverForAgent;
    }

    private void closeAgentConnections() {
        Map<String, AgentConnection> agentConnection = agentConnectionStore.getAgentConnection();
        Collection<AgentConnection> connections = agentConnection.values();
        for (Connection connection : connections) {
            connection.close();
        }
    }

    private boolean deleteSelf() {
        return deleteNode(uiNode);
    }

    private boolean deleteNode(String... nodes) {
        boolean ret = true;
        for (String node : nodes) {
            if (node != null) {
                try {
                    zkClient.deletePath(node);
                    logger.info("zk delete successfully, node {}", node);
                } catch (KeeperException.NoNodeException e) {
                    // ignore
                } catch (Exception e) {
                    logger.error("zk delete path error", e);
                    ret = false;
                }
            }
        }
        return ret;
    }

    private void register() {
        registerProxyNode();
        zkClient.addConnectionChangeListener((sender, state) -> {
            if (state == ConnectionState.RECONNECTED) {
                deleteSelf(); // 本Proxy重启后需要执行删除自己在zk注册的信息，应为可能本proxy的端口变了
                registerProxyNode();// 本Proxy最新的信息注册到zk
            }
        });
    }
    // proxy 节点信息写入注册中心（zookeeper）
    private String doRegister(String basePath, String node) {
        try {
            if (!zkClient.checkExist(basePath)) {
                zkClient.addPersistentNode(basePath);
            }
            node = ZKPaths.makePath(basePath, node); // /bistoury/proxy/new/group/ui/10.2.40.18:9090:9881
            deleteNode(node); // zk中删除node节点
            zkClient.addEphemeralNode(node);// 添加node的临时节点
            logger.info("zk register successfully, node {}", node);
        } catch (Exception e) {
            logger.error("zk register failed", e);
        }
        return node;
    }

    private void registerProxyNode() {
        this.uiNode = doRegister(registryStore.getProxyZkPathForNewUi(), getIp() + ":" + tomcatPort + ":" + websocketPort);
    }

    private static String getIp() {
        return LocalHost.getLocalHost();
    }

    public boolean offline() {
        deleteSelf();
        closeAgentConnections();
        return true;
    }

    public boolean online() {
        deleteSelf(); //
        register();
        return true;
    }
}
