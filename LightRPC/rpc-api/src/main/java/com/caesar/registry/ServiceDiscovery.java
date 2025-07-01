package com.caesar.registry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */



public interface ServiceDiscovery {

    /**
     * 通过服务的名字来找到对应的 ip和端口
     * @param serviceName
     * @return
     */
    List<InetSocketAddress> lookup(String serviceName);


}
