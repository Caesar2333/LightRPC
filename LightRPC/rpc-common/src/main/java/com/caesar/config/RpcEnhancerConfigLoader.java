package com.caesar.config;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
public class RpcEnhancerConfigLoader {

    private static final String CONFIG_FILE = "rpc.properties";

    private static final RpcEnhancerConfig CONFIG;

    static {
        CONFIG = new RpcEnhancerConfig();
        try (InputStream input = Thread.currentThread() // 这里是读取文件
                .getContextClassLoader()
                .getResourceAsStream(CONFIG_FILE)) {
            // 判空
            if (input == null) {
                throw new RuntimeException("未找到配置文件: " + CONFIG_FILE);
            }
            Properties properties = new Properties();
            properties.load(input);

            CONFIG.setRetryCount(Integer.parseInt(properties.getProperty("rpc.enhancer.retryCount")));
            CONFIG.setRetryEnabled(Boolean.parseBoolean(properties.getProperty("rpc.enhancer.retryEnabled")));
            CONFIG.setTimeoutMillis(Integer.parseInt(properties.getProperty("rpc.enhancer.timeoutMillis")));

        } catch (IOException e) {
            throw new RuntimeException("加载配置失败: " + CONFIG_FILE, e);
        }
    }

    public static RpcEnhancerConfig getConfig() {
        return CONFIG;
    }


}
