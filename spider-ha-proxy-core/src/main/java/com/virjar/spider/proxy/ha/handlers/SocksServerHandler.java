package com.virjar.spider.proxy.ha.handlers;

import com.virjar.spider.proxy.ha.auth.AuthInfo;
import com.virjar.spider.proxy.ha.auth.AuthenticatorManager;
import com.virjar.spider.proxy.ha.core.HaProxyMapping;
import com.virjar.spider.proxy.ha.handlers.upstream.Socks5UpstreamHandShaker;
import com.virjar.spider.proxy.ha.handlers.upstream.UpstreamHandShaker;
import com.virjar.spider.proxy.ha.utils.ClientAuthUtils;
import com.virjar.spider.proxy.ha.utils.NettyUtils;
import io.netty.channel.*;
import io.netty.handler.codec.socks.*;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SocksServerHandler extends SimpleChannelInboundHandler<SocksRequest> {
    private ChannelHandlerContext ctx;
    @Getter
    private Channel upstreamChannel;
    @Getter
    private HaProxyMapping haProxyMapping;

    private SocksCmdRequest req;

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, SocksRequest socksRequest) throws Exception {
        this.ctx = ctx;
        switch (socksRequest.requestType()) {
        case INIT:
            handleInit(ctx);
            break;
        case AUTH:
            handleAuth(ctx, (SocksAuthRequest) socksRequest);
            break;
        case CMD:
            handleCmd(ctx, socksRequest);
            break;
        case UNKNOWN:
            ctx.close();
            break;
        default:
        }

    }

    private void handleAuth(ChannelHandlerContext ctx, SocksAuthRequest socksRequest) {
        String username = socksRequest.username();
        String password = socksRequest.password();
        AuthInfo authInfo = AuthInfo.builder().username(username).pwd(password).build();
        // 密码鉴权
        if (AuthenticatorManager.getAuthenticator(haProxyMapping.getSource()).authenticate(authInfo)) {
            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
            ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.SUCCESS));
        } else {
            ctx.pipeline().addFirst(new SocksAuthRequestDecoder());
            ctx.writeAndFlush(new SocksAuthResponse(SocksAuthStatus.FAILURE));
        }
    }

    private void handleInit(ChannelHandlerContext ctx) {
        // 对于socks代理来说，ip鉴权和密码鉴权是两个步骤，所以需要提前判定,先鉴权IP，然后鉴权密码
        haProxyMapping = HaProxyMapping.get(ctx.channel());
        String clientIp = ClientAuthUtils.getClientIp(ctx.channel(), null);
        AuthInfo authInfo = AuthInfo.builder().ip(clientIp).build();
        if (AuthenticatorManager.getAuthenticator(haProxyMapping.getSource()).authenticate(authInfo)) {
            ctx.pipeline().addFirst(new SocksCmdRequestDecoder());
            ctx.writeAndFlush(new SocksInitResponse(SocksAuthScheme.NO_AUTH));
            return;
        }
        ctx.pipeline().addFirst(new SocksAuthRequestDecoder());
        ctx.write(new SocksInitResponse(SocksAuthScheme.AUTH_PASSWORD));
    }

    private void handleCmd(ChannelHandlerContext ctx, SocksRequest socksRequest) {
        req = (SocksCmdRequest) socksRequest;
        if (req.cmdType() != SocksCmdType.CONNECT) {
            ctx.close();
            return;
        }



        // 创建到 后端代理资源的链接
        haProxyMapping.borrowConnect(value -> {
            if (value == null) {
                log.warn("connect to proxy server failed:{} ", haProxyMapping.resourceKey());
                writeConnectFailed("");
                return;
            }
            upstreamChannel = value;
            new Socks5UpstreamHandShaker(upstreamChannel, haProxyMapping.getSource(),
                    new UpstreamHandShaker.UpstreamHandSharkCallback() {
                        @Override
                        public void onHandSharkFailed(String message) {
                            writeConnectFailed(message);
                        }

                        @Override
                        public void onHandSharkSuccess() {
                            writeConnectSuccess();
                        }
                    }, (SocksCmdRequest) socksRequest).doHandShark();
        });

    }

    private void writeConnectFailed(String message) {
        log.info("connect upstream failed {}", message);
        ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.FAILURE, req.addressType()));
        NettyUtils.closeChannelIfActive(ctx.channel());
    }

    private void writeConnectSuccess() {

        NettyUtils.makePair(ctx.channel(), upstreamChannel);
        NettyUtils.loveOther(ctx.channel(), upstreamChannel);

        ctx.channel().writeAndFlush(new SocksCmdResponse(SocksCmdStatus.SUCCESS, req.addressType()))
                .addListener((ChannelFutureListener) channelFuture -> {
                    if (!channelFuture.isSuccess()) {
                        // 极小的可能，writeConnectSuccess的时候 用户端连接已经关闭了，
                        // 如果是这样的话，loveOther可能无效
                        NettyUtils.closeChannelIfActive(upstreamChannel);
                        return;
                    }
                    ChannelPipeline pipeline = ctx.channel().pipeline();
                    pipeline.remove(SocksMessageEncoder.class);
                    pipeline.remove(SocksServerHandler.class);

                    pipeline.addLast(new RelayHandler(upstreamChannel));
                    upstreamChannel.pipeline().addLast(new RelayHandler(ctx.channel()));
                });

    }

    @Override
    public void channelReadComplete(ChannelHandlerContext ctx) {
        ctx.flush();
    }

}
