package com.virjar.spider.proxy.ha.auth;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Set;

/**
 * Date: 2021-04-25
 * 鉴权配置
 *
 * @author alienhe
 */
public class AuthConfig {
    @Getter
    @Setter
    private AuthMode authMode = AuthMode.NONE;
    @Getter
    @Setter
    private Set<String> whiteIps = Sets.newHashSet();
    @Getter
    @Setter
    private Set<String> blackIps = Sets.newHashSet();
    @Getter
    @Setter
    private String authUsername;
    @Getter
    @Setter
    private String authPassword;

    /**
     * 客户端鉴权模式
     */
    public enum AuthMode {
        /**
         * 不鉴权
         */
        NONE,
        /**
         * 设置用户名+密码的方式
         */
        USER_ONLY,
        /**
         * IP白名单
         */
        WHITE_IP_ONLY,
        /**
         * IP黑名单，可以与USER模式一起使用
         */
        BLACK_IP,
        /**
         * 所有模式的配置均可生效
         */
        ALL
    }
}
