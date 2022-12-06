package com.virjar.spider.proxy.ha.auth;

import com.google.common.collect.Sets;
import com.virjar.spider.proxy.ha.Constants;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ini4j.ConfigParser;

/**
 * Date: 2021-04-25
 *
 * @author alienhe
 */
@Slf4j
public class AuthHelper {

    public static AuthConfig parseAuthConfigs(ConfigParser config, String sourceItem)
            throws ConfigParser.NoSectionException, ConfigParser.NoOptionException,
            ConfigParser.InterpolationException {
        try {
            AuthConfig authConfig = new AuthConfig();
            authConfig.setAuthMode(AuthConfig.AuthMode.valueOf(
                    StringUtils.trimToEmpty(config.get(sourceItem, Constants.CONFIG_GLOBAL.AUTH_MODE)).toUpperCase()));
            switch (authConfig.getAuthMode()) {
            case BLACK_IP:
                parseBlackIps(authConfig, config, sourceItem);
                parseAuthToken(authConfig, config, sourceItem);
                break;
            case USER_ONLY:
                parseAuthToken(authConfig, config, sourceItem);
                break;
            case WHITE_IP_ONLY:
                parseWhiteIps(authConfig, config, sourceItem);
                break;
            case ALL:
                parseBlackIps(authConfig, config, sourceItem);
                parseWhiteIps(authConfig, config, sourceItem);
                parseAuthToken(authConfig, config, sourceItem);
                break;
            default:
            }
            return authConfig;
        } catch (IllegalArgumentException e) {
            log.warn("==== please check your auth mode is right spelled!");
            return null;
        }
    }

    private static void parseBlackIps(AuthConfig authConfig, ConfigParser config, String sourceItem)
            throws ConfigParser.NoSectionException, ConfigParser.NoOptionException,
            ConfigParser.InterpolationException {
        if (!config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.AUTH_BLACK_IPS)) {
            return;
        }
        String blackIps = StringUtils.trimToEmpty(config.get(sourceItem, Constants.CONFIG_GLOBAL.AUTH_BLACK_IPS));
        if (StringUtils.isNotBlank(blackIps)) {
            authConfig.setBlackIps(Sets.newHashSet(StringUtils.split(blackIps, ",")));
        }
    }

    private static void parseWhiteIps(AuthConfig authConfig, ConfigParser config, String sourceItem)
            throws ConfigParser.NoSectionException, ConfigParser.NoOptionException,
            ConfigParser.InterpolationException {
        if (!config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.AUTH_WHITE_IPS)) {
            return;
        }
        String whiteIps = StringUtils.trimToEmpty(config.get(sourceItem, Constants.CONFIG_GLOBAL.AUTH_WHITE_IPS));
        if (StringUtils.isNotBlank(whiteIps)) {
            authConfig.setWhiteIps(Sets.newHashSet(StringUtils.split(whiteIps, ",")));
        }
    }

    private static void parseAuthToken(AuthConfig authConfig, ConfigParser config, String sourceItem)
            throws ConfigParser.NoSectionException, ConfigParser.NoOptionException,
            ConfigParser.InterpolationException {
        if (!config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.AUTH_USERNAME) || !config
                .hasOption(sourceItem, Constants.CONFIG_GLOBAL.AUTH_PWD)) {
            return;
        }
        String userName = StringUtils.trimToEmpty(config.get(sourceItem, Constants.CONFIG_GLOBAL.AUTH_USERNAME));
        String pwd = StringUtils.trimToEmpty(config.get(sourceItem, Constants.CONFIG_GLOBAL.AUTH_PWD));
        if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(pwd)) {
            authConfig.setAuthUsername(userName);
            authConfig.setAuthPassword(pwd);
        }
    }
}
