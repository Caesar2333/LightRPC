package com.caesar.factory;

import com.caesar.codec.Compressor;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
public class CompressorFactory {

    private static final Map<Byte, Compressor> codeMap = new HashMap<>();
    private static final Map<String,Compressor> nameMap = new HashMap<>();

    // （1）通过static静态代码块，在spi指定的路径下，加载所有的实现类，并且放入到map中维护

    static{

        ServiceLoader<Compressor> loader = ServiceLoader.load(Compressor.class); // 由于有默认实现类 所以loader不可能为null

        for(Compressor compressor : loader){

            // 找出重复的
            if(codeMap.containsKey(compressor.getCode())){
                throw new RuntimeException("Duplicate serializer code: " + compressor.getCode());
            }
            // 如果没有重复的话，那就依次维护到map中
            codeMap.put(compressor.getCode(), compressor);
            nameMap.put(compressor.getName(), compressor);

        }

    }

    // (1) 根据指定的code找到对应的Compressor
    public static Compressor getCompressor(byte code){
        return codeMap.get(code);
    }


    // (2) 根据指定的name获得Compressor
    public static Compressor getCompressor(String name){
        return nameMap.get(name);
    }



}
