package com.virjar.spider.proxy.ha;

import cn.hutool.http.HttpUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.virjar.spider.proxy.ha.admin.PortalManager;
import com.virjar.spider.proxy.ha.auth.AuthConfig;
import com.virjar.spider.proxy.ha.auth.AuthHelper;
import com.virjar.spider.proxy.ha.core.HaProxyMapping;
import com.virjar.spider.proxy.ha.core.Source;
import com.virjar.spider.proxy.ha.core.UpstreamProducer;
import com.virjar.spider.proxy.ha.utils.ClasspathResourceUtil;
import com.virjar.spider.proxy.ha.utils.IPUtils;
import io.netty.util.concurrent.DefaultThreadFactory;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.ini4j.ConfigParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class HaProxyBootstrap {

    public static void main(String[] args) throws Exception {
        // 加载所有的后端数据源配置
        loadSourceConfig();
        // 静态初始化
        HaProxyMapping.staticInit();

        // 如果有内存泄漏，打开这个用于排查
        // ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.ADVANCED);

        // 定时任务拉取数据源，启动HA服务
        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1, new DefaultThreadFactory("main-scheduler"));
        scheduler.scheduleAtFixedRate(Configs::doRefreshResource, 0,
                Configs.refreshUpstreamInterval
                , TimeUnit.SECONDS);
        UpstreamProducer.relocateOutIpResolver();

        if (Configs.adminServerPort > 0
                && Configs.adminServerPort < 65535) {
            PortalManager.startService();
        }
    }

    private static void loadSourceConfig() throws Exception {
        InputStream stream = ClasspathResourceUtil.getResourceAsStream(Constants.CONFIG_FILE);

        if (stream == null) {
            throw new IOException("can not load config resource: " + Constants.CONFIG_FILE);
        }
        ConfigParser config = new ConfigParser();
        config.read(stream);
        List<String> sections = config.sections();
        for (String sourceItem : sections) {
            String type = config.get(sourceItem, Constants.CONFIG_SECTION.TYPE);
            if (type.equals(Constants.CONFIG_GLOBAL.CONFIG_SECTION_TYPE_GLOBAL)) {
                parseGlobal(config, sourceItem);
            }
        }
    }

    public static List<Source> updateSource() {
        try {
            String responseBody = HttpUtil.get(Configs.proxySourceURL);
            JSONObject jsonData = JSON.parseObject(responseBody);
            JSONArray sources = jsonData.getJSONObject("data").getJSONArray("source");
            int num = jsonData.getJSONObject("data").getIntValue("num");
            log.info("get proxy source:{}", sources);
            List<Source> ret = Lists.newArrayListWithCapacity(num);
            for (int i = 0; i < num; i++) {
                JSONObject source = sources.getJSONObject(i);
                String type = source.getString("type");
                if (type.equals(Constants.CONFIG_SECTION.CONFIG_SECTION_TYPE_SOURCE)) {
                    ret.add(parseSource(source));
                }
            }
            return ret;
        } catch (Exception e) {
            log.error("error", e);
            return Lists.newArrayListWithCapacity(0);
        }
    }

    private static void parseGlobal(ConfigParser config, String sourceItem) throws ConfigParser.NoSectionException, ConfigParser.NoOptionException, ConfigParser.InterpolationException {
        if (config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.REFRESH_UPSTREAM_INTERVAL)) {
            // 刷新频率
            Configs.refreshUpstreamInterval = Integer.parseInt(
                    config.get(sourceItem, Constants.CONFIG_GLOBAL.REFRESH_UPSTREAM_INTERVAL)
            );
        }
        if (config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.CACHE_CONNECTION_SIZE)) {
            Configs.cacheConnPerUpstream = Integer.parseInt(
                    config.get(sourceItem, Constants.CONFIG_GLOBAL.CACHE_CONNECTION_SIZE)
            );
        }
        if (config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.CACHE_CONNECTION_ALIVE_SECONDS)) {
            Configs.cacheConnAliveSeconds = Integer.parseInt(
                    config.get(sourceItem, Constants.CONFIG_GLOBAL.CACHE_CONNECTION_ALIVE_SECONDS)
            );
        }
        if (config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.LISTEN_TYPE)) {
            String listenType = StringUtils.trimToEmpty(config.get(sourceItem, Constants.CONFIG_GLOBAL.LISTEN_TYPE))
                    .toLowerCase();
            configListenIp(listenType);
        }
        if (config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.AUTH_MODE)) {
            Configs.authConfig = AuthHelper.parseAuthConfigs(config, sourceItem);
        }

        if (config.hasOption(sourceItem, Constants.ADMIN_SERVER_PORT)) {
            Configs.adminServerPort = Integer.parseInt(
                    config.get(sourceItem, Constants.ADMIN_SERVER_PORT)
            );
        }

        if (config.hasOption(sourceItem, Constants.ADMIN_API_TOKEN)) {
            Configs.adminApiToken = config.get(sourceItem, Constants.ADMIN_API_TOKEN);
        }
    }

    private static void configListenIp(String listenType) {
        if (IPUtils.isIpV4(listenType)) {
            Configs.listenIp = listenType;
            return;
        }
        switch (listenType) {
            //lo,private,public,all
            case "lo":
                Configs.listenIp = "127.0.0.1";
                break;
            case "all":
                Configs.listenIp = "0.0.0.0";
                break;
            default:
                try {
                    Configs.listenIp = IPUtils.fetchIp(listenType);
                } catch (SocketException e) {
                    //ignore
                }
        }
        if (StringUtils.isBlank(Configs.listenIp)) {
            Configs.listenIp = "0.0.0.0";
        }

    }

    private static Source parseSource(ConfigParser config, String sourceItem)
            throws ConfigParser.NoSectionException, ConfigParser.NoOptionException,
            ConfigParser.InterpolationException {
        Source source = new Source(sourceItem, config.get(sourceItem, Constants.CONFIG_SECTION.NAME),
                config.get(sourceItem, Constants.CONFIG_SECTION.PROTOCOL),
                config.get(sourceItem, Constants.CONFIG_SECTION.SOURCE_URL),
                config.get(sourceItem, Constants.CONFIG_SECTION.MAPPING_SPACE));
        if (config.hasOption(sourceItem, Constants.CONFIG_SECTION.UPSTREAM_AUTH_USER)) {
            source.setUpstreamAuthUser(config.get(sourceItem, Constants.CONFIG_SECTION.UPSTREAM_AUTH_USER));
            source.setUpstreamAuthPassword(config.get(sourceItem, Constants.CONFIG_SECTION.UPSTREAM_AUTH_PASSWORD));
        }
        if (config.hasOption(sourceItem, Constants.CONFIG_GLOBAL.AUTH_MODE)) {
            source.setAuthConfig(AuthHelper.parseAuthConfigs(config, sourceItem));
        } else {
            source.setAuthConfig(new AuthConfig());
        }

        return source;
    }

    private static Source parseSource(JSONObject data) {
        String type = data.getString("type");
        String name = data.getString("name");
        String protocol = data.getString("protocol");
        String sourceUrl = data.getString("sourceUrl");
        String mappingSpace = data.getString("mappingSpace");
        String upstreamAuthUser = data.getString("upstream_auth_user");
        String upstreamAuthPassword = data.getString("upstream_auth_password");
        String authMode = data.getString("auth_mode");
        Source source = new Source(type, name, protocol, sourceUrl, mappingSpace);
        if (StringUtils.isNotEmpty(upstreamAuthUser)) {
            source.setUpstreamAuthUser(upstreamAuthUser);
            source.setUpstreamAuthPassword(upstreamAuthPassword);
        }
        source.setAuthConfig(new AuthConfig());
        return source;
    }

}
