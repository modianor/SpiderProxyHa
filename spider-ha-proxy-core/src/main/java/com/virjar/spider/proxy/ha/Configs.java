package com.virjar.spider.proxy.ha;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.virjar.spider.proxy.ha.auth.AuthConfig;
import com.virjar.spider.proxy.ha.core.Source;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Configs {
    public static List<Source> sourceList = new ArrayList<>();
    /**
     * 资源刷新间隔时间
     */
    public static int refreshUpstreamInterval = 30;
    /**
     * 后端探测接口，探测代理ip是否可用以及解析出口ip地址
     */
    public static String proxyHttpTestURL = "https://sekiro.virjar.com/dly/getPublicIp";

    public static String proxySourceURL = "http://localhost:6048/proxyipcenter/source";

    public static int cacheConnPerUpstream = 3;
    public static int cacheConnAliveSeconds = 30;

    public static String listenIp = "0.0.0.0";

    public static AuthConfig authConfig;

    public static int adminServerPort = 9085;
    public static String adminApiToken = "";

    public static void doRefreshResource() {
        // 数据库里当前的source
        List<Source> sources = HaProxyBootstrap.updateSource();

        // 刷新新添加的source
        for (Source source : sources) {
            // 不存在 代表新添加的source 直接加入到sourceList
            if (!containSource(sourceList, source)) {
                sourceList.add(source);
            }
        }

        // 检查哪些被停止的source
        List<Integer> destroyIndex = new ArrayList<>();
        for (int i = 0; i < sourceList.size(); i++) {
            Source source = sourceList.get(i);
            if (!containSource(sources, source)) {
                destroyIndex.add(i);
            }
        }

        for (Integer index : destroyIndex) {
            Source remove = sourceList.remove(index.intValue());
            remove.destroy();
            remove = null;
        }

        // 刷新更新的source
        for (Source source : sourceList) {
             source.refresh();
        }
    }

    public static Set<String> openPortSet = Sets.newConcurrentHashSet();

    public static Map<Integer, Source> sourceMap = Maps.newHashMap();

    public static boolean containSource(List<Source> sourceList, Source source) {
        for (Source s : sourceList) {
            if (StringUtils.equals(s.getName(), source.getName())) {
                return true;
            }
        }
        return false;
    }
}
