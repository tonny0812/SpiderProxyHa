package com.virjar.spider.proxy.ha.core;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.utils.IPUtils;
import io.netty.handler.codec.http.HttpResponseStatus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.asynchttpclient.*;
import org.asynchttpclient.proxy.ProxyServer;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static org.asynchttpclient.Dsl.asyncHttpClient;

@Slf4j
public class UpstreamProducer {
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

    public void refresh() {
        httpclient.prepareGet(source.getSourceUrl()).execute().toCompletableFuture()
                .whenCompleteAsync((response, throwable) -> {
                    if (throwable != null) {
                        log.error("download resource failed:{}", source.getSourceUrl(), throwable);
                        return;
                    }
                    try {
                        handleResourceResponse(response);
                    } catch (Exception e) {
                        log.error("error", e);
                    }
                });
    }

    private void handleResourceResponse(Response response) {
        if (response.getStatusCode() != HttpResponseStatus.OK.code()) {
            log.error("error resource response url:{} response:{}", source.getSourceUrl(), response.getResponseBody(StandardCharsets.UTF_8));
            return;
        }
        String responseBody = response.getResponseBody(StandardCharsets.UTF_8);
        log.info("resource down response:{}", responseBody);
        //ip:port\nip:port\nip:port...
        for (String line : responseBody.split("\n")) {
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
        }
    }

    private void testConnectForUpstream(IpAndPort ipAndPort) {
        log.info("begin test for :{}", ipAndPort);


        // 对于任意代理资源，发送代理请求，使他访问我们到代理接口，拿到真实ip，另外探测出真实的ip出口
        BoundRequestBuilder getBuilder = httpclient.prepareGet(Configs.proxyHttpTestURL);
        ProxyServer.Builder proxyBuilder = new ProxyServer.Builder(ipAndPort.getIp(), ipAndPort.getPort());
        if (StringUtils.isNotBlank(source.getUpstreamAuthUser())) {
            proxyBuilder.setRealm(
                    new Realm.Builder(source.getUpstreamAuthUser(), source.getUpstreamAuthPassword())
                            .setScheme(Realm.AuthScheme.BASIC));
        }
        getBuilder.setProxyServer(proxyBuilder);


        getBuilder.execute().toCompletableFuture().whenCompleteAsync((response, throwable) -> {
            if (throwable != null) {
                log.warn("test proxy failed:{}", ipAndPort);
                return;
            }

            try {
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

        source.getLooper().post(() -> source.handleUpstreamResource(ipAndPort));
    }

    public void reTestUpstream(Upstream upstream) {
        testedIpResource.invalidate(upstream.resourceKey());
        testConnectForUpstream(upstream.getIpAndPort());
    }

}
