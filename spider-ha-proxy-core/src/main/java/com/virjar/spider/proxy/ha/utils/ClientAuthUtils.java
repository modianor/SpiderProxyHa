package com.virjar.spider.proxy.ha.utils;

import com.google.common.io.BaseEncoding;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/**
 * Date: 2021-04-25
 *
 * @author alienhe
 */
@Slf4j
public class ClientAuthUtils {

    public static String getClientIp(Channel channel, HttpRequest httpRequest) {
        if (channel == null || !channel.isActive()) {
            return StringUtils.EMPTY;
        }
        // 先判断是否由NG转发
        if (httpRequest != null) {
            // 从请求头获取 nginx 反代设置的客户端真实 IP
            String clientIp = httpRequest.headers().get("X-Real-IP");
            if (StringUtils.isNotBlank(clientIp)) {
                return clientIp;
            }
        }
        // 如果为空则使用 netty 默认获取的客户端 IP
        InetSocketAddress address = (InetSocketAddress) channel.remoteAddress();
        return address.getAddress().getHostAddress();
    }

    /**
     * @param username 鉴权用户
     * @param pwd      鉴权密码
     * @return Basic + base64(username:password) 即Basic Auth
     */
    public static String getBasicAuthToken(String username, String pwd) {
        return "Basic " + BaseEncoding.base64().encode((username + ":" + pwd).getBytes(StandardCharsets.UTF_8));
    }

}
