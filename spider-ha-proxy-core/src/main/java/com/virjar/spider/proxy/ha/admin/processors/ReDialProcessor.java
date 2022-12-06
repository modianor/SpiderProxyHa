package com.virjar.spider.proxy.ha.admin.processors;

import com.alibaba.fastjson.JSONObject;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.admin.AdminRequestProcessor;
import com.virjar.spider.proxy.ha.core.Source;
import com.virjar.spider.proxy.ha.utils.HttpNettyUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;

public class ReDialProcessor extends BaseAuthProcessor {
    @Override
    public void process0(Channel channel, JSONObject request, HttpHeaders httpHeaders) {
        Integer mappingPort = request.getInteger("mappingPort");
        if (mappingPort == null) {
            HttpNettyUtils.responseNeedParam(channel,"mappingPort");
            return;
        }
        Source source = Configs.sourceMap.get(mappingPort);
        if (source == null) {
            HttpNettyUtils.responseJsonFailed(channel, "none mapping port");
            return;
        }
        HttpNettyUtils.responseJsonSuccess(channel, "ok");
        source.reDial(mappingPort);
    }
}
