package com.caesar.compressorImpl;

import com.caesar.codec.Compressor;
import com.caesar.enums.CompressTypeEnum;


import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
public class GzipCompressor implements Compressor {
    @Override
    public byte[] compress(byte[] data) {

        // 判空
        if(data == null || data.length == 0)
        {
            return new byte[0]; // 不建议返回null
        }

        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             GZIPOutputStream gzip = new GZIPOutputStream(bos)) {

            gzip.write(data);
            gzip.close();
            return bos.toByteArray();

        } catch (IOException e) {
            throw new RuntimeException("GZIP compression failed", e);
        }
    }

    @Override
    public byte[] decompress(byte[] data) {

        // 判空
        if(data == null || data.length == 0)
        {
            return new byte[0];
        }


        try (ByteArrayInputStream bis = new ByteArrayInputStream(data);
             GZIPInputStream gzip = new GZIPInputStream(bis);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[1024];
            int len;
            while ((len = gzip.read(buffer)) != -1) {
                out.write(buffer, 0, len);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("GZIP decompress failed", e);
        }
    }

    @Override
    public byte getCode() {
        // 内部默认的compressor有枚举实现
        return CompressTypeEnum.GZIP.getCode();
    }

    @Override
    public String getName() {
        return CompressTypeEnum.GZIP.getName();
    }
}
