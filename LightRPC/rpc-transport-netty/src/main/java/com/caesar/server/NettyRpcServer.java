package com.caesar.server;

import com.caesar.config.RpcConfigLoader;
import com.caesar.handler.NettyServerHandler;
import com.caesar.codec.RpcMessageDecoder;
import com.caesar.codec.RpcMessageEncoder;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */

/**
 * 🧠 关键点是：
 * 通信两端	哪些 Channel 是“主动创建”的？	用途
 * 客户端	✅ 主动 new NioSocketChannel	拿来连服务端
 * 服务端	✅ 手动 new NioServerSocketChannel
 * ✅ Netty 自动 new NioSocketChannel	前者用于监听，后者用于和客户端通信
 */


public class NettyRpcServer implements RpcServer {


    /**
     * 根据 选定从哪一个port来开启netty服务来启动服务
     * @param port 开启服务的端口
     */
    public void start()
    {
        EventLoopGroup bossGroup = new NioEventLoopGroup(1); // 只是处理连接事件
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 真正地处理请求

        try{

            ServerBootstrap bootstrap = new ServerBootstrap(); //这个启动类
            bootstrap.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class) // “我这个 bootstrap（服务端）自己要创建的监听 Channel 是这个类型”，和worker无关
                    .childHandler(new ChannelInitializer<SocketChannel>() { // 每一条经过的链接都是需要这样做的

                        @Override
                        protected void initChannel(SocketChannel socketChannel) throws Exception {
                            ChannelPipeline pipeline = socketChannel.pipeline();
                            pipeline.addLast(new LengthFieldBasedFrameDecoder(1024*1024,
                                    16, 4, 0, 0)); // in
                            socketChannel.pipeline().addLast(new RpcMessageDecoder()); // in
                            socketChannel.pipeline().addLast(new RpcMessageEncoder()); // out
                            pipeline.addLast(new NettyServerHandler()); // 在pipeline上 添加上 handler链条 in
                        }
                    });


            int port = RpcConfigLoader.getConfig().getServerPort(); // 配置文件 配置 port

            ChannelFuture future = bootstrap.bind(port).sync(); // 同步绑定一下 port端口
            System.out.println("服务端启动成功，端口：" + port);
            future.channel().closeFuture().sync(); // 服务启动后一直卡着，只到服务关闭

        }catch(InterruptedException e)
        {
            e.printStackTrace();
        }
        finally {
            bossGroup.shutdownGracefully();
            workerGroup.shutdownGracefully();
        }

    }

}
