# Lettuce提供的异步api针对于 servelet和webflux有两种api啊。

* #### 一种是针对于 `servlet`的completableFuture的容器的——直接的异步操作（返回的容器是completableFurure而已，用的是其结果处理链条）。并不是`supplyasync`这种有线程池的操作。没有带线程池。——底层默认是netty

  * #### 一般默认用线程池的一般都是 同步操作，为了不堵塞当前线程的选择。

* #### 一种是针对这个WebFlux Reactive 设计的。——底层默认的是netty



# 到底包线程池还是不包呢？？

## ✅ 实际开发建议：

1. **先看客户端是不是异步**：
   - 是：用它自己的异步回调（thenAccept, Mono, whatever）
   - 否：包线程池
2. **你自己如果已经有线程池，一定要避免“套娃”设计**：
   - 不要 `线程池-A` 调用 `线程池-B` 的代码并 `join()` 等待
3. **如果多个异步操作要组合，就用 `CompletableFuture` 或 `Mono` 来组装任务链，而不是用线程池来嵌套执行。**

------

## ✅ 加餐：我来帮你记住一条黄金法则

> **非阻塞异步客户端：直接用，不加线程池；阻塞客户端：丢进线程池执行。**

## ✅ 正确姿势总结表格

| 客户端类型                   | 异步支持 | 是否用线程池包装 | 推荐做法                                    |
| ---------------------------- | -------- | ---------------- | ------------------------------------------- |
| Lettuce                      | ✅        | ❌ 不要包         | 直接调用 `redisAsync.get().thenApply(...)`  |
| Lattice                      | ✅        | ❌ 不要包         | 使用其返回的 `CompletableFuture` 或异步回调 |
| WebClient                    | ✅        | ❌ 不要包         | 直接用 `.retrieve().bodyToMono(...)`        |
| JDBC                         | ❌        | ✅ 必须包         | 放进线程池执行（否则阻塞主线程）            |
| RestTemplate                 | ❌        | ✅ 必须包         | 放线程池隔离开                              |
| 自己的阻塞代码（如 file IO） | ❌        | ✅                | 也必须放进线程池隔离开                      |





# Lettuce + ComPletableFuture（传统异步） **Lettuce + Reactive（WebFlux）**总览

## 🧨 结论抢先说：

| 客户端类型        | 回包触发线程             | 下游回调执行在哪？                                           | 最终写回响应是谁？                           |
| ----------------- | ------------------------ | ------------------------------------------------------------ | -------------------------------------------- |
| **Future 模式**   | Lettuce 的 Netty IO 线程 | ✅ 仍然由这个线程执行 `.thenAccept()` 等回调                  | ❗也通常由 **Netty IO线程自己** 写回          |
| **Reactive 模式** | Lettuce 的 Netty IO 线程 | ❗只触发 `sink.success(x)`，之后调度线程（非 IO 线程）执行 map/flatMap | ✅ 通常由 **WebFlux Reactor 的调度线程** 写回 |

## 🧠 为什么 Future 模式写回还是 Netty IO 线程？

Lettuce 的 `RedisFuture.get()` / `.thenAccept()` 属于传统的 CompletableFuture：

- #### 它的回调 `.thenAccept()` 没有加 `.async()`，那默认就是**在哪个线程 complete 就在哪执行回调**。

- 而这个 complete 是谁触发的？答：**Lettuce 的 Netty IO 线程在拆包之后，调用了 future.complete()**。

- ### 所以 `.thenAccept()` 就**直接在 Netty IO 线程上执行**。

### 🚨 问题是：

如果你在 `.thenAccept()` 里干了个慢操作（比如 `response.getWriter().write()`），那你就**堵死了 Lettuce 的 IO 线程**，后面的 Redis 回包全挂。

## ✅ 示例代码：Lettuce + Future 模式

```java
RedisFuture<String> future = asyncCommands.get("key");
future.thenAccept(result -> {
    // 🚨 你在 Lettuce 的 Netty IO 线程里！
    response.getWriter().write(result); // ❗这如果慢了，就完了
});
```

## 🧠 而 Reactive 模式是怎么避免这个问题的？

