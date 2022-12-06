package com.virjar.spider.proxy.ha.auth;

import com.google.common.collect.Maps;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.core.Source;

import java.util.Map;

/**
 * Date: 2021-04-25
 *
 * @author alienhe
 */
public class AuthenticatorManager {

    private static final String GLOBAL = "global";

    private static Map<String, IAuthenticator> cacheAuthenticators = Maps.newHashMap();

    public static IAuthenticator getAuthenticator(Source source) {
        IAuthenticator authenticator = cacheAuthenticators.get(source.getName());
        if (authenticator == null) {
            authenticator = new BasicAuthenticator();
            cacheAuthenticators.put(source.getName(), authenticator);
        }
        if (authenticator instanceof BaseRefreshGlobalAuthenticator) {
            // refresh auth config
            ((BaseRefreshGlobalAuthenticator) authenticator).refresh(source.getAuthConfig());
        }
        return authenticator;
    }

    public static IAuthenticator getGlobalAuthenticator(){
        IAuthenticator authenticator = cacheAuthenticators.get(GLOBAL);
        if (authenticator == null) {
            authenticator = new BasicAuthenticator();
            cacheAuthenticators.put(GLOBAL, authenticator);
        }
        if (authenticator instanceof BaseRefreshGlobalAuthenticator) {
            ((BaseRefreshGlobalAuthenticator) authenticator).refresh(Configs.authConfig);
        }
        return authenticator;
    }
}
