package com.caesar.serializerImpl;

import com.caesar.enums.SerializationTypeEnum;
import com.caesar.codec.Serializer;

import java.io.*;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
public class JdkSerializer implements Serializer {

    @Override
    public byte[] serialize(Object obj) {
        // (1)判空
        if(obj == null)
        {
            return new byte[0]; // 返回换一个空的数组，不建议返回null
        }
        // (2) 判断是否具有serializable的条件
        if(!(obj instanceof Serializable))
        {
            // 如果没有实现这个接口的话，直接抛出错误
            throw new IllegalArgumentException("Serializer can only be used with Serializable");
        }


        // try(资源1；资源2)为 try-with-resources语句，可以省略使用finally关闭资源
        try(ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ObjectOutputStream oos = new ObjectOutputStream(bos))
        {
            // 开始序列化流程，直接使用java原生api
            // 下列为，jdk序列化标准流程，不用纠结

            oos.writeObject(obj);
            return bos.toByteArray();

        }catch (IOException e){
            throw new RuntimeException("jdk serialization failed", e);
        }
    }

    @Override
    public <T> T deserialize(byte[] data, Class<T> clazz) {

        // (1)判空
        if(data == null || data.length == 0)
        {
            throw new IllegalArgumentException("data cannot be null");
        }

        // 开始序列化
        try(ByteArrayInputStream bis = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bis))
        {
            Object object = ois.readObject();
            return clazz.cast(object);

        }catch (IOException | ClassNotFoundException e)
        {
            throw new RuntimeException("jdk deserialization failed", e);
        }
    }

    @Override
    public byte getCode() {
        // jdk为默认的方法，内部维护了一个枚举
        return SerializationTypeEnum.JDK.getCode();
    }

    @Override
    public String getName() {
        return SerializationTypeEnum.JDK.getName();
    }
}
