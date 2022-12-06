package com.virjar.spider.proxy.ha.admin.processors;

import com.alibaba.fastjson.JSONObject;
import com.virjar.spider.proxy.ha.Configs;
import com.virjar.spider.proxy.ha.admin.AdminRequestProcessor;
import com.virjar.spider.proxy.ha.utils.HttpNettyUtils;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpHeaders;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Date: 2021-04-28
 *
 * @author alienhe
 */
@Slf4j
public abstract class BaseAuthProcessor implements AdminRequestProcessor {

    @Override
    public void process(Channel channel, JSONObject request, HttpHeaders httpHeaders) {
        if (authByToken(request)) {
            try{
                process0(channel, request, httpHeaders);
            }catch (Exception e){
                log.error("process handler error:{}",request,e);
                HttpNettyUtils.responseJsonFailed(channel,"exception occured!");
            }
        } else {
            log.warn("request with wrong admin api token:{}", request);
            HttpNettyUtils.responseJsonFailed(channel, "wrong api token");
        }
    }

    public abstract void process0(Channel channel, JSONObject request, HttpHeaders httpHeaders);

    private boolean authByToken(JSONObject request) {
        String apiToken = request.getString("apiToken");
        return StringUtils.equals(apiToken, Configs.adminApiToken);
    }
}
