package com.caesar.enums;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/29
 */

import lombok.Getter;

/**
 * 下面为默认实现提供的枚举
 */

@Getter
public enum CompressTypeEnum {


    NONE((byte) 1,"none"),
    GZIP((byte) 2,"gzip");


    /**
     * -- GETTER --
     *  有了枚举对象才能得到对应的code
     *
     * @return
     */
    private final byte code;
    private final String name;

    CompressTypeEnum(byte code, String name) {
        this.code = code;
        this.name = name;
    }

    /**
     * 此时只有数字，所以需要一个静态方法来获取对应的枚举对象
     */
    public static CompressTypeEnum fromCode(byte code) {

        for(CompressTypeEnum type : values()) {
            if(type.getCode() == code) {
                return type;
            }
        }

        throw new IllegalArgumentException("Unknown default codec type: " + code); // 当找不到对应的数字的时候 直接抛出错误

    }
}
