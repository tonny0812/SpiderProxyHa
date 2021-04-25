package com.virjar.spider.proxy.ha.auth;

import com.virjar.spider.proxy.ha.utils.ClientAuthUtils;
import org.apache.commons.lang3.StringUtils;

/**
 * Date: 2021-04-25
 *
 * @author alienhe
 */
public class BasicAuthenticator extends BaseRefreshGlobalAuthenticator {

    BasicAuthenticator() {
    }

    @Override
    public boolean authenticate0(AuthInfo authInfo) {
        AuthConfig authConfig = getAuthConfig();
        switch (authConfig.getAuthMode()) {
        case WHITE_IP_ONLY:
            return authWhiteIp(authInfo.getIp());
        case USER_ONLY:
            return authToken(authInfo.getAuthToken());
        case BLACK_IP:
            String authToken = authInfo.getAuthToken();
            String ip = authInfo.getIp();
            return StringUtils.isBlank(authToken) ? authBlackIp(ip) : authBlackIp(ip) && authToken(authToken);
        default:
            return true;
        }
    }

    @Override
    public boolean authenticateWithIp0(AuthInfo authInfo) {
        AuthConfig authConfig = getAuthConfig();
        switch (authConfig.getAuthMode()) {
        case WHITE_IP_ONLY:
            return authWhiteIp(authInfo.getIp());
        case BLACK_IP:
            return authBlackIp(authInfo.getIp());
        default:
            return true;
        }
    }

    private boolean authToken(String proxyAuthContent) {
        return StringUtils.equals(proxyAuthContent, ClientAuthUtils
                .getBasicAuthToken(getAuthConfig().getAuthUsername(), getAuthConfig().getAuthPassword()));
    }

    private boolean authWhiteIp(String clientIp) {
        return getAuthConfig().getWhiteIps().contains(clientIp);
    }

    private boolean authBlackIp(String clientIp) {
        return !getAuthConfig().getBlackIps().contains(clientIp);
    }
}
