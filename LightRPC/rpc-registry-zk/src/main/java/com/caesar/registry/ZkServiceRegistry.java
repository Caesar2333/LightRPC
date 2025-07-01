package com.caesar.registry;


import com.caesar.config.RpcConfigLoader;
import org.apache.curator.framework.CuratorFramework;
import org.apache.curator.framework.CuratorFrameworkFactory;
import org.apache.curator.retry.ExponentialBackoffRetry;
import org.apache.zookeeper.CreateMode;

import java.net.InetSocketAddress;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public class ZkServiceRegistry implements ServiceRegistry {

    private final CuratorFramework client;
    private static final String ROOT_PATH = "/rpc";

    public ZkServiceRegistry() {
        this.client = CuratorFrameworkFactory.builder()
                .connectString(RpcConfigLoader.getConfig().getRegistryAddress()) // ZK地址
                .retryPolicy(new ExponentialBackoffRetry(1000, 3)) // 重试机制
                .build();
        client.start();
    }


    @Override
    public void register(String serviceName, InetSocketAddress address) {

        String servicePath = ROOT_PATH + "/" + serviceName;
        String ip = address.getAddress().getHostAddress();
        int port = address.getPort();
        String addressNode = ip + ":" + port;
        String addressPath = servicePath + "/" + addressNode;


        try {
            if (client.checkExists().forPath(servicePath) == null) {
                client.create().creatingParentsIfNeeded().forPath(servicePath);
            }
            client.create()
                    .withMode(CreateMode.EPHEMERAL)
                    .forPath(addressPath); // 节点内容为空即可
        } catch (Exception e) {
            throw new RuntimeException("ZK服务注册失败", e);
        }








    }
}
