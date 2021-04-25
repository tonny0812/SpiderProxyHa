package com.virjar.spider.proxy.ha;

import com.google.common.collect.Sets;
import com.virjar.spider.proxy.ha.auth.AuthConfig;
import com.virjar.spider.proxy.ha.core.Source;

import java.util.List;
import java.util.Set;

public class Configs {
    public static List<Source> sourceList;
    /**
     * 资源刷新间隔时间
     */
    public static int refreshUpstreamInterval = 30;
    /**
     * 后端探测接口，探测代理ip是否可用以及解析出口ip地址
     */
    public static String proxyHttpTestURL = "https://sekiro.virjar.com/dly/getPublicIp";

    public static int cacheConnPerUpstream = 3;
    public static int cacheConnAliveSeconds = 30;

    public static String listenIp = "0.0.0.0";

    public static AuthConfig authConfig;

    public static void doRefreshResource() {
        for (Source source : sourceList) {
            source.refresh();
        }
    }

    public static Set<String> openPortSet = Sets.newConcurrentHashSet();


}
