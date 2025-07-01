package com.caesar.enums;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */

/**
 * 下面为默认实现提供的枚举
 */



public enum MessageTypeEnum {

    REQUEST((byte)1),
    RESPONSE((byte)2),
    HEARTBEAT_REQUEST((byte)3),
    HEARTBEAT_RESPONSE((byte)4);

    private final byte code;

    MessageTypeEnum(final byte code) {
        this.code = code;
    }

    /**
     * 有了枚举对象才能得到对应的code
     * @return
     */
    public byte getCode() {
        return code;
    }

    /**
     * 此时只有数字，所以需要一个静态方法来获取对应的枚举对象
     */
    public static MessageTypeEnum fromCode(byte code) {

        for(MessageTypeEnum type : values()) {
            if(type.getCode() == code) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown default codec type: " + code); // 当找不到对应的数字的时候 直接抛出错误

    }



}
