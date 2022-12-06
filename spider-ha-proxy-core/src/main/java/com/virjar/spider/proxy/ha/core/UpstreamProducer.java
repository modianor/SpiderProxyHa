package com.virjar.spider.proxy.ha.core;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.Constants;
import com.virjar.spider.proxy.ha.utils.IPUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.*;
import org.asynchttpclient.proxy.ProxyServer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import static org.asynchttpclient.Dsl.asyncHttpClient;

@Slf4j
public class UpstreamProducer {
    private static String candidateProxyHttpTestURL = null;
    private static int retryRelocateProxyHttpTestCount = 0;

    private static final AsyncHttpClient httpclient = asyncHttpClient(
            new DefaultAsyncHttpClientConfig.Builder()
                    .setKeepAlive(true)
                    .setConnectTimeout(10000)
                    .setReadTimeout(10000)
                    .setPooledConnectionIdleTimeout(20000)
                    .build());
    private final Cache<String, String> testedIpResource = CacheBuilder.newBuilder()
            .expireAfterWrite(10, TimeUnit.MINUTES)
            .build();
    private final Source source;

    public UpstreamProducer(Source source) {
        this.source = source;
    }

    /**
     * 检测服务部署服务器的外网ip
     */
    public static void relocateOutIpResolver() {
        // 检查管理端端口
        if (Configs.adminServerPort < 0) {
            return;
        }
        // 对于任意代理资源，发送代理请求，使他访问我们到代理接口，拿到真实ip，另外探测出真实的ip出口
        BoundRequestBuilder getBuilder = httpclient.prepareGet(Configs.proxyHttpTestURL);

        getBuilder.execute().toCompletableFuture().whenCompleteAsync((response, throwable) -> {
            if (throwable != null) {
                log.warn("can not find my out Ip:", throwable);
                return;
            }
            // 拿到当前出口ip
            String myOutIp = response.getResponseBody(StandardCharsets.UTF_8).trim();
            log.info("test response :{} for proxy:{}", myOutIp, myOutIp);
            if (!IPUtils.isIpV4(myOutIp)) {
                log.warn("response not ip format:{}", myOutIp);
                return;
            }
            if (IPUtils.isPrivate(myOutIp)) {
                return;
            }

            String newProxyTestUrl =
                    "http://" + myOutIp + ":" + Configs.adminServerPort + "/" + Constants.ADMIN_API_PATH.RESOLVE_IP;
            log.info("new proxy test url:{}", newProxyTestUrl);
            candidateProxyHttpTestURL = newProxyTestUrl;

        });
    }

    /**
     * 从代理商定时拉取IP资源
     */
    public void refresh() {
        httpclient.prepareGet(source.getSourceUrl()).execute().toCompletableFuture()
                .whenCompleteAsync((response, throwable) -> {
                    if (throwable != null) {
                        log.error("download resource failed:{}", source.getSourceUrl(), throwable);
                        return;
                    }
                    try {
                        // 检测并映射端口
                        handleResourceResponse(response);
                    } catch (Exception e) {
                        log.error("error", e);
                    }
                });
    }

    /**
     * 解析拉取ip资源请求
     * @param response
     */
    private void handleResourceResponse(Response response) {
        if (response.getStatusCode() != HttpResponseStatus.OK.code()) {
            log.error("error resource response url:{} response:{}", source.getSourceUrl(), response.getResponseBody(StandardCharsets.UTF_8));
            return;
        }
        String responseBody = response.getResponseBody(StandardCharsets.UTF_8);
        JSONObject data = JSON.parseObject(responseBody);
        JSONArray items = data.getJSONObject("data").getJSONArray("data");
        log.info("resource down response:{}", items);
        for (int i = 0; i < items.size(); i++) {
            JSONObject item = items.getJSONObject(i);
            String ip = item.getString("ip");
            int port = item.getIntValue("port");
            IpAndPort ipAndPort = new IpAndPort(ip, port);
            if (!ipAndPort.isIllegal()) {
                log.warn("broken proxy response format: {}", item);
                continue;
            }
            // 已经被同步过的代理，不需要再次链接
            if (testedIpResource.getIfPresent(ipAndPort.getIpPort()) != null) {
                continue;
            }
            testedIpResource.put(ipAndPort.getIpPort(), ipAndPort.getIpPort());
            // 尝试连接upstream
            testConnectForUpstream(ipAndPort);
        }

        //ip:port\nip:port\nip:port...
        /*for (String line : responseBody.split("\n")) {
            line = line.trim();
            IpAndPort ipAndPort = new IpAndPort(line);
            if (!ipAndPort.isIllegal()) {
                log.warn("broken proxy response format: {}", line);
                continue;
            }
            // 已经被同步过的代理，不需要再次链接
            if (testedIpResource.getIfPresent(ipAndPort.getIpPort()) != null) {
                continue;
            }
            testedIpResource.put(ipAndPort.getIpPort(), ipAndPort.getIpPort());

            testConnectForUpstream(ipAndPort);
        }*/
    }

