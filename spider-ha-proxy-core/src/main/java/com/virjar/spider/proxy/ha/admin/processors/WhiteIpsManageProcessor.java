package com.virjar.spider.proxy.ha.admin.processors;

import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Sets;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.Constants;
import com.virjar.spider.proxy.ha.auth.AuthConfig;
import com.virjar.spider.proxy.ha.core.Source;
import com.virjar.spider.proxy.ha.utils.ClasspathResourceUtil;
import com.virjar.spider.proxy.ha.utils.HttpNettyUtils;
import com.virjar.spider.proxy.ha.utils.IPUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.ini4j.ConfigParser;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Date: 2021-04-28
 *
 * @author alienhe
 */
@Slf4j
public class WhiteIpsManageProcessor extends BaseAuthProcessor {

    /**
     * whiteIps 待添加的白名单IP列表
     * source 代理渠道源，与config.ini中配置的source一致
     */
    @Override
    public void process0(Channel channel, JSONObject request, HttpHeaders httpHeaders) {
        String action = request.getString("action");
        if (StringUtils.isEmpty(action)) {
            HttpNettyUtils.responseNeedParam(channel, "action");
            return;
        }
        switch (action) {
        case "list":
            actionListWhiteIps(channel, request);
            break;
        case "add":
            actionAddWhiteIps(channel, request);
            break;
        default:
            HttpNettyUtils.responseJsonFailed(channel, "not support action:" + action);
        }

    }

    private Source getSource(Channel channel, JSONObject request) {
        Integer mappingPort = request.getInteger("mappingPort");
        if (mappingPort == null) {
            HttpNettyUtils.responseNeedParam(channel, "mappingPort");
            return null;
        }
        return Configs.sourceMap.get(mappingPort);
    }

    private void actionListWhiteIps(Channel channel, JSONObject request) {
        Source source = getSource(channel, request);
        HttpNettyUtils.responseJsonSuccess(channel, source);
    }

    private void actionAddWhiteIps(Channel channel, JSONObject request) {
        Source source = getSource(channel, request);
        if (source == null) {
            HttpNettyUtils.responseJsonFailed(channel, "none mapping port");
            return;
        }
        String whiteIpStr = request.getString("whiteIps");
        if (StringUtils.isBlank(whiteIpStr)) {
            HttpNettyUtils.responseNeedParam(channel, "whiteIps");
            return;
        }
        Set<String> whiteIps = Sets.newHashSet(StringUtils.split(whiteIpStr, ","));
        try {
            List<String> successAddIps = addWhiteIpsToSource(source, whiteIps);
            HttpNettyUtils.responseJsonSuccess(channel, successAddIps);
        } catch (IOException | ConfigParser.NoSectionException e) {
            log.error("refresh config.ini failed:", e);
            HttpNettyUtils.responseJsonFailed(channel, "refresh config.ini failed:" + e.getMessage());
        }
    }

    private List<String> addWhiteIpsToSource(Source source, Collection<String> whiteIps)
            throws IOException, ConfigParser.NoSectionException {
        AuthConfig authConfig = source.getAuthConfig();
        List<String> filterIps = whiteIps.stream().filter(this::isValidIp).collect(Collectors.toList());
        authConfig.getWhiteIps().addAll(filterIps);
        // refreshConfigIni(source.getId(), authConfig.getWhiteIps());
        return filterIps;
    }

    private boolean isValidIp(String ip){
        // cidr or ip
        return StringUtils.isNotBlank(ip) && (ip.contains("/") || IPUtils.isIpV4(ip));
    }

    private synchronized void refreshConfigIni(String sourceId, Collection<String> newWhiteIps)
            throws IOException, ConfigParser.NoSectionException {
        InputStream stream = ClasspathResourceUtil.getResourceAsStream(Constants.CONFIG_FILE);
        if (stream == null) {
            throw new IOException("can not refresh config resource: " + Constants.CONFIG_FILE);
        }
        ConfigParser config = new ConfigParser();
        config.read(stream);
        config.set(sourceId, Constants.CONFIG_GLOBAL.AUTH_WHITE_IPS, StringUtils.join(newWhiteIps,","));
        File file = new File(ClasspathResourceUtil.getResource(Constants.CONFIG_FILE).getFile());
        try(FileOutputStream outputStream = new FileOutputStream(file)){
            config.write(outputStream);
        }
    }
}
