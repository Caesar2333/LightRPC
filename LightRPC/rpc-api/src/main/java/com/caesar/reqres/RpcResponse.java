package com.caesar.reqres;

import lombok.Data;

import java.io.Serializable;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
@Data
public class RpcResponse<T> implements Serializable {

    private boolean success; // 是否调用成功
    private String message; // 错误信息
    private T data;
    private long requestId; // 和请求对应的id


}
