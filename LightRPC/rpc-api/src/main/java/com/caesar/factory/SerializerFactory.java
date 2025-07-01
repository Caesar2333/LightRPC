package com.caesar.factory;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */

import com.caesar.codec.Serializer;

import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;

/**
 * 工厂的目的就是加载所有的实现类，并且维护了 类型 - 实现类，名字-实现类 两个map
 * 使其可以通过 类型来快速找到实现类是什么。
 * 相当于管理所有实现类的入口
 */
public class SerializerFactory {

    private static final Map<Byte, Serializer> codeMap = new HashMap<>();
    private static final Map<String,Serializer> nameMap = new HashMap<>();

    // （1）通过static静态代码块，在spi指定的路径下，加载所有的实现类，并且放入到map中维护

    static{

        ServiceLoader<Serializer> loader = ServiceLoader.load(Serializer.class);

        for(Serializer serializer : loader){

            // 找出重复的
            if(codeMap.containsKey(serializer.getCode())){
                throw new RuntimeException("Duplicate serializer code: " + serializer.getCode());
            }
            // 如果没有重复的话，那就依次维护到map中
            codeMap.put(serializer.getCode(), serializer);
            nameMap.put(serializer.getName(), serializer);

        }

    }

    // (1) 根据指定的code找到对应的serializer
    public static Serializer getSerializer(byte code){
        return codeMap.get(code);
    }


    // (2) 根据指定的name获得serilizer
    public static Serializer getSerializer(String name){
        return nameMap.get(name);
    }


}
