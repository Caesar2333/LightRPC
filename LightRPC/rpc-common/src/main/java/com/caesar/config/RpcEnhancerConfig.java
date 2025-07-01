package com.caesar.config;

import lombok.Data;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
@Data
public class RpcEnhancerConfig {

    private boolean retryEnabled;
    private int retryCount;
    private int timeoutMillis;


}
