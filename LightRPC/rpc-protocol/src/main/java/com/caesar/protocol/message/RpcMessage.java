package com.caesar.protocol.message;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RpcMessage implements Serializable {

    /**
     * rpc协议的设计 和这里的message的字段有所不同。
     * 魔数 版本号 消息类型 序列化方式 压缩类型 请求id 数据长度 数据内容
     * 其中的数据长度 是后续在 encode中计算出来的
     */


    private byte messageType;
    private byte serializationType;
    private byte compressType;
    private long requestId; // 设置为long 8个字节 64位 2^64种可能 足够了
    private Object data; // 通常是 rpcResponse或者是RpcRequest



}
