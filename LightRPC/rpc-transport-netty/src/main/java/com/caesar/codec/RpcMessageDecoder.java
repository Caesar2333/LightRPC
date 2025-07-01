package com.caesar.codec;

import com.caesar.config.RpcConfig;
import com.caesar.config.RpcConfigLoader;
import com.caesar.constants.MessageConstants;
import com.caesar.enums.MessageTypeEnum;
import com.caesar.extension.ExtensionLoader;
import com.caesar.protocol.message.RpcMessage;
import com.caesar.reqres.RpcRequest;
import com.caesar.reqres.RpcResponse;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
public class RpcMessageDecoder extends ByteToMessageDecoder {
    @Override
    protected void decode(ChannelHandlerContext channelHandlerContext, ByteBuf in, List<Object> out) throws Exception {

        // 获取全员的配置
        RpcConfig config = RpcConfigLoader.getConfig();

        // （1）魔数校验

        int magic = in.readInt();
        if(magic != MessageConstants.MAGIC_NUMBER)
        {
            // 如果魔数不相等的话，说明不是这个协议的，抛出错误
            throw new IllegalArgumentException("magic is not correct，魔数为：" + Integer.toHexString(magic));
        }
        // 魔数匹配后 开始以此读出数据

        byte version = in.readByte();
        byte messageType = in.readByte();
        byte serializationType = in.readByte();
        byte compressType = in.readByte();
        long requestId = in.readLong();

        // 开始读取数据
        int length = in.readInt();
        byte[] data = new byte[length];
        in.readBytes(data); // 读取到数据

        // 解压缩
        Compressor compressor = ExtensionLoader.getExtensionLoader(Compressor.class)
                .getExtension(config.getCompressor());
        System.out.println("压缩实现为：" + compressor.getClass().getName());
        byte[] decompressedData = compressor.decompress(data);

        System.out.println("解压缩完成！");

        // 反序列化
        Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class)
                .getExtension(config.getSerializer());

        System.out.println("序列化实现为：" + serializer.getClass().getName());

        Object object = null;
        if(messageType == MessageTypeEnum.REQUEST.getCode())
        {
            object = serializer.deserialize(decompressedData, RpcRequest.class);
        }else if(messageType == MessageTypeEnum.RESPONSE.getCode())
        {
            object = serializer.deserialize(decompressedData, RpcResponse.class);
        }

        System.out.println("反序列化完成");

        // 通过out 写出message对象
        RpcMessage rpcMessage = new RpcMessage();
        rpcMessage.setMessageType(messageType);
        rpcMessage.setSerializationType(serializationType);
        rpcMessage.setCompressType(compressType);
        rpcMessage.setRequestId(requestId);
        rpcMessage.setData(object);

        System.out.println("成功收到rpcMessage");

        out.add(rpcMessage);

    }
}