    private void testConnectForUpstream(IpAndPort ipAndPort) {
        log.info("begin test for :{} with url:{}", ipAndPort, Configs.proxyHttpTestURL);


        // 对于任意代理资源，发送代理请求，使他访问我们到代理接口，拿到真实ip，另外探测出真实的ip出口
        BoundRequestBuilder getBuilder = httpclient.prepareGet(Configs.proxyHttpTestURL);
        ProxyServer.Builder proxyBuilder = new ProxyServer.Builder(ipAndPort.getIp(), ipAndPort.getPort());
        if (StringUtils.isNotBlank(source.getUpstreamAuthUser())) {
            proxyBuilder.setRealm(
                    new Realm.Builder(source.getUpstreamAuthUser(), source.getUpstreamAuthPassword())
                            .setScheme(Realm.AuthScheme.BASIC));
        }
        // 设置代理ip
        getBuilder.setProxyServer(proxyBuilder);

        getBuilder.execute().toCompletableFuture().whenCompleteAsync((response, throwable) -> {
            if (throwable != null) {
                log.warn("test proxy failed:{}", ipAndPort);
                return;
            } else {
                log.info("test proxy success:{}", ipAndPort);
            }

            try {
                // 记录代理ip的出口ip
                onProxyResourceTestSuccess(ipAndPort, response);
            } catch (Exception e) {
                log.error("error", e);
            }
        });
    }

    private void onProxyResourceTestSuccess(IpAndPort ipAndPort, Response proxyResourceTestResponse) {
        String responseBody = proxyResourceTestResponse.getResponseBody(StandardCharsets.UTF_8).trim();
        log.info("test response :{} for proxy:{}", responseBody, ipAndPort);
        if (!IPUtils.isIpV4(responseBody)) {
            log.warn("response not ip format:{}", responseBody);
            return;
        }
        ipAndPort.setOutIp(responseBody);
//        if (outIpResource.getIfPresent(responseBody) != null) {
//            // 这个出口ip被映射过
//            return;
//        }

        doRelocateTestUrlIfNeed(ipAndPort);
        source.getLooper().post(() -> source.handleUpstreamResource(ipAndPort));
    }

    public void reTestUpstream(Upstream upstream) {
        testedIpResource.invalidate(upstream.resourceKey());
        testConnectForUpstream(upstream.getIpAndPort());
    }

    private void doRelocateTestUrlIfNeed(IpAndPort ipAndPort) {
        if (candidateProxyHttpTestURL == null) {
            return;
        }
        if (Configs.proxyHttpTestURL.equals(candidateProxyHttpTestURL)) {
            return;
        }
        if (retryRelocateProxyHttpTestCount > 5) {
            return;
        }
        retryRelocateProxyHttpTestCount++;

        BoundRequestBuilder getBuilder = httpclient.prepareGet(candidateProxyHttpTestURL);
        ProxyServer.Builder proxyBuilder = new ProxyServer.Builder(ipAndPort.getIp(), ipAndPort.getPort());
        if (StringUtils.isNotBlank(source.getUpstreamAuthUser())) {
            proxyBuilder.setRealm(
                    new Realm.Builder(source.getUpstreamAuthUser(), source.getUpstreamAuthPassword())
                            .setScheme(Realm.AuthScheme.BASIC));
        }
        getBuilder.setProxyServer(proxyBuilder);

        getBuilder.execute().toCompletableFuture().whenCompleteAsync(new BiConsumer<Response, Throwable>() {
            @Override
            public void accept(Response response, Throwable throwable) {
                if (throwable != null) {
                    log.warn("doRelocateTest  error", throwable);
                    return;
                }
                String responseBody = response.getResponseBody(StandardCharsets.UTF_8).trim();
                log.info("doRelocateTest  response :{} for proxy:{}", responseBody, ipAndPort);
                if (ipAndPort.getIp().equals(responseBody)) {
                    Configs.proxyHttpTestURL = candidateProxyHttpTestURL;
                }
            }
        });
    }

}
