package com.caesar.handler;

import com.caesar.config.RpcConfigLoader;
import com.caesar.enums.MessageTypeEnum;
import com.caesar.extension.ExtensionLoader;
import com.caesar.protocol.message.RpcMessage;
import com.caesar.registry.ServiceProvider;
import com.caesar.reqres.RpcRequest;
import com.caesar.reqres.RpcResponse;
import com.caesar.utils.ServiceUtils;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.lang.reflect.Method;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
public class NettyServerHandler extends ChannelInboundHandlerAdapter {

    private final ServiceProvider serviceProvider = ExtensionLoader.getExtensionLoader(ServiceProvider.class)
            .getExtension(RpcConfigLoader.getConfig().getRegistry());

    /**
     * 这个方法是对管道读取来的 数据进行处理的
     * 这里需要做的是 根据发过来的需求，找到对应的服务，得到结果，返回回去
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {


        System.out.println("服务端收到了请求！" + msg);


        // 服务端读取消息
        RpcMessage message = (RpcMessage) msg;

        // 只是处理请求的消息
        if(message.getMessageType() != MessageTypeEnum.REQUEST.getCode())
        {
            return;
        }

        RpcRequest request = (RpcRequest) message.getData();

        String serviceName = ServiceUtils.buildServiceName(request.getInterfaceName(), request.getGroup(), request.getVersion());
        System.out.println("serviceProvider正在查找服务：" + serviceName);

        Object service = serviceProvider.getService(serviceName);
        System.out.println("serviceProvider找到了服务 ： " + service);


        RpcResponse<Object> response = new RpcResponse<>();
        response.setRequestId(request.getRequestId());

        try{
            // 万一服务调取失败 需要抓取一下服务
            Method method = service.getClass().getMethod(request.getMethodName(), request.getParameterTypes());
            Object result = method.invoke(service, request.getParameters());

            response.setSuccess(true);
            response.setData(result);

        }catch(Exception e)
        {
            response.setSuccess(false);
            response.setMessage("服务调用失败" + e.getMessage());
        }

        // 将响应的消息写回去
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setRequestId(request.getRequestId());
        rpcMessage.setMessageType(MessageTypeEnum.RESPONSE.getCode());
        rpcMessage.setData(response);
        rpcMessage.setCompressType(message.getCompressType());
        rpcMessage.setSerializationType(message.getSerializationType());

        ctx.writeAndFlush(rpcMessage);

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace(); // 如果出现错误的话，就将错误打印出来
        ctx.close(); // 同时关闭上下文
    }
}