在 Reactive（WebFlux）中，Lettuce 会给你一个 `Mono<String>`，你可以 `.map()`, `.flatMap()`：

```java
Mono<String> mono = reactiveCommands.get("key");
return mono.map(result -> {
    // ✅ 你不在 Netty IO 线程了！而是调度线程
    return result.toUpperCase();
});
```

这是怎么做到的呢？

### ✅ 执行链：

1. Lettuce 的 Netty IO 线程接收到 Redis 回包，触发 `sink.success(result)`；
2. Reactor 注册的 sink 通知 WebFlux；
3. WebFlux 用自己的调度器线程（比如 elastic）去处理 `.map()` 等逻辑；
4. 最终返回的响应由 Netty 的写事件（可能由事件循环或另一个线程）安全写回。

也就是说：

> **Reactive 模式真正解耦了“网络线程” 和 “业务逻辑”线程**，避免了 Future 模式的线程绑死问题。

## 🔍 小结对比：Future 模式的隐患

| 动作                      | Future 模式（Lettuce） | Reactive 模式（WebFlux） |
| ------------------------- | ---------------------- | ------------------------ |
| who call `thenAccept`     | Lettuce Netty IO 线程  | Reactor 调度线程         |
| who write response        | 同一个 IO 线程         | 写事件线程（非业务线程） |
| 慢操作是否卡住 Netty IO？ | ✅ 是，会卡死           | ❌ 否，自动调度分离       |

## ❗最佳实践建议：

在 Servlet 模型 + Future 场景中，**强烈建议你这么做**：

```java
RedisFuture<String> future = async.get("key");
future.thenAcceptAsync(result -> {
    // ✅ 用线程池处理下游
    response.getWriter().write(result);
}, executorService); // 用一个线程池
```

但注意，Servlet 是阻塞模型，**你还得配合 `request.startAsync()` 和 `asyncContext.complete()`**：

```java
AsyncContext ctx = request.startAsync();
future.thenAcceptAsync(result -> {
    ctx.getResponse().getWriter().write(result);
    ctx.complete(); // ✅ 手动告知请求完成
});
```







#  **Lettuce + Reactive（WebFlux）**请求响应链条

```text
Redis Netty I/O 线程做的事情：
- 读到响应 → 解码 → sink.success(data)
- ✅ 这一步是完全线程安全、无锁的非阻塞通知

Reactor 的线程做的事情：
- 接收到 sink.success(data) 触发的事件
- 执行下游 Mono 的 map、flatMap 等操作
- 调用响应写回逻辑，最终 write response

```

```java
@RestController
@RequiredArgsConstructor
public class TestController {

    private final RedisReactiveCommands<String, String> reactiveCommands;

    @GetMapping("/get")
    public Mono<String> get() {
        return reactiveCommands.get("mykey")
                               .map(val -> "结果是：" + val);
    }
}

```

## 🧠 最终精炼版（面试/总结都能用）：

### 🚀 发起请求阶段：

1. **WebFlux 的 Netty I/O 线程** 接收到 HTTP 请求；
2. 控制器执行 `reactive.get("key")`，此时返回 `Mono<String>`；
3. `Mono` 中注册了一个 **sink（MonoSink）**，但并没有立即执行；
4. WebFlux 线程 **立刻释放**，等待下游有结果再继续；
5. `Lettuce` 使用自己内部的 **Netty I/O 线程**，**异步发送 Redis 请求**；
6. 同时注册 **channel 的可读事件回调**，等待 Redis 响应。

------

### 🔁 Redis 响应返回阶段：

1. **Redis 响应回来**，Lettuce 的 Netty I/O 线程触发 `channelRead()`；
2. 解析出结果后，**调用 `sink.success(data)`**；
3. `MonoSink` 通知 Project Reactor 的调度系统（这里是调度线程池，不是netty 的io是他Reactor自己搞的另外一个东西）；
4. Reactor 会 **选择 WebFlux 自己的线程（reactor-http-nio-x）继续执行链条**；
5. 执行 `.map()`、`.flatMap()`、`.doOnNext()` 等；
6. 最终调用响应写回逻辑 → 写入 HTTP response → **flush**。

------



## 哪些线程干那些事儿？

