package com.virjar.spider.proxy.ha.auth;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * Date: 2021-04-25
 * 待鉴权信息
 *
 * @author alienhe
 */
@Builder
public class AuthInfo {

    @Getter
    @Setter
    private String username;
    @Getter
    @Setter
    private String pwd;
    @Getter
    @Setter
    private String ip;
    @Getter
    @Setter
    private String authToken;


}
