package com.caesar.codec;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */

/**
 * 压缩的spi接口定义
 */
public interface Compressor {

    // (1) 将一个byte[]压缩成一个byte[]
    byte[] compress(byte[] data);

    // (2) 将一个byte[]解压成一个byte[]
    byte[] decompress(byte[] data);

    // (3) Compressor也有自己类型号和名字
    byte getCode();

    String getName();

}
