package com.caesar.reqres;

import lombok.Data;

import java.io.Serializable;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
@Data
public class RpcRequest implements Serializable {

    private String interfaceName; // 接口名字
    private String methodName; // 方法名字
    private Object[] parameters; // 方法参数列表
    private Class<?>[] parameterTypes;
    private String group; //服务分组
    private String version; // 服务版本号
    private long requestId; // 请求id，用于和响应关联

}
