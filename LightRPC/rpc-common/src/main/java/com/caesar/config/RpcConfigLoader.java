package com.caesar.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
public class RpcConfigLoader {

    private static final String CONFIG_FILE = "rpc.properties";

    private static final RpcConfig CONFIG;

    static {
        CONFIG = new RpcConfig();
        try (InputStream input = Thread.currentThread() // 这里是读取文件
                .getContextClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            // 判空
            if (input == null) {
                throw new RuntimeException("未找到配置文件: " + CONFIG_FILE);
            }
            Properties properties = new Properties();
            properties.load(input);

            CONFIG.setCompressor(properties.getProperty("rpc.compressor"));
            CONFIG.setSerializer(properties.getProperty("rpc.serializer"));
            CONFIG.setRegistry(properties.getProperty("rpc.registry"));
            CONFIG.setLoadBalancer(properties.getProperty("rpc.loadbalancer"));
            CONFIG.setRegistryAddress(properties.getProperty("rpc.registry.address"));
            CONFIG.setServerPort(Integer.parseInt(properties.getProperty("rpc.server.port")));
            CONFIG.setServerAddress(properties.getProperty("rpc.server.address"));

        } catch (IOException e) {
            throw new RuntimeException("加载配置失败: " + CONFIG_FILE, e);
        }
    }

    public static RpcConfig getConfig() {
        return CONFIG;
    }


}
