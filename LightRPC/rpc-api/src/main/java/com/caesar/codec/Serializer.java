package com.caesar.codec;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */

/**
 * 序列化的spi接口定义
 * 由于后续 serializer有自己的默认实现，所以在用户使用的时候，我们需要表明建议用户使用的类型范围
 */

public interface Serializer {


    // (1)将一个obejct对象序列化成 byte[]
    byte[] serialize(Object obj);

    // (2) 将byte[]反序列化成 指定的java对象，加入制定对象的class
    <T> T deserialize(byte[] data, Class<T> clazz);

    // (3) 每一个序列化器的spi实现，都应该有自己的code以及name，同理，默认实现也是这样的
    byte getCode(); //用于编码协议字段。

    String getName(); // 用于配置 & 可读性

}
