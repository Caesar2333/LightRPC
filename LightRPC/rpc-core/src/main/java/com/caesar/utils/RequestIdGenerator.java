package com.caesar.utils;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public class RequestIdGenerator {

    private static final AtomicInteger counter = new AtomicInteger(0);

    public static long nextId()
    {
        return counter.getAndIncrement();
    }

}
