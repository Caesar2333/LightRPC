package com.caesar;

import com.caesar.client.NettyRpcClient;
import com.caesar.client.RpcClient;
import com.caesar.codec.Compressor;
import com.caesar.codec.Serializer;
import com.caesar.config.RpcConfigLoader;
import com.caesar.extension.ExtensionLoader;
import com.caesar.proxy.RpcClientProxy;
import com.caesar.service.UserService;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
public class ClientDemo {

    public static void main(String[] args) {
        RpcClient client = new NettyRpcClient(); // 或 ExtensionInitializer.RPC_CLIENT

        byte serializerCode = ExtensionLoader.getExtensionLoader(Serializer.class)
                .getExtension(RpcConfigLoader.getConfig().getSerializer())
                .getCode();

        System.out.println("序列化的code为：" + serializerCode);

        byte compressorCode = ExtensionLoader.getExtensionLoader(Compressor.class)
                .getExtension(RpcConfigLoader.getConfig().getCompressor())
                .getCode();
        System.out.println("压缩的code为：" + compressorCode);

        RpcClientProxy clientProxy = new RpcClientProxy(client, serializerCode, compressorCode);

        UserService userService = clientProxy.getProxy(UserService.class,"test","v1");
        System.out.println(userService.hello());


    }




}
