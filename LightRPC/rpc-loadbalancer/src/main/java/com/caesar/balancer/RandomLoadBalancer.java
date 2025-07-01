package com.caesar.balancer;

import java.net.InetSocketAddress;
import java.util.List;
import java.util.Random;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
public class RandomLoadBalancer implements LoadBalancer {

    private final Random random = new Random();

    @Override
    public InetSocketAddress select(List<InetSocketAddress> serviceAddresses) {

        if (serviceAddresses == null || serviceAddresses.isEmpty()) {
            throw new RuntimeException("没有可用的服务地址");
        }

        int index = random.nextInt(serviceAddresses.size());
        return serviceAddresses.get(index);
    }
}
