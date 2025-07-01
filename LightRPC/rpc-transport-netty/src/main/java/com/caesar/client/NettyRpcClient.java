package com.caesar.client;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */


import com.caesar.balancer.LoadBalancer;
import com.caesar.config.RpcConfig;
import com.caesar.config.RpcConfigLoader;
import com.caesar.extension.ExtensionLoader;
import com.caesar.handler.NettyClientHandler;
import com.caesar.codec.RpcMessageDecoder;
import com.caesar.codec.RpcMessageEncoder;
import com.caesar.protocol.message.RpcMessage;
import com.caesar.registry.ServiceDiscovery;
import com.caesar.reqres.RpcRequest;
import com.caesar.reqres.RpcResponse;

import com.caesar.utils.ServiceUtils;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 这里的客户端是 netty的客户端，整个rpc框架的客户端，整个框架的底层通信。
 * 而我们之前写的这个demo是模拟的是使用我们人，在客户端的时候，是怎么调用我的东西的。
 *
 * 🧠 关键点是：
 * 通信两端	哪些 Channel 是“主动创建”的？	用途
 * 客户端    主动 new NioSocketChannel	拿来连服务端
 * 服务端	手动 new NioServerSocketChannel
 *  Netty 自动 new NioSocketChannel	前者用于监听，后者用于和客户端通信
 * 
 */

public class NettyRpcClient implements RpcClient {

    private final Bootstrap bootstrap;
    private final EventLoopGroup group;
    private final UnprocessedRequests unprocessedRequests; // 这个是处理回填的核心类，里面维护了一个map,以及对这个map的操作
    private final ServiceDiscovery serviceDiscovery;
    private final LoadBalancer loadBalancer;

    private final Map<String, Channel> channelCache = new ConcurrentHashMap<>();

    /*
    * 注意一下，handler的引入是需要注意顺序的，其是一个有序的双向链表，会按照你的顺序来处理上述的东西
    * 同时需要注意的是：不管是在客户端还是服务端注册的handler，这些handler都是针对于你接受到的数据的整个处理链条
    * 而不是你主动发出数据的处理链条。
    * 这就是为什么，你客户端sendrequest的时候，你必须自己调用handler处理，而来的数据，就自己经过你的handler链条进行处理的
     */
    public NettyRpcClient()
    {
        group = new NioEventLoopGroup(); // 这个是客户端的工作线程。客户端这边也有一个netty，来处理io的
        bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioSocketChannel.class) // 客户端线程是这个类型的
                .handler(new ChannelInitializer<SocketChannel>() {
                    @Override
                    protected void initChannel(SocketChannel socketChannel) throws Exception {
                        ChannelPipeline pipeline = socketChannel.pipeline();

                        pipeline.addLast(new LengthFieldBasedFrameDecoder(1024*1024,
                                16, 4, 0, 0));
                        pipeline.addLast(new RpcMessageEncoder()); // out
                        pipeline.addLast(new RpcMessageDecoder()); // in
                        pipeline.addLast(new NettyClientHandler(unprocessedRequests)); // in 所以排在decoder的后面，否则会出现错误

                    }
                });

        unprocessedRequests = new UnprocessedRequests();

        // 获得配置
        RpcConfig config = RpcConfigLoader.getConfig();

        this.serviceDiscovery = ExtensionLoader.getExtensionLoader(ServiceDiscovery.class)
                .getExtension(config.getRegistry());

        this.loadBalancer = ExtensionLoader.getExtensionLoader(LoadBalancer.class)
                .getExtension(config.getLoadBalancer());

    }


    @Override
    public CompletableFuture<RpcResponse<?>> sendRequest(RpcMessage message) {

        /**
         * 我们现在已经拿到了RpcMessage了，为什么还要写这个sendrequest？？因为写触发handler的话，是需要手动触发的
         */
        CompletableFuture<RpcResponse<?>> future = new CompletableFuture<>();

        try{
            unprocessedRequests.put(message.getRequestId(),future); // 先维护
            RpcRequest request = (RpcRequest) message.getData();
            String interfaceName = request.getInterfaceName();
            String group1 = request.getGroup();
            String version = request.getVersion();

            String serviceName = ServiceUtils.buildServiceName(interfaceName, group1, version);

            Channel channel = getChannel(serviceName);

            channel.writeAndFlush(message).addListener((ChannelFutureListener) f ->{
                if(!f.isSuccess())
                {
                    unprocessedRequests.remove(message.getRequestId()); // 如果发送失败的话，直接移除这个需求
                    future.completeExceptionally(f.cause());
                }
            });


        }catch(Exception e){

            unprocessedRequests.remove(message.getRequestId());
            future.completeExceptionally(e);

        }

        return future; // 成功的话 有数据，失败的话会报错


    }





    /**
     * 通过注册中心 以及 负载均衡 来挑选 链接的实例
     * @param serviceName
     * @return
     * @throws InterruptedException
     */
    private Channel getChannel(String serviceName) throws InterruptedException {

        if(channelCache.containsKey(serviceName))
        {
            Channel channel = channelCache.get(serviceName);
            if(channel != null && channel.isActive()) return channel;
        }

        // 否则就创立新的链接
        // 获取所有的服务地址有多少个
        List<InetSocketAddress> addresses = serviceDiscovery.lookup(serviceName);

        InetSocketAddress select = loadBalancer.select(addresses);
        System.out.println("挑选好了服务，准备发送请求,服务地址和端口分别为：" + select.getAddress().getHostAddress() + ":" + select.getPort() );

        Channel channel = bootstrap.connect(select).sync().channel();
        channelCache.put(serviceName, channel);
        return channel;

    }



}
