package com.caesar.provider;

import com.caesar.config.RpcConfigLoader;
import com.caesar.extension.ExtensionLoader;
import com.caesar.registry.ServiceProvider;
import com.caesar.registry.ServiceRegistry;
import com.caesar.utils.ServiceUtils;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/6/30
 */
public class ZkServiceProviderImpl implements ServiceProvider {

    private final Map<String, Object> localServiceMap = new ConcurrentHashMap<>(); // 本地维护一下 所有的注册好的服务
    private final ServiceRegistry registry = ExtensionLoader.getExtensionLoader(ServiceRegistry.class)
            .getExtension(RpcConfigLoader.getConfig().getRegistry()); // SPI扩展


    /**
     * zookeeper的作用：把当前这个服务（如：com.example.UserService#v1#groupA）的网络地址（如：192.168.1.66:9000）注册到 Zookeeper 某个路径下。
     * 只是提供了服务发现和服务注册的功能，并且注册只是将服务的名字，和网络地址注册了
     *
     * 而真正提供服务的，需要依靠本地的localServiceMap，只要服务端向云端上注册一个服务，那么其就要将本地服务放入到这个map中维护，等到对方说出服务名字，你再给他
     * @param serviceClass 服务接口class
     * @param serviceInstance 具体服务类，真实的调用对象
     * @param group
     * @param version
     * @param <T>
     *
     */
    @Override
    public <T> void publishService(Class<T> serviceClass, T serviceInstance, String group, String version) {

        String serviceName = ServiceUtils.buildServiceName(serviceClass.getName(), group, version);

        localServiceMap.put(serviceName, serviceInstance); // 服务名字 和本地服务对象

        System.out.println("将服务名字为：" + serviceName + "的服务的实例保存到了本地");

        // 注册到 Zookeeper 注册中心
        // 这里需要在配置中 配置出 本地服务的端口以及ip
        String host = RpcConfigLoader.getConfig().getServerAddress();
        int port = RpcConfigLoader.getConfig().getServerPort();

        registry.register(serviceName, new InetSocketAddress(host, port)); // 注册进去
        System.out.println("注册了服务名为：" + serviceName + "的服务的——host :" + host + " port :" + port);

    }

    @Override
    public Object getService(String serviceName) {
        return localServiceMap.get(serviceName);
    }
}
