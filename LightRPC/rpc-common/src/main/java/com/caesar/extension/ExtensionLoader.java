package com.caesar.extension;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */

/**
 * 每一个接口都有一个loader
 * 然后每一个loader再根据配置文件中的名字去找到实现类缓存起来
 * 这里的loader实现的懒加载，用到的时候才直接去加载，并且在内存的map中有实例的缓存
 * @param <T>
 */
public class ExtensionLoader<T> {


    private static final Map<Class<?>, ExtensionLoader<?>> LOADERS = new ConcurrentHashMap<>(); // loader的缓存
    private static final String SPI_DIR = "META-INF/extensions/";

    private final Class<T> type; // 接口的名字，不同的接口 不同的loader
    private final Map<String, T> extensionInstances = new ConcurrentHashMap<>(); // 实例对象的缓存

    private ExtensionLoader(Class<T> type) {
        this.type = type;
    }

    public static <T> ExtensionLoader<T> getExtensionLoader(Class<T> type) {
        if (type == null) throw new IllegalArgumentException("Extension type == null");
        return (ExtensionLoader<T>) LOADERS.computeIfAbsent(type, ExtensionLoader::new);// 高级get，首先通过key去get，没有的话 传入参数type到后面的构造函数 重新创建一个对象
    }

    public T getExtension(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("Extension name == null");
        }
        return extensionInstances.computeIfAbsent(name, this::createExtension);
    }

    /**
     * 实际上这个方法就是在读文件，分析已经约定好的格式 取出等号左边和右边的东西来加载
     * @param name
     * @return
     */
    private T createExtension(String name) {
        String fileName = SPI_DIR + type.getName();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                Thread.currentThread().getContextClassLoader().getResourceAsStream(fileName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] parts = line.split("=");
                if (parts.length != 2) continue;
                if (parts[0].trim().equals(name)) {
                    String className = parts[1].trim();
                    Class<?> clazz = Class.forName(className);
                    System.out.println("正在加载extensions:" + className);
                    return (T) clazz.getDeclaredConstructor().newInstance();
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("加载扩展失败: " + name + " for type " + type.getName(), e);
        }
        throw new IllegalStateException("找不到扩展名为 " + name + " 的实现 for " + type.getName());
    }






}
