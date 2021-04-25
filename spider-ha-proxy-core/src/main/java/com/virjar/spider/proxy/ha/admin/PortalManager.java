package com.virjar.spider.proxy.ha.admin;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpContentCompressor;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.concurrent.DefaultThreadFactory;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 管理端，可以用于操作代理服务器<br>
 * 但是只提供api，不考虑提供web页面
 */
public class PortalManager {
    private static final AtomicBoolean started = new AtomicBoolean(false);
    private static ServerBootstrap serverBootstrap;

    public static void startService() {
        if (started.compareAndSet(false, true)) {
            startServiceInternal();
        }
    }

    private static void startServiceInternal() {
        serverBootstrap = new ServerBootstrap();
        NioEventLoopGroup serverBossGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("admin-boss-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
        );
        NioEventLoopGroup serverWorkerGroup = new NioEventLoopGroup(
                0,
                new DefaultThreadFactory("admin-worker-group" + DefaultThreadFactory.toPoolName(NioEventLoopGroup.class))
        );
        serverBootstrap.group(serverBossGroup, serverWorkerGroup)
                .channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    public void initChannel(SocketChannel ch) {
                        ChannelPipeline pipeline = ch.pipeline();
                        pipeline.addLast(
                                "idle",
                                new IdleStateHandler(0, 0, 70)
                        );
                        pipeline.addLast(new HttpServerCodec());
                        // 作为api功能，不支持文件上传之类的大请求，所以只提供5k
                        pipeline.addLast(new HttpObjectAggregator(5 * 1025));
                        pipeline.addLast(new HttpContentCompressor());
                        pipeline.addLast(new PortalHttpRequestDispatcher());
                    }
                });
    }
}
