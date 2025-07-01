package com.caesar;

import com.caesar.config.RpcConfigLoader;
import com.caesar.extension.ExtensionLoader;
import com.caesar.registry.ServiceProvider;
import com.caesar.registry.ServiceRegistry;
import com.caesar.server.NettyRpcServer;
import com.caesar.server.RpcServer;
import com.caesar.service.UserService;
import com.caesar.service.impl.UserServiceImpl;
import com.caesar.utils.ServiceUtils;


import java.net.InetSocketAddress;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
public class ServerDemo {

    public static void main(String[] args) {

        UserService userService = new UserServiceImpl();

        ServiceProvider serviceProvider = ExtensionLoader.getExtensionLoader(ServiceProvider.class)
                .getExtension(RpcConfigLoader.getConfig().getRegistry());


        serviceProvider.publishService(UserService.class,userService,"test","v1"); // 注册一下本地的服务



        // 启动服务
        RpcServer server = new NettyRpcServer();
        server.start();


    }



}
