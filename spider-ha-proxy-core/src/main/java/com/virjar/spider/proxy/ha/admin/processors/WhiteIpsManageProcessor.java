package com.virjar.spider.proxy.ha.admin.processors;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Sets;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.auth.AuthConfig;
import com.virjar.spider.proxy.ha.core.Source;
import com.virjar.spider.proxy.ha.utils.HttpNettyUtils;
import com.virjar.spider.proxy.ha.utils.IPUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import org.apache.commons.lang3.StringUtils;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2021-04-28
 *
 * @author alienhe
 */
public class WhiteIpsManageProcessor extends BaseAuthProcessor {

    /**
     * whiteIps 待添加的白名单IP列表
     * source 代理渠道源，与config.ini中配置的source一致
     */
    @Override
    public void process0(Channel channel, JSONObject request, HttpHeaders httpHeaders) {
        String action = request.getString("action");
        if (StringUtils.isEmpty(action)) {
            HttpNettyUtils.responseNeedParam(channel, "action");
            return;
        }
        switch (action) {
        case "list":
            actionListWhiteIps(channel, request);
            break;
        case "add":
            actionAddWhiteIps(channel, request);
            break;
        default:
            HttpNettyUtils.responseJsonFailed(channel, "not support action:" + action);
        }

    }

    private Source getSource(Channel channel, JSONObject request) {
        Integer mappingPort = request.getInteger("mappingPort");
        if (mappingPort == null) {
            HttpNettyUtils.responseNeedParam(channel, "mappingPort");
            return null;
        }
        return Configs.sourceMap.get(mappingPort);
    }

    private void actionListWhiteIps(Channel channel, JSONObject request) {
        Source source = getSource(channel, request);
        HttpNettyUtils.responseJsonSuccess(channel, source);
    }

    private void actionAddWhiteIps(Channel channel, JSONObject request) {
        Source source = getSource(channel, request);
        if (source == null) {
            HttpNettyUtils.responseJsonFailed(channel, "none mapping port");
            return;
        }
        String whiteIpStr = request.getString("whiteIps");
        if (StringUtils.isBlank(whiteIpStr)) {
            HttpNettyUtils.responseNeedParam(channel, "whiteIps");
            return;
        }
        Set<String> whiteIps = Sets.newHashSet(StringUtils.split(whiteIpStr, ","));
        AuthConfig authConfig = source.getAuthConfig();
        switch (authConfig.getAuthMode()) {
        case NONE:
            // 无需鉴权
            HttpNettyUtils.responseJsonFailed(channel,
                    String.format("the target source [%s] does not auth", source.getName()));
            return;
        case BLACK_IP:
        case USER_ONLY:
            HttpNettyUtils.responseJsonFailed(channel,
                    String.format("the target source [%s] does not support white ip auth", source.getName()));
            return;
        case WHITE_IP_ONLY:
            // 白名单模式才需要添加IP
            List<String> successAddIps = addWhiteIpsToSource(source, whiteIps);
            HttpNettyUtils.responseJsonSuccess(channel, successAddIps);
            return;
        default:
            HttpNettyUtils
                    .responseJsonFailed(channel, String.format("unknown auth mode source [%s]", source.getName()));
        }

    }

    private List<String> addWhiteIpsToSource(Source source, Collection<String> whiteIps) {
        AuthConfig authConfig = source.getAuthConfig();
        List<String> filterIps = whiteIps.stream().filter(IPUtils::isIpV4).collect(Collectors.toList());
        authConfig.getWhiteIps().addAll(filterIps);
        return filterIps;
    }
}
