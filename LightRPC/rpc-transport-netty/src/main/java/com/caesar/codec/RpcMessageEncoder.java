package com.caesar.codec;


import com.caesar.config.RpcConfig;
import com.caesar.config.RpcConfigLoader;
import com.caesar.extension.ExtensionLoader;
import com.caesar.constants.MessageConstants;
import com.caesar.protocol.message.RpcMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */
@ChannelHandler.Sharable // 这个注解表示 这个类可以还给很多channel使用的额
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    @Override
    protected void encode(ChannelHandlerContext channelHandlerContext, RpcMessage msg, ByteBuf out) throws Exception {

        // 获取全局的配置
        RpcConfig config = RpcConfigLoader.getConfig();

        // (1)魔数
        out.writeInt(MessageConstants.MAGIC_NUMBER);


        // (2)版本号
        out.writeByte(MessageConstants.VERSION); // 协议的版本号 固定为1

        // （3）message中去其他元素,需要按照协议规定的顺序，以此书写这些内容，
        out.writeByte(msg.getMessageType());
        out.writeByte(msg.getSerializationType());
        out.writeByte(msg.getCompressType());
        out.writeLong(msg.getRequestId());

        // (4)对data进行序列化和压缩

        byte[] bodyBytes = null; // 先创建一个容器
        if(msg.getData() != null) {
             // 如果数据不为空的话，才进行转换，否则的话 不转换

//            Serializer serializer = SerializerFactory.getSerializer(msg.getSerializationType());
//            Compressor compressor = CompressorFactory.getCompressor(msg.getCompressType());

            Serializer serializer = ExtensionLoader.getExtensionLoader(Serializer.class).getExtension(config.getSerializer());
            System.out.println("序列化实现为：" + serializer.getName() + ":" + serializer.getCode());
            Compressor compressor = ExtensionLoader.getExtensionLoader(Compressor.class).getExtension(config.getCompressor());
            System.out.println("压缩实现为：" + compressor.getName() + ":" + compressor.getCode()) ;

            byte[] serialize = serializer.serialize(msg.getData());
            System.out.println("序列化完成！");
            bodyBytes = compressor.compress(serialize);
            System.out.println("压缩完成");

        }else{
            // 否则的话返回一个空的byte[]
            bodyBytes = new byte[0];
        }


        // (5) 计算压缩后data的length，然后填入流
        out.writeInt(bodyBytes.length); // 数据的长度就是在这里写入的

        System.out.println("准备写入发送数据");
        out.writeBytes(bodyBytes); // 最后写入数据
        // 全部完成



    }
}
