package com.caesar.client;


import com.caesar.protocol.message.RpcMessage;
import com.caesar.reqres.RpcResponse;

import java.util.concurrent.CompletableFuture;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public interface RpcClient {

    // 定义一个发送请求的接口
    // 每一种客户端的接口，可以是netty自定义协议的实现，也可以是其他的协议的实现

    CompletableFuture<RpcResponse<?>> sendRequest(RpcMessage message);

}
