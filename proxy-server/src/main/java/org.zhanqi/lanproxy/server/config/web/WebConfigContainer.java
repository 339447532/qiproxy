package org.zhanqi.qiproxy.server.config.web;

import org.zhanqi.qiproxy.common.container.Container;
import org.zhanqi.qiproxy.server.config.ProxyConfig;
import org.zhanqi.qiproxy.server.config.web.routes.RouteConfig;
import org.zhanqi.qiproxy.server.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;

public class WebConfigContainer implements Container {

    private static Logger logger = LoggerFactory.getLogger(WebConfigContainer.class);

    private NioEventLoopGroup serverWorkerGroup;

    private NioEventLoopGroup serverBossGroup;

    public WebConfigContainer() {

        // 配置管理，并发处理很小，使用单线程处理网络事件
        serverBossGroup = new NioEventLoopGroup(1);
        serverWorkerGroup = new NioEventLoopGroup(1);

    }

    @Override
    public void start() {
        ServerBootstrap httpServerBootstrap = new ServerBootstrap();
        httpServerBootstrap.group(serverBossGroup, serverWorkerGroup).channel(NioServerSocketChannel.class)
                .childHandler(new ChannelInitializer<SocketChannel>() {

                    @Override
                    public void initChannel(SocketChannel ch) throws Exception {
                        ChannelPipeline pipeline = ch.pipeline();

                        pipeline.addLast(new HttpServerCodec());
                        pipeline.addLast(new HttpObjectAggregator(64 * 1024));
                        pipeline.addLast(new ChunkedWriteHandler());
                        pipeline.addLast(new HttpRequestHandler());
                    }
                });

        try {
            httpServerBootstrap.bind(ProxyConfig.getInstance().getConfigServerBind(),
                    ProxyConfig.getInstance().getConfigServerPort()).get();
            logger.info("http server start on port " + ProxyConfig.getInstance().getConfigServerPort());
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }

        RouteConfig.init();

        // 强制初始化 UserService 以确保 SQLite 数据库被创建
        UserService.getInstance();
    }

    @Override
    public void stop() {
        serverBossGroup.shutdownGracefully();
        serverWorkerGroup.shutdownGracefully();
    }

}
