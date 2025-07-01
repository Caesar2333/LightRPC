package com.caesar.enhancer;


import com.caesar.config.RpcEnhancerConfig;
import com.caesar.config.RpcEnhancerConfigLoader;


import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;


/**
 * Author: Yuxian Zheng
 * Version: 1.0
 * Date: 2025/7/1
 */

/**
 * 利用函数式编程 无侵入式的 添加了重试以及超时的增强代码
 * 超时和重试怎么配合：应该是每一个重试 都配合上超时等待
 */
public class RpcEnhancer {

    private static final ScheduledExecutorService TIMEOUT_EXECUTOR = Executors.newScheduledThreadPool(4);// 将等待的任务放在线程池中

    public static <T> CompletableFuture<T> withTimeout(CompletableFuture<T> future) {

        int timeoutMillis = RpcEnhancerConfigLoader.getConfig().getTimeoutMillis();
        CompletableFuture<T> timeout = new CompletableFuture<>();
        TIMEOUT_EXECUTOR.schedule(() -> timeout.completeExceptionally(new Exception("请求超时")), timeoutMillis, TimeUnit.MILLISECONDS);

        return future.applyToEither(timeout, Function.identity()); // 这个是看谁先返回，传入的future是异步请求的future，如果在设定的超时时间内没有返回的话，那么就返回这个timeout，如果返回的话，就返回future
    }

    public static <T> CompletableFuture<T> withRetry(Supplier<CompletableFuture<T>> taskSupplier) {
        RpcEnhancerConfig config = RpcEnhancerConfigLoader.getConfig();

        if (!config.isRetryEnabled()) return taskSupplier.get(); // 这里的get指代的是触发一起请求，懒触发，将触发请求的操作封装在函数式方法中get中了

        int maxRetries = config.getRetryCount();

        CompletableFuture<T> result = new CompletableFuture<>();

        attempt(taskSupplier, result, maxRetries); // 递归尝试

        return result;
    }

    private static <T> void attempt(Supplier<CompletableFuture<T>> taskSupplier, CompletableFuture<T> result, int retriesLeft) {
        /**
         * whenComplete是一个watcher，当请求返回的时候触发，成功的时候 res ！= null，ex = null，失败则相反
         */
        taskSupplier.get().whenComplete((res, ex) -> {
            if (ex == null) {
                result.complete(res); // 成功的时候，直接返回获得的结果
            } else {
                if (retriesLeft > 0) {
                    attempt(taskSupplier, result, retriesLeft - 1); // 失败则在重试一次
                } else {
                    result.completeExceptionally(ex);
                }
            }
        });
    }

}
