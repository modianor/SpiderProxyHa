package com.virjar.spider.proxy.ha.auth;

public interface IAuthenticator {

    /**
     * 是否鉴权通过，鉴权所有信息包括IP
     *
     * @param authInfo 鉴权判定需要的所有信息
     * @return 是否通过
     */
    boolean authenticate(AuthInfo authInfo);

    /**
     * 仅鉴权IP
     * 对于socks代理来说，ip鉴权和密码鉴权是两个步骤，所以需要提前判定
     *
     * @param authInfo 鉴权判定需要的所有信息,但是没有账户和密码字段
     * @return 是否通过
     */
    boolean authenticateWithIp(AuthInfo authInfo);
}
