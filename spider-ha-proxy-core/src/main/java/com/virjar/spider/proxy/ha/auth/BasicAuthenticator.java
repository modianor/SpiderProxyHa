package com.virjar.spider.proxy.ha.auth;

import com.virjar.spider.proxy.ha.utils.ClientAuthUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Set;

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
        String authToken = authInfo.getAuthToken();
        switch (authConfig.getAuthMode()) {
        case WHITE_IP_ONLY:
            return authWhiteIp(authInfo.getIp());
        case USER_ONLY:
            return authToken(authInfo.getAuthToken());
        case BLACK_IP:
            String ip = authInfo.getIp();
            return StringUtils.isBlank(authToken) ? authBlackIp(ip) : authBlackIp(ip) && authToken(authToken);
        case ALL:
            // 1.先判断Token
            boolean isTokenPass = authToken(authToken);
            // 2.再判断IP
            boolean isWhiteIpPass = authWhiteIp(authInfo.getIp());
            boolean isBlackIpPass = authBlackIp(authInfo.getIp());
            // 3.任一模式通过均可
            return isBlackIpPass && (isTokenPass || isWhiteIpPass);
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
        case ALL:
            return authWhiteIp(authInfo.getIp()) && authBlackIp(authInfo.getIp());
        default:
            return true;
        }
    }

    private boolean authToken(String proxyAuthContent) {
        return StringUtils.equals(proxyAuthContent, ClientAuthUtils
                .getBasicAuthToken(getAuthConfig().getAuthUsername(), getAuthConfig().getAuthPassword()));
    }

    private boolean authWhiteIp(String clientIp) {
        return isIpInRange(clientIp, getAuthConfig().getWhiteIps());
    }

    /**
     * 黑名单IP鉴权
     *
     * @param clientIp 客户端Ip
     * @return 是否通过黑名单IP检测
     */
    private boolean authBlackIp(String clientIp) {
        return !isIpInRange(clientIp, getAuthConfig().getBlackIps());
    }

    private boolean isIpInRange(String ip, Set<String> cidrSets) {
        for (String cidr : cidrSets) {
            if (isIpInRange(ip, cidr)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isIpInRange(String ip, String cidr) {
        if (!cidr.contains("/")) {
            return StringUtils.equals(ip, cidr);
        }
        // cidr写法
        String[] ips = ip.split("\\.");
        int ipAddr =
                (Integer.parseInt(ips[0]) << 24) | (Integer.parseInt(ips[1]) << 16) | (Integer.parseInt(ips[2]) << 8)
                        | Integer.parseInt(ips[3]);
        int type = Integer.parseInt(cidr.replaceAll(".*/", ""));
        int mask = 0xFFFFFFFF << (32 - type);
        String cidrIp = cidr.replaceAll("/.*", "");
        String[] cidrIps = cidrIp.split("\\.");
        int cidrIpAddr = (Integer.parseInt(cidrIps[0]) << 24) | (Integer.parseInt(cidrIps[1]) << 16) | (
                Integer.parseInt(cidrIps[2]) << 8) | Integer.parseInt(cidrIps[3]);
        return (ipAddr & mask) == (cidrIpAddr & mask);
    }
}
