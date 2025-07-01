package com.caesar.service.impl;

import com.caesar.service.UserService;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */
public class UserServiceImpl implements UserService {
    @Override
    public String hello() {
        return "恭喜客户端拿到我的服务！";
    }
}
