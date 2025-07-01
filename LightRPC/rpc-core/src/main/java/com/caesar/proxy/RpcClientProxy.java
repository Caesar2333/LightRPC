package com.caesar.proxy;

import com.caesar.client.RpcClient;

import com.caesar.enhancer.RpcEnhancer;
import com.caesar.enums.MessageTypeEnum;
import com.caesar.protocol.message.RpcMessage;
import com.caesar.reqres.RpcRequest;
import com.caesar.reqres.RpcResponse;
import com.caesar.utils.RequestIdGenerator;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public class RpcClientProxy implements InvocationHandler {

    /**
     * 这个netty通信实现 基于netty message的封装
     * 主要的作用就是 将rpcrequest封装
     * 将rpcmessage封装
     * 具体的发送细节交给具体的实现类 RpcNettyClient去做的
     * RpcClient client = new NettyRpcClient();
     * RpcClientProxy proxy = new RpcClientProxy(client,
     *     SerializerTypeEnum.KRYO.getCode(),
     *     CompressorTypeEnum.GZIP.getCode());
     *
     * UserService userService = proxy.getProxy(UserService.class);
     * User user = userService.getUserById(42);  // 实际发出远程请求
     * 上述为实例
     *
     */

    private final RpcClient rpcClient;
    private final byte serializerCode;
    private final byte compressorCode;
    private String group;
    private String version;

    /**
     * 构造函数先这样，后续使用 配置 + spi的方式，这个是不合格的。
     * @param client
     * @param serializerCode
     * @param compressorCode
     */
    public RpcClientProxy(RpcClient client, byte serializerCode, byte compressorCode) {
        this.rpcClient = client;
        this.serializerCode = serializerCode;
        this.compressorCode = compressorCode;
    }

    @SuppressWarnings("unchecked")
    public <T> T getProxy(Class<T> interfaceClass,String group,String version) {
        this.group = group;
        this.version = version;
        return (T) Proxy.newProxyInstance(
                interfaceClass.getClassLoader(),
                new Class<?>[]{interfaceClass},
                this
        );
    }


    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {

        RpcRequest rpcRequest = new RpcRequest();
        rpcRequest.setInterfaceName(method.getDeclaringClass().getName());
        rpcRequest.setMethodName(method.getName());
        rpcRequest.setParameters(args);
        rpcRequest.setParameterTypes(method.getParameterTypes());
        rpcRequest.setGroup(this.group);
        rpcRequest.setVersion(this.version);

        long requestId = RequestIdGenerator.nextId();
        rpcRequest.setRequestId(requestId);

        RpcMessage message = new RpcMessage();
        message.setMessageType(MessageTypeEnum.REQUEST.getCode());
        message.setSerializationType(serializerCode);
        message.setCompressType(compressorCode);
        message.setRequestId(requestId);
        message.setData(rpcRequest);

        // 这里是接口编程，所以需要你具体的client实现去发送这个message,也就是我们之前写的netty模块，解码编码，序列化的系列东西
        // 重试和超时加强
        CompletableFuture<RpcResponse<?>> future = RpcEnhancer
                .withRetry(() -> RpcEnhancer.withTimeout(rpcClient.sendRequest(message)));

        RpcResponse<?> response = null;
        try {
            response = future.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            throw new RuntimeException("rpc调用失败：" + cause.getMessage(),cause);
        }


        if(response.isSuccess())
        {
            // 如果调用成功的话，直接获取数据并且返回
            return response.getData();
        }else {
            throw new RuntimeException("调用远程服务失败" + response.getMessage());
        }

    }
}
