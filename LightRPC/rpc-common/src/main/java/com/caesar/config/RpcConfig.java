package com.caesar.config;

import lombok.Data;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
@Data
public class RpcConfig {

    private String compressor;
    private String serializer;
    private String registry;
    private String loadBalancer;
    private String registryAddress;
    private int serverPort;
    private String serverAddress;





}
