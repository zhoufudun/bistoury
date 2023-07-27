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

package qunar.tc.bistoury.ui.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestParam;
import qunar.tc.bistoury.common.AsyncHttpClientHolder;
import qunar.tc.bistoury.common.JacksonSerializer;
import qunar.tc.bistoury.serverside.bean.ApiResult;
import qunar.tc.bistoury.serverside.common.ZKClient;
import qunar.tc.bistoury.serverside.common.ZKClientCache;
import qunar.tc.bistoury.serverside.configuration.DynamicConfigLoader;
import qunar.tc.bistoury.serverside.configuration.local.LocalDynamicConfig;
import qunar.tc.bistoury.serverside.store.RegistryStore;
import qunar.tc.bistoury.ui.service.ProxyService;
import qunar.tc.bistoury.ui.util.ProxyInfo;
import qunar.tc.bistoury.ui.util.ProxyInfoParser;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.List;
import java.util.Optional;

@Service
public class ProxyServiceImpl implements ProxyService {

    private static final Logger logger = LoggerFactory.getLogger(ProxyServiceImpl.class);

    private static final String SCHEMA = "ws://";
    private static final String PATH = "/ws";
    private static final String COLON = ":";

    private static final AsyncHttpClient httpClient = AsyncHttpClientHolder.getInstance();

    private static final TypeReference<ApiResult<AgentInfo>> AGENT_TYPE_REFERENCE = new TypeReference<ApiResult<AgentInfo>>() {
    };

    private String proxyAgent;

    @Resource
    private RegistryStore registryStore;

    private ZKClient zkClient;

    @PostConstruct
    public void init() {
        zkClient = ZKClientCache.get(registryStore.getZkAddress());
        DynamicConfigLoader.<LocalDynamicConfig>load("config.properties").addListener(conf -> proxyAgent = conf.getString("agent.proxy"));
    }

    /**
     * 从zk中获取proxy列表信息
     *
     * @return
     */
    @Override
    public List<String> getAllProxyUrlsFromRegister() {
        try {
            String proxyZkPathForNewUi = registryStore.getProxyZkPathForNewUi();
            logger.info("getAllProxyUrls from local="+proxyZkPathForNewUi);
            List<String> children = zkClient.getChildren(proxyZkPathForNewUi);
            logger.info("get children from zk="+children);
            return children;
        } catch (Exception e) {
            logger.error("get all proxy server address error", e);
            return ImmutableList.of();
        }
    }


    @Override
    public List<String> getWebSocketUrl(final String agentIp) {
        List<String> result = Lists.newArrayList();
        doGetWebSocketUrl(result, getAllProxyUrlsFromRegister(), agentIp);
        return result;
    }

    @Override
    public Optional<ProxyInfo> getNewProxyInfo(String agentIp) {
        for (String proxyWebSocketUrl : getAllProxyUrlsFromRegister()) {
            final Optional<ProxyInfo> proxyInfoRef = ProxyInfoParser.parseProxyInfo(proxyWebSocketUrl);

            if (proxyInfoRef.isPresent()) {
                String url = buildProxyAgentUrl(proxyInfoRef.get());
                if (ifExistAgentOnSpecifiedProxy(url, agentIp)) {
                    return proxyInfoRef;
                }
            }
        }
        return Optional.empty();
    }

    /**
     *
     * @param result
     * @param proxyWebSocketUrls 注册中心保存的proxy集群列表
     * @param agentIp
     */
    private void doGetWebSocketUrl(List<String> result, List<String> proxyWebSocketUrls, final String agentIp) {
        for (String proxyWebSocketUrl : proxyWebSocketUrls) {
            /**
             * 解析注册中心（zk）的数据
             */
            Optional<ProxyInfo> optional = ProxyInfoParser.parseProxyInfo(proxyWebSocketUrl);
            if (!optional.isPresent()) {
                continue;
            }
            logger.info("proxyWebSocketUrl="+proxyWebSocketUrl+", optional="+optional.get().toString());
            ProxyInfo proxyInfo = optional.get();
            String url = buildProxyAgentUrl(proxyInfo);
            // 循环遍历每一个proxy，查询执行的agent是否在某一个proxy下
            if (ifExistAgentOnSpecifiedProxy(url, agentIp)) {
                result.add(buildWebsocketUrl(proxyInfo));
            }
        }
        logger.info("getProxyWebSocketUrl success, proxy service provider ws protocol for ui ws url="+result);
    }

    /**
     * 构建ws协议的url，ui的指令通过ws协议发给agent所注册的proxy，proxy在将指令下发给agent
     * 举例：ws://10.2.40.18:9881/ws
     * @param proxyInfo
     * @return
     */
    private String buildWebsocketUrl(ProxyInfo proxyInfo) {
        return SCHEMA + proxyInfo.getIp() + COLON + proxyInfo.getWebsocketPort() + PATH;
    }

    private String buildProxyAgentUrl(ProxyInfo proxyInfo) {
        return String.format(proxyAgent, proxyInfo.getIp(), proxyInfo.getTomcatPort());
    }

    /**
     * 判断指定的agentIp是否挂在指定的proxy节点下
     *
     * @param url
     * @param agentIp
     * @return
     */
    private boolean ifExistAgentOnSpecifiedProxy(String url, @RequestParam String agentIp) {
        try {
            AsyncHttpClient.BoundRequestBuilder builder = httpClient.prepareGet(url);
            builder.addQueryParam("ip", agentIp);
            logger.info("ui query agent from proxy, proxy url="+url+"?ip="+agentIp);
            Response response = httpClient.executeRequest(builder.build()).get();
            if (response.getStatusCode() == 200) {
                ApiResult<AgentInfo> result = JacksonSerializer.deSerialize(response.getResponseBody("utf8"), AGENT_TYPE_REFERENCE);
                if (result.getStatus() == 0) {
                    return true;
                }
            }
        } catch (Exception e) {
            logger.error("query exist agent error, agent ip [{}], url [{}]", agentIp, url, e);
        }
        return false;
    }


    private static class AgentInfo {
        private String ip;

        public String getIp() {
            return ip;
        }

        public void setIp(String ip) {
            this.ip = ip;
        }
    }
}
