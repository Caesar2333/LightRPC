package com.caesar.balancer;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.List;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */

public interface LoadBalancer {

    /**
     * 从一组服务地址中选择一个
     * @param serviceAddresses 可用服务地址列表（IP:Port）
     * 从某个 服务名中 找到一系列的候选地址，根据具体的实现，从中之找到一个
     * @return 选中的地址
     */
    InetSocketAddress select(List<InetSocketAddress> serviceAddresses);

}