### ✅ 一图看清线程链条（Lettuce + WebFlux）

| 阶段                  | 执行线程是谁？                       | 是否释放原线程？ | 是否阻塞？ |
| --------------------- | ------------------------------------ | ---------------- | ---------- |
| 发起请求              | WebFlux 的 Netty IO 线程             | ✅ 是             | ❌ 否       |
| 发包给 Redis          | Lettuce 自己的 Netty IO 线程         | ✅ 是             | ❌ 否       |
| Redis 回包            | Lettuce 的 Netty IO 线程             | ✅ 是             | ❌ 否       |
| 调用 `sink.success()` | Lettuce IO 线程                      | ✅ 是             | ❌ 否       |
| `.map/.flatMap` 执行  | Reactor 的调度线程（默认是 elastic） | ✅ 是             | ❌ 否       |
| 最终写回 HTTP 响应    | WebFlux 的 Netty IO 写事件线程       | ✅ 是             | ❌ 否       |

### ✅ 所以我们重新划清界限：

| 概念/组件                                                    | 是否 Netty 自带 | 属于谁的线程池？             |
| ------------------------------------------------------------ | --------------- | ---------------------------- |
| `BossGroup`                                                  | ✅ 是            | Netty 的连接处理线程         |
| `WorkerGroup`                                                | ✅ 是            | Netty 的 IO 事件处理线程     |
| `Schedulers.elastic`                                         | ❌ 不是          | **Reactor 自己的线程池**     |
| `Schedulers.parallel`                                        | ❌ 不是          | Reactor 的多核调度线程池     |
| `.subscribeOn(...)` 用于同步客户端，和completabelFuture的supplyasync很像 | ❌ 不是 Netty    | 控制谁来“启动”上游流程的线程 |
| `.publishOn(...)`                                            | ❌ 不是 Netty    | 控制从这里起换线程执行逻辑   |

### 🧨 所以为什么很多人误以为 Netty 也有调度线程？

因为用了 WebFlux，以为一切都 Netty 干的，但真相是：

- **Netty 提供的是高性能通信框架**
- **Reactor 提供的是“声明式数据流 + 线程调度”框架**
- WebFlux 把这俩粘在一起，但职责明确分开



## ✅ 核心理念关键词汇（你可以直接说）：

- **两个 EventLoopGroup：解耦但协作**（Lettuce vs Reactor Netty）
- **非阻塞发起，回调触发，响应式处理**
- **MonoSink 是线程通信的桥梁**
- **下游线程由调度器决定，避免线程错乱或越权写回**



## 两个netty是如何沟通的呢？

* #### Redis 的 Netty 和 WebFlux 的 Netty 是**两个完全独立的线程池系统**，它们之间没有直接通信关系； 但通过 Project Reactor 的 **事件信号机制（sink）**，实现了**线程间的非阻塞通信与桥接**。

### 二者没有物理链接

* #### Lettuce 的 Netty 与 WebFlux 的 Netty 各自维护不同的 TCP 连接和事件循环组。它们之间没有 socket、channel 共享，没有 selector 联通。



### 🔄 二、那为啥你感觉它们好像“配合得很流畅”？

这个“配合”靠的是：

### ✅ **Reactor 的信号机制（`MonoSink.success()`）**：

- 当你在 WebFlux 中调用 `reactive.get("key")`，实际上你拿到的是一个 `Mono<T>`；
- 这个 Mono 的实现里注册了一个 **sink**；
- sink 相当于是一个“线程安全的桥梁”：
  - Lettuce 的 Netty I/O 线程只需要 `sink.success(data)`；
  - 后续的数据处理和响应写回都由 WebFlux 的调度器接手完成。

所以：

> **Redis 的 Netty → 把数据扔给 sink → WebFlux 的线程从 sink 里收到信号 → 决定在哪个线程继续往下走**

------

### 🧪 三、真正的“联系”在哪里？

就在这行代码里：

```java
sink.success(redisResult);
```

这个 `sink.success()`：

- 本质是 Project Reactor 内部的一个 `Publisher` 调用了 `onNext()`；
- 然后 Project Reactor 就会根据上下文（Scheduler）决定“在哪个线程上继续处理”；
- WebFlux 默认使用的是 `reactor-http-nio` 线程池；
- 所以最终你看到的 `map() → flatMap() → response write` 都是 WebFlux 自己的线程执行的。







