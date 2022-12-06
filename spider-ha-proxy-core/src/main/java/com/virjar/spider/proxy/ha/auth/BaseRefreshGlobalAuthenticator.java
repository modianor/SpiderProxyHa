package com.virjar.spider.proxy.ha.auth;

/**
 * Date: 2021-04-25
 *
 * 1. 可刷新鉴权配置
 * 2. 子source未配置鉴权信息时，通过global配置鉴权
 * @author alienhe
 */
public abstract class BaseRefreshGlobalAuthenticator implements IAuthenticator{

    private AuthConfig authConfig;

    AuthConfig getAuthConfig(){
        return this.authConfig;
    }

    void refresh(AuthConfig authConfig){
        this.authConfig = authConfig;
    }

    @Override
    public boolean authenticate(AuthInfo authInfo) {
        if(getAuthConfig() == null){
            return AuthenticatorManager.getGlobalAuthenticator().authenticate(authInfo);
        }
        return authenticate0(authInfo);
    }

    @Override
    public boolean authenticateWithIp(AuthInfo authInfo) {
        if(getAuthConfig() == null){
            return AuthenticatorManager.getGlobalAuthenticator().authenticateWithIp(authInfo);
        }
        return authenticateWithIp0(authInfo);
    }

    public abstract boolean authenticate0(AuthInfo authInfo);

    public abstract boolean authenticateWithIp0(AuthInfo authInfo);
}
