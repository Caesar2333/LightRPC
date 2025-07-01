package com.caesar.utils;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public class ServiceUtils {

    public static String buildServiceName(String interfaceName, String group, String version) {
        return interfaceName + ":" + group + ":" + version;
    }


}