# Lettuce + ComPletableFuture（传统异步）链条

## 🧪 示例代码：Servlet + Lettuce + CompletableFuture

```java
@WebServlet(urlPatterns = "/get", asyncSupported = true)
public class RedisGetServlet extends HttpServlet {

    private final RedisAsyncCommands<String, String> asyncCommands;

    public RedisGetServlet() {
        RedisClient client = RedisClient.create("redis://localhost");
        StatefulRedisConnection<String, String> connection = client.connect();
        this.asyncCommands = connection.async();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws IOException {
        AsyncContext ctx = req.startAsync(); // ✅ 释放主线程，进入异步模式

        RedisFuture<String> future = asyncCommands.get("mykey"); // ✅ 发起 Redis 异步请求

        future.thenAccept(result -> {
            try {
                resp.getWriter().write("Redis Value: " + result);
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                ctx.complete(); // ✅ 通知容器响应完成
            }
        });
    }
}
```

------

## ✅ 一、整个执行链条（线程视角）：

```
[1] servlet 线程接收到请求（Tomcat 工作线程）
 ↓
[2] 调用 req.startAsync() → 异步上下文创建，Servlet 线程释放 ✅
 ↓
[3] 调用 asyncCommands.get("mykey") → Lettuce 启动 Redis 请求（lettuce 的 Netty I/O 线程）
 ↓
[4] Redis 响应返回 → Lettuce 的 Netty I/O 线程触发 channelRead → RedisFuture.complete(value)
 ↓
[5] 调用 future.thenAccept(...) 中的回调 → 回调默认 **由 lettuce 的 Netty I/O 线程执行**
 ↓
[6] 回调中执行 resp.getWriter().write(...) → 由 lettuce Netty I/O 线程写回响应 ⚠️ 这里很危险！
 ↓
[7] 调用 ctx.complete()，告知 servlet 容器响应结束
```

------

## 🧩 二、每个线程做了什么？谁释放了？谁还在挂着？

| 阶段                     | 线程                               | 操作                              | 是否释放                            |
| ------------------------ | ---------------------------------- | --------------------------------- | ----------------------------------- |
| `doGet()` 起始           | Servlet 工作线程                   | 处理 HTTP 请求                    | ❌ 初始挂着                          |
| `req.startAsync()`       | Servlet 容器                       | 标记异步请求                      | ✅ 立即释放工作线程回到线程池        |
| `asyncCommands.get()`    | 任意线程                           | 发 Redis 命令（底层 Netty）       | ✅ 立即返回 RedisFuture              |
| Lettuce 发包             | Lettuce 的 Netty I/O 线程          | TCP 发 Redis 请求 + 注册回调      | ✅ 发完即走，继续监听 channel        |
| Lettuce 收响应           | Lettuce 的 Netty I/O 线程          | 收 Redis 数据、触发 `.complete()` | ❌ 会继续处理回调逻辑                |
| `.thenAccept()` 回调执行 | ❗ 默认是 Lettuce 的 Netty I/O 线程 | 写 HTTP 响应                      | ⚠️ 危险：不是 servlet 线程，可能越权 |
| ctx.complete()           | 同样在 Lettuce 的线程中            | 告知容器响应完成                  | ✅ 响应结束，销毁 AsyncContext       |

## ⚠️ 三、隐藏的风险点：线程越权写回！

你写的是：

```java
resp.getWriter().write(...);
```

但：

- 这个调用是在 Lettuce 的 Netty I/O 线程中执行的；
- 而 Servlet 容器的 Response 对象通常要求只能由容器托管的工作线程访问；
- 所以在 Tomcat 中**是危险的**，可能抛出警告或行为未定义（轻则 flush 不出去，重则直接崩）

## ✅ 正确做法：手动切回 Servlet 的线程池

```java
AsyncContext ctx = req.startAsync();

RedisFuture<String> future = asyncCommands.get("key");

future.thenAccept(result -> {
    ctx.start(() -> {
        try {
            ctx.getResponse().getWriter().write("Value: " + result);
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            ctx.complete();
        }
    });
});
```

