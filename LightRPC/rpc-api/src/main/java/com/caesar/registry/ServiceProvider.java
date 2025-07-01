package com.caesar.registry;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */

public interface ServiceProvider {

    /**
     * 发布服务：将本地服务注册到注册中心
     * 还要填入 服务所属的 group和 version，同时request中也有对应的 version和group
     */
    <T> void publishService(Class<T> serviceClass, T serviceInstance, String group, String version);

    /**
     * 获取本地服务实例
     */
    Object getService(String serviceName);

}
