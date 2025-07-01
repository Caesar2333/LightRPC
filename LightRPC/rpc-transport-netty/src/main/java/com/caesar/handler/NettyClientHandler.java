package com.caesar.handler;

import com.caesar.client.UnprocessedRequests;
import com.caesar.enums.MessageTypeEnum;
import com.caesar.protocol.message.RpcMessage;
import com.caesar.reqres.RpcResponse;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
public class NettyClientHandler extends SimpleChannelInboundHandler<RpcMessage> {

    private final UnprocessedRequests unprocessedRequests;

    public NettyClientHandler(UnprocessedRequests unprocessedRequests) {
        this.unprocessedRequests = unprocessedRequests;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RpcMessage msg) throws Exception {

        System.out.println("成功收到了来服务端的响应" + msg);


        // 客户端的handler来自服务端的响应
        if(msg.getMessageType() == MessageTypeEnum.RESPONSE.getCode())
        {
            RpcResponse<?> response =  (RpcResponse<?>) msg.getData();
            unprocessedRequests.complete(response); // 将拿到的结果 直接回填给对应的future
        }

    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        cause.printStackTrace(); // 如果出现错误的话，就将错误打印出来
        ctx.close(); // 同时关闭上下文
    }

}