这样做的好处是：

- `ctx.start(Runnable)` 会提交任务到 Servlet 容器的异步线程池（一般是原生线程池或 Tomcat 内部池）；
- 保证对 `resp` 的访问在**合法线程上下文中进行**，防止线程越权问题；
- 同时仍保持整个请求是**非阻塞 + 异步的**。

## ✅ 总结（你可以直接背）：

| 点                               | 内容                                                  |
| -------------------------------- | ----------------------------------------------------- |
| servlet 线程在什么时候释放？     | 调用 `req.startAsync()` 之后立刻释放                  |
| Redis 请求谁发的？               | Lettuce 的 Netty I/O 线程                             |
| Redis 响应谁收的？               | Lettuce 的 Netty I/O 线程                             |
| future.thenAccept 默认在哪执行？ | 仍是 Lettuce 的 Netty I/O 线程                        |
| 是否能直接写 response？          | ❌ 危险，可能线程越权，需 `ctx.start()` 切换回合法线程 |
| 整个链条是阻塞的吗？             | ❌ 全链路非阻塞，但线程切换必须合法                    |





# 堵塞的jedis的客户端另外起了线程吗？？

## 🧨 重点强调：线程谁的？

- **Jedis 没有起线程。**
- 是 **调用 Jedis 的 Servlet 线程** 去执行所有请求、等待和读取。
- 所以 Jedis 的阻塞，**会拖住整个 Servlet 的线程**，也就会卡住整个 HTTP 请求。









# `.subscribeOn(...)` 之于 WebFlux和`CompletableFuture.supplyAsync(..., executor)` 之于 Servlet

* #### ✅ **如果你用的是异步非阻塞客户端（比如 Lettuce Reactive、R2DBC、WebClient）**那你大多数时候 **根本不需要 `.subscribeOn()`** —— 因为这些库**本身就已经是非阻塞 + 回调驱动的**，根本**不会卡你的线程**！

* #### 遇见同步客户端的时候才会要使用的。

>#### `.subscribeOn()` 的目标是把“起点执行”从 Netty IO 线程上摘下来，
>
>####  如果你使用的是异步非阻塞客户端（如 Lettuce Reactive、WebClient、R2DBC），
>
>####  那 `.subscribeOn()` 通常 **完全不需要用**，
>
>####  因为你根本就不会阻塞任何线程，它自己内部就已经是回调驱动的异步链路了。

## 👇 用你熟悉的语言来类比一下

| 处理模型             | 异步启动方式                         | 控制在哪个线程执行起点              | 避免什么问题              |
| -------------------- | ------------------------------------ | ----------------------------------- | ------------------------- |
| **Servlet + Future** | `supplyAsync(..., executor)`         | ✅ 谁执行起点逻辑（发 Redis，请 DB） | 避免阻塞 Servlet 主线程   |
| **WebFlux + Mono**   | `.subscribeOn(Schedulers.elastic())` | ✅ 谁启动数据流逻辑                  | 避免阻塞 Netty 的 IO 线程 |

## ✅ 关键点 1：为什么都需要“异步起点”？

因为你发起请求（比如去 Redis、MySQL、外部 API）时，如果用的是**同步阻塞 API**（如 JDBC、Jedis），那：

- 在 Servlet 模型下：你卡住的是 Tomcat 的工作线程
- 在 WebFlux 模型下：你卡住的是 Netty 的 IO 线程（`reactor-http-nio`）

这两者都很昂贵！卡死几个线程就是雪崩。

所以我们必须**用线程池“包裹起点”**，把这种阻塞操作丢给别的线程干，主线程只要继续等回调就行。

## ✅ 示例类比（完整对比）

### Servlet + CompletableFuture

```java
AsyncContext ctx = request.startAsync();
CompletableFuture.supplyAsync(() -> {
    return jdbc.queryForObject(...);  // 🚨 阻塞操作交给线程池
}, executor).thenAccept(result -> {
    ctx.getResponse().getWriter().write(result);
    ctx.complete();  // ✅ 写完响应，手动结束
});
```

### WebFlux + subscribeOn

