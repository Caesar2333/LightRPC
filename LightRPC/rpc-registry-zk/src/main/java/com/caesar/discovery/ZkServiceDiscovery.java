package com.caesar.discovery;

import com.caesar.config.RpcConfigLoader;
import com.caesar.registry.ServiceDiscovery;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public class ZkServiceDiscovery implements ServiceDiscovery {

    private final CuratorFramework client;
    private static final String ROOT_PATH = "/rpc";



    public ZkServiceDiscovery() {
        this.client = CuratorFrameworkFactory.builder()
                .connectString(RpcConfigLoader.getConfig().getRegistryAddress())
                .retryPolicy(new ExponentialBackoffRetry(1000, 3))
                .build();
        client.start();
    }

    @Override
    public List<InetSocketAddress> lookup(String serviceName) {

        try {
            String servicePath = ROOT_PATH + "/" + serviceName;
            List<String> addressList = client.getChildren().forPath(servicePath);

            if (addressList.isEmpty()) {
                throw new RuntimeException("服务未找到：" + serviceName);
            }

            List<InetSocketAddress> inetSocketAddresses = addressList.stream()
                    .map(address -> address.trim().split(":"))
                    .map(split -> new InetSocketAddress(split[0], Integer.parseInt(split[1])))
                    .toList();

            return inetSocketAddresses;
            // 暂时简单随机选一个（后续配合负载均衡策略）
//            String address = addressList.get(new Random().nextInt(addressList.size()));
//            String[] split = address.split(":");
//            return new InetSocketAddress(split[0], Integer.parseInt(split[1]));

        } catch (Exception e) {
            throw new RuntimeException("服务发现失败", e);
        }
    }

}
