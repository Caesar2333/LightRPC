package com.caesar.registry;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */

import java.net.InetSocketAddress;

/**
 * 服务端用于将自己的地址 注册到注册中心去
 */
public interface ServiceRegistry {

    /**
     *
     * @param serviceName
     * @param address 表示的是 主机和端口号的组合
     */
    void register(String serviceName, InetSocketAddress address);

}