```java
@GetMapping("/user")
public Mono<String> getUser() {
    return Mono.fromCallable(() -> {
        return jdbc.queryForObject(...); // 🚨 阻塞操作
    }).subscribeOn(Schedulers.boundedElastic()) // ✅ 控制谁来执行 Callable
      .map(user -> user.getName());
}
```

## ✅ 总结一句话：

> `.subscribeOn()` 就是 WebFlux 世界中的 `supplyAsync(..., threadPool)`，
>  用来保证你的慢操作别特么拖死 IO 线程，
>  是你做非阻塞响应式编程必须掌握的一招。





# 无论是 Servlet 还是 WebFlux，遇到同步客户端都会堵线程。

## 🔚 最后的升华总结

> #### ✅ 无论是 Servlet 还是 WebFlux，遇到同步客户端都会堵线程。
>
> ####  ✅ 解决的关键是：**把耗时任务丢线程池，释放主线程资源**。
>
> ####  ✅ 所不同的是：Servlet 得你手动释放，WebFlux 提供了 `.subscribeOn()` 做线程隔离。
>
> ####  ✅ 异步客户端（如 Lettuce Reactive、WebClient、R2DBC）不需要 `.subscribeOn()`，它们天然非阻塞。



## ✅ 统一总结：Servlet & WebFlux 都能处理“同步客户端”带来的阻塞问题，本质是 **“线程隔离 + 异步回调”**

| 特性                             | Servlet 模型                       | WebFlux 模型                            |
| -------------------------------- | ---------------------------------- | --------------------------------------- |
| 默认模型                         | 同步阻塞式（基于 Tomcat 工作线程） | 异步响应式（基于 Netty IO + Reactor）   |
| 使用同步客户端（如 JDBC、Jedis） | 🚨 会阻塞主线程                     | 🚨 会阻塞 Netty IO 线程                  |
| 释放当前线程的方式               | ✅ `startAsync() + supplyAsync()`   | ✅ `.subscribeOn(Schedulers.elastic())`  |
| 后续逻辑回调线程                 | 你手动指定线程池                   | Reactor 框架用调度器线程池（elastic等） |
| 写回响应                         | 你手动调用 `response.getWriter()`  | Reactor 封装的 `writeWith()`            |
| 写完后需标记完成                 | ✅ `ctx.complete()`                 | 自动完成（除非你写底层 handler）        |

## 🧠 简化一句话记忆：

> **Servlet 是命令式模型，得你手动用 `startAsync()` 把线程释放出来；
>  WebFlux 是声明式模型，用 `.subscribeOn(...)` 自动切线程托管阻塞操作。**

## 🧨 那异步客户端为什么不需要这些操作？

因为它本身就已经做到了：

- ✅ 发请求时就释放线程（比如 Lettuce 的 `get()` 直接返回 Mono）
- ✅ 响应回来通过内部 Netty IO 线程完成回调（触发 `sink.success(...)`）
- ✅ 后续 `.map()` 之类的操作由 Reactor 调度器安排，不会卡任何主线程

## ✅ 经典对照代码模板（拿去就能用）：

### Servlet + CompletableFuture + 同步阻塞客户端

```java
AsyncContext ctx = request.startAsync();

CompletableFuture.supplyAsync(() -> {
    return jdbc.queryForObject(...); // ✅ 阻塞操作交给线程池
}, threadPool).thenAccept(result -> {
    ctx.getResponse().getWriter().write(result);
    ctx.complete(); // ✅ 标记完成
});
```

------

### WebFlux + subscribeOn + 同步阻塞客户端

```java
@GetMapping("/user")
public Mono<String> getUser() {
    return Mono.fromCallable(() -> {
        return jdbc.queryForObject(...); // ✅ 依旧是同步阻塞操作
    }).subscribeOn(Schedulers.boundedElastic()) // ✅ 释放 Netty 线程
      .map(user -> user.getName());
}
```

------

### WebFlux + 异步非阻塞客户端（推荐方式）

```java
@GetMapping("/redis")
public Mono<String> getFromRedis() {
    return reactiveRedis.get("user:1") // ✅ 异步非阻塞
        .map(val -> val.toUpperCase()); // 默认自动调度
}
```

























































