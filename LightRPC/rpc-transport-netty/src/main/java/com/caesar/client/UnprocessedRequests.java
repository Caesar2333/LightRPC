package com.caesar.client;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */

import com.caesar.reqres.RpcResponse;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 这个模块主要用于回填逻辑
 * 里面维护了一个map，requestid - future
 * 这样的话，当客户端收到响应的时候，就知道对哪一个之前的request中的future进行回填了
 */
public class UnprocessedRequests {

    private static final Map<Long, CompletableFuture<RpcResponse<?>>> REQUEST_MAP = new ConcurrentHashMap<>();

    /**
     * 大概有三个方法，放入map中维护，删除其中的键值对，以及对future进行回填
     */

    public void put(long requestId, CompletableFuture<RpcResponse<?>> future)
    {
        REQUEST_MAP.put(requestId, future);
    }

    public void remove(long requestId)
    {
        REQUEST_MAP.remove(requestId);
    }

    /**
     * 这个函数的逻辑是：我现在已经拿到了服务端的response，但是我不知道这个response到底是属于谁的，所以所以我需要在这里找到对应的
     * future，将我这个response回填回去，这样的话。在用户的客户端层面，其在使用本地调用拿到的data就是来源于我的response的。
     * @param response
     */
    public void complete(RpcResponse<?> response)
    {
        CompletableFuture<RpcResponse<?>> future = REQUEST_MAP.remove(response.getRequestId());

        if(future != null)
        {
            future.complete(response); // 这个将response填入，后续 future.get拿到的就是这个
        }
    }






}
