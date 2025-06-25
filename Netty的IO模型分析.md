# 比较好的两篇文章

（1）https://netty.io/wiki/related-articles.html

* #### netty的核心三大件

  * #### 改进的缓冲区的：channelBuffer

  * #### 通用的异步IO api：Netty 有一个通用的异步 I/O 接口，称为 [`Channel`](http://www.jboss.org/file-access/default/members/netty/freezone/api/3.1/org/jboss/netty/channel/Channel.html) 抽象出点对点通信所需的所有操作。——不仅抽象了 tcp和udp，还抽象了 NIO，OIO的操作。

  * #### 基于拦截器链的事件驱动模型

（2）https://mp.weixin.qq.com/s?__biz=Mzg2MzU3Mjc3Ng==&mid=2247483737&idx=1&sn=7ef3afbb54289c6e839eed724bb8a9d6&chksm=ce77c71ef9004e08e3d164561e3a2708fc210c05408fa41f7fe338d8e85f39c1ad57519b614e&scene=178&cur_album_id=2217816582418956300#rd ——聊聊Netty那些事儿之从内核角度看IO模型



# Netty 的 `Channel` 到底抽象了哪些东西？

## ✅ 一、原文核心意思先总结一句话

> Netty 的 `Channel` 是一个**统一的异步通信抽象接口**，**不是只抽象 TCP/UDP 协议**，而是**同时抽象了通信协议 + IO 模型（NIO/OIO/AIO）**。
>  **你写一套业务逻辑，不用关心底层是 NIO 还是 OIO，是 TCP 还是 UDP，它都能跑，只需要换个实现类。**

------

## ✅ 二、你问的两个“抽象层级”到底是哪一层？

| 抽象目标                            | 你问的核心点                         | 是否被 Netty Channel 抽象？ | 举例                                              |
| ----------------------------------- | ------------------------------------ | --------------------------- | ------------------------------------------------- |
| **通信协议层（TCP/UDP/本地/串口）** | 是不是统一了所有协议？               | ✅ 是的                      | TCP → NioSocketChannel / UDP → NioDatagramChannel |
| **IO 模型层（NIO/OIO/AIO）**        | 是不是统一了 Java 不同 IO 编程模型？ | ✅ 是的                      | NIO → `...nio.*`；OIO → `...oio.*`                |



------

## ✅ 三、那为啥 Netty 还需要多个 Channel 实现类？

### 因为 Channel 是一个**接口（统一抽象）**，但每种传输协议和 I/O 模型有不同底层实现细节。

#### 1. 抽象的是行为接口，不等于一行代码实现全部。

举个例子来说明你现在困惑的点：

```java
public interface Channel {
    void write(Object msg);
    void close();
    boolean isActive();
    ...
}
```

这是 Netty 定义的接口。

> 它把“**对端通信通道应当有哪些行为**”抽象出来了，但不代表底层实现就一套代码能跑所有协议。

### ✅ 所以才有这些不同实现类：

| 类名                 | 代表协议                      | I/O 模型      |
| -------------------- | ----------------------------- | ------------- |
| `NioSocketChannel`   | TCP                           | NIO           |
| `OioSocketChannel`   | TCP                           | OIO（阻塞式） |
| `NioDatagramChannel` | UDP                           | NIO           |
| `OioDatagramChannel` | UDP                           | OIO           |
| `LocalChannel`       | 本地内存通道（内存 RPC 通信） | NIO           |

**它们都实现了同一个 `Channel` 接口**，所以你写的业务逻辑（Handler、Pipeline、业务逻辑）**不需要改**，只要换个 Channel 实现就可以支持不同协议或模型。

## ✅ 四、Netty 的强大就在于：抽象在上层，变化在底层

比如你最开始写了一个 TCP NIO 的服务：

```
bootstrap.channel(NioSocketChannel.class);
```

现在你想换成阻塞式 IO（例如测试时为了兼容老系统）：

```
bootstrap.channel(OioSocketChannel.class);
```

再想换成 UDP：

```
bootstrap.channel(NioDatagramChannel.class);
```

你的业务逻辑完全不用改，**只改了一行注册的 Channel 实现类**。

这就是原文里说的：

> "Switching from one transport to the other usually takes just a couple lines of changes such as choosing a different ChannelFactory implementation."

## ✅ 五、为什么 Netty 叫它“Universal I/O API”？

因为你不用再在这些东西之间做艰难选择了：

- 是用 `java.net.Socket`（OIO）？
- 还是用 `java.nio.channels.SocketChannel`（NIO）？
- 还是等 Java NIO.2（AIO）？

Netty 抽象了统一的 `Channel` 接口和 `Pipeline` 机制，让你专注在“我要处理什么数据”，而不是“我用哪个库接数据”。

你只需要换个 Channel 类型，业务逻辑 Handler 全部复用。

## ✅ 六、总结：你最关心的两个问题明确回答

| 你问的内容                                  | 答案                                                         |
| ------------------------------------------- | ------------------------------------------------------------ |
| `Channel` 是抽象 TCP/UDP 还是抽象 NIO/OIO？ | ✅ 都抽象了！Channel 是“通信通道”的抽象，无论底层是 TCP 还是 UDP、NIO 还是 OIO，它都用统一接口。 |
| 那为啥还有那么多 Channel 类？不是抽象了吗？ | ✅ 因为不同协议 + 不同 IO 模型的底层细节不一样，所以 Netty 用多个实现类封装它们。但上层逻辑对开发者统一。 |



# Netty的事件驱动模型到底是怎么样的？？那么多名词到底是什么意思？

## 一、这些东西到底是**啥**

| 名词                | 是什么？                               | 类型              | 是不是线程？ | 职责                                      |
| ------------------- | -------------------------------------- | ----------------- | ------------ | ----------------------------------------- |
| **Reactor 模式**    | 一种**网络框架的设计模式**             | 抽象概念          | ❌            | 统一监听所有事件，然后分发给不同处理逻辑  |
| **Selector**        | JDK 原生的 I/O 复用组件                | Java 类           | ❌            | 监视 Channel 的事件（读/写/连接）         |
| **EventLoop**       | Netty 中的**线程抽象**，死循环处理事件 | 线程（+任务队列） | ✅ 是个线程   | 绑定多个 Channel，串行处理事件            |
| **EventLoopGroup**  | 管理多个 EventLoop 的集合              | 线程池            | ✅（多个）    | 管理多个线程，比如 WorkerGroup、BossGroup |
| **Channel**         | 每个客户端连接的抽象                   | Java 对象         | ❌            | 代表一个连接（如 TCP socket）             |
| **ChannelPipeline** | Channel 上的一条处理链                 | Java 对象         | ❌            | 承载多个 Handler，串行处理事件            |
| **ChannelHandler**  | 具体的逻辑代码块（类似拦截器）         | Java 类           | ❌            | 编解码、业务处理、异常处理                |

## 二、所以每个东西的职责总结如下

| 组件                | 职责                                    | 举例类名                       |
| ------------------- | --------------------------------------- | ------------------------------ |
| **Reactor 模式**    | 架构理念：用少量线程管理大量连接        | 无具体类，Netty整体实现了它    |
| **Selector**        | 用于检测连接的状态，如是否可读、可写等  | `java.nio.channels.Selector`   |
| **EventLoop**       | 单个线程，死循环处理绑定 Channel 的事件 | `NioEventLoop`                 |
| **EventLoopGroup**  | 一组 EventLoop，即一个线程池            | `NioEventLoopGroup`            |
| **BossGroup**       | 监听新连接，创建 Channel                | `new NioEventLoopGroup(1)`     |
| **WorkerGroup**     | 处理读写事件                            | `new NioEventLoopGroup(n)`     |
| **Channel**         | 表示一个连接（TCP、UDP）                | `NioSocketChannel`             |
| **ChannelPipeline** | 每个 Channel 独有的一条“处理器链”       | `DefaultChannelPipeline`       |
| **Handler**         | 编解码、业务处理等逻辑模块              | `ChannelInboundHandlerAdapter` |

## 三、常见的结论

* #### **Reactor 模式是一种基于事件驱动的并发模型，用于处理多个 IO 事件的复用，核心思想是：一个（或少量）线程监听所有 IO 事件，根据事件类型分发到对应的业务处理逻辑。**

  * #### Reactor模式的实现 就是一个`eventLoop + 其中包含的selector`

* #### Selector 是 Java NIO 提供的类：`java.nio.channels.Selector`。

  * #### 它本质上是一个**I/O 多路复用组件**，可以监听多个 Channel 上的事件（读、写、连接、关闭等）。

  * #### 它不能自己跑，是**挂在某个线程（EventLoop）中由线程驱动的**

  * #### `Selector` 的核心机制在操作系统中叫作 **I/O 多路复用**。Java NIO 底层使用 JNI 调用了操作系统的这些原生能力，这就是 NIO 的高性能来源之一。（Netty中的selector封装的是 Java NIO中的 能力）在不同 OS 下使用不同实现：

    - #### Linux → `epoll`

    - #### Windows → `select`

    - #### macOS → `kqueue`

  * #### Selector 来监听 Channel 上的事件。所谓的监听不是写代码主动去问。而是操作系统OS，精准的告诉你，哪些Channel触发了事件，然后返回Selectedkeys，让eventLoop去做处理的。

  * #### Selector 并不是监听“Channel 本身”，而是监听“Channel 对象上注册的感兴趣事件”。

  * #### WorkerGroup 中的 EventLoop 的 selector其不监听有无连接，其监听的是“某个 Channel 是否有 **读事件 / 写事件 / 异常事件**”。OP_READ（可读）， OP_WRITE（可写），OP_CLOSE（关闭）

  * #### **Selector 会明确告诉你：“是哪个 Channel 有了哪个事件”**，返回对应的selectionKeys，里面包含了Channel的信息。

  * ```markdown
    BossGroup (1个线程)
    │
    └── EventLoop-Boss
        └── Selector-Boss （只监听 ServerSocketChannel 的 accept）
        └── accept 到连接后 → 创建 NioSocketChannel
                              → 注册到 WorkerGroup 中某个 EventLoop
    
    WorkerGroup (多个线程)
    │
    ├── EventLoop-1
    │   └── Selector-1 （监听多个 Channel 的读/写事件）
    │   └── 有事件 → 调用该 Channel 的 Pipeline
    │              → 逐个执行 Handler
    │
    ├── EventLoop-2
    │   └── Selector-2 （监听其它 Channel 的事件）
    ...
    
    ```

* #### EventLoop的轮询代码如下

  * ```java
    while (!thread.isInterrupted()) {
        int readyCount = selector.select(); // 阻塞等待：有就绪事件才往下走
    
        if (readyCount == 0) continue; // 没有事件，继续轮询
    
        Set<SelectionKey> selectedKeys = selector.selectedKeys();
    
        for (SelectionKey key : selectedKeys) {
            if (key.isAcceptable()) {
                // 有新连接，走 accept 逻辑
            }
            if (key.isReadable()) {
                // 有数据读，调用 handler.channelRead(...)
            }
            if (key.isWritable()) {
                // 可以写数据，flush 或 send
            }
            ...
        }
    
        selectedKeys.clear(); // 清除本轮已处理的事件
    }
    
    ```

  * #### 「轮询」就是指 EventLoop 线程中的这段死循环。指代的是 工作线程的轮询，即处理事件的代理是循环的。而不是说 `Selector`的不断在问这个Channel，不是这样的。哪些channel触发，是os精准告诉你的。

  * #### selector.select() 不是什么“扫描”，而是 OS 通知：

    * #### 它不会每次去遍历所有 Channel

    * #### 它是调用 OS 的 `epoll_wait()`（Linux）等函数，让 OS 通知你有哪个 FD（Channel）有事件发生

    * #### 你得到的是**“精准触发结果集”**，不是靠你自己查出来的

    * #### selectedKeys 中的每个 SelectionKey 是一条“事件记录”

  * #### 所谓 Netty 中 EventLoop 的轮询，指的是它在自己线程内不断调用 Selector 的 `select()` 方法（阻塞等待 I/O 事件），一旦 OS 告诉你“某些 Channel 上的事件就绪了”，Selector 就会返回一批 `SelectionKey`，你再一个个去执行这批事件对应的操作。



* #### 所有的 I/O 线程，无论是 Boss 线程还是 Worker 线程，都是 `EventLoop` 类型。

* #### `BossGroup` 本质上就是一个 `EventLoopGroup`，你通常只给它一个线程。

  * #### `BossGroup `只有一个线程` EventLoop` ，默认是只有一个`EventLoopGroup`

  * #### BossGroup 只负责“接收连接”，真正的数据 I/O 是 WorkerGroup 干的。

  * ```text
    1. Boss 线程发现有新连接 → ServerSocketChannel accept()
    2. 创建新的 SocketChannel（代表这个连接）
    3. 选一个 Worker EventLoop，把这个 Channel 注册进去
    4. 从此以后，这个连接的所有 I/O 都由这个 Worker EventLoop 处理
    ```

  * #### BossGroup 默认只有一个 EventLoop → 这个线程的 Selector 只监听 accept事件 → accept 之后创建 Channel → 分配给 WorkerGroup → 后续 I/O 全交给对应的 WorkerGroup 中的某个 EventLoop。

  * ### ✅ 你说的这句话翻译成流程是：

    1. **BossGroup 默认只有一个线程（一个 EventLoop）**
       - ✅ 正确。Netty 默认 `new NioEventLoopGroup(1)` 创建 BossGroup，只有一个线程。
       - 这个线程背后绑定了一个 `Selector`，只注册 `ServerSocketChannel`。
    2. **这个 EventLoop 的 Selector 只监听 `accept` 事件**
       - ✅ 正确。Boss 的职责非常单一：**监听新连接的到来**。
       - 也就是在 `ServerSocketChannel` 上注册的是：`SelectionKey.OP_ACCEPT`。
    3. **有新连接进来，触发 accept 后，就会创建一个 SocketChannel**
       - ✅ 正确。底层其实是操作系统返回一个已经建立的连接（`SocketChannel`），Netty 封装成 `NioSocketChannel`。
       - 并不会新建线程，而是构造出一个 Netty 的 `Channel` 对象。
    4. **然后这个新建的 Channel 会注册给 WorkerGroup 中的某个 EventLoop**
       - ✅ 正确。这是 Netty 的经典分工：
         - Boss 只监听 accept，建立连接后就**立刻把 Channel 丢给 WorkerGroup 管**。
         - WorkerGroup 中是 N 多个 EventLoop（每个一个 Selector）。
    5. **后续的 I/O 操作就由 WorkerGroup 中的 EventLoop 来处理**
       - ✅ 正确。一个 Channel 一旦注册到了某个 Worker 的 Selector：
         - 这个 Worker 的 Selector 会监听它的 `read/write` 等事件（OP_READ、OP_WRITE）。
         - 后续的请求处理逻辑、业务逻辑、pipeline 链路全在这个 EventLoop 上跑。

* #### 每个 EventLoop 线程有自己的 Selector。

  * ```text
    bossGroup（EventLoopGroup）
    └── EventLoop 线程（比如 Boss-1）
        └── Selector（负责监听 ServerSocketChannel 的 accept 事件）
    
    ```

* #### 一个 Worker 线程绑定多个 Channel，数据在其 Channel 的 Pipeline 上链式处理，

  * #### 一个 `EventLoop`（线程）可以绑定多个 Channel。（一般是workerGroup上的额 EventLoop的）

  * #### 这些 Channel 共享这个线程，但操作是串行进行的（无锁）

  * #### 每个 Channel 都有自己的 `ChannelPipeline`

  * ##### 所有 I/O 事件（channelRead、write、exceptionCaught）都会流经这个 Pipeline，并依次调用每个 Handler。这个就是所谓的拦截器形式的链式处理。感觉是责任链的模式

* ## ✅ 最终结构总结图：

  ```markdown
  客户端连接
      ↓
  BossGroup（1个 EventLoop线程）
      ↓          （包含 Selector）
  accept → 生成 Channel → 分配给
      ↓
  WorkerGroup（多个 EventLoop线程）
      ↓          （每个线程有一个 Selector）
  每个 EventLoop 管多个 Channel
      ↓
  Channel 内部 → ChannelPipeline → Handler 链式处理事件
  ```

## ✅ 一句话总结你问的这些所有概念：

> #### 每个 EventLoop 是一个线程，绑定一个 Selector，负责监听多个 Channel 的事件；BossGroup 中的 EventLoop 只监听连接事件（accept），WorkerGroup 中的 EventLoop 监听并处理数据事件（read/write），数据在每个 Channel 的 Pipeline 中通过多个 Handler 被串行处理。





# ✅ Netty 工作流程：架构总览 + 全概念融合（面试笔记版）

## 补充：Netty 抽象的价值

| Java 原生                          | Netty 提供的统一抽象                        |
| ---------------------------------- | ------------------------------------------- |
| Socket / DatagramSocket 无公共接口 | `Channel` 统一抽象所有协议                  |
| OIO / NIO / AIO 不兼容             | `EventLoop` 屏蔽所有 IO 模型细节            |
| TCP/UDP 切换困难                   | 改一个 Channel 实现类即可                   |
| Buffer 难用，容易出错              | `ByteBuf` 提供索引、池化、扩容机制          |
| 编写状态机复杂                     | `Pipeline + Handler` 实现事件级可组合处理链 |

## 面试核心记忆口诀（可口语化表达）

> “Netty 的核心在于将 I/O 事件驱动模型抽象为统一的 `Channel` 接口，通过 `EventLoop` + `Selector` 实现 Reactor 模式，主线程（Boss）只接连接，工作线程（Worker）才处理 I/O，每个连接挂一条 `Pipeline`，事件从 Handler 里一路流转，最终非阻塞地写回客户端。”



## 一、Netty 是什么？

**Netty 是一个基于 NIO 的高性能异步事件驱动通信框架**，通过抽象统一的 Channel 接口和事件处理模型，屏蔽了底层的多种传输协议（TCP/UDP）和 I/O 编程模型（OIO/NIO），实现了高度可移植、高吞吐、低延迟的网络编程框架。

## 二、Netty 的三大核心抽象（原始设计理念）

| 抽象名          | 定义                              | 对应类                                       |
| --------------- | --------------------------------- | -------------------------------------------- |
| **Buffer**      | 用于存储读/写数据的内存容器       | `ByteBuf`                                    |
| **Channel**     | 表示一次连接或通信通道（TCP/UDP） | `NioSocketChannel` 等                        |
| **Event Model** | 基于事件的执行机制                | 包含：Selector、EventLoop、Pipeline、Handler |

## 三、Netty 工作流程概览（流程图级别理解）

```markdown
客户端发起连接
    ↓
BossGroup（EventLoopGroup）
    ↓
EventLoop（线程） ← 持有 Selector（监听 accept 事件）
    ↓
Selector 检测到新连接 → 触发 accept
    ↓
创建 Channel（NioSocketChannel）→ 分配给 WorkerGroup 中某个 EventLoop
    ↓
WorkerGroup（EventLoopGroup）
    ↓
EventLoop（线程） ← 持有 Selector（监听 read/write 事件）
    ↓
Selector 返回已就绪的 Channel + 事件类型（读/写）
    ↓
EventLoop 拿到 SelectionKey → 调用该 Channel 的 Pipeline
    ↓
事件在 ChannelPipeline 中向下传播
    ↓
依次调用 ChannelHandler（编解码 / 业务逻辑 / 响应回写）
```

## 四、Netty 核心组件职责总表（你必须记得的）

| 名称                | 是什么                     | 是否线程    | 职责说明                                      |
| ------------------- | -------------------------- | ----------- | --------------------------------------------- |
| **EventLoop**       | Netty 中的线程抽象         | ✅ 是线程    | 死循环运行：监听 Selector，分发事件，执行任务 |
| **Selector**        | Java NIO 的多路复用器      | ❌ 是 JDK 类 | 通知某个 Channel 上的事件就绪                 |
| **EventLoopGroup**  | EventLoop 的集合（线程池） | ❌ 是容器    | BossGroup 或 WorkerGroup 的本质               |
| **Channel**         | 表示客户端连接             | ❌ 是对象    | 封装 TCP/UDP 通信，挂载 Pipeline              |
| **ChannelPipeline** | Channel 的处理链           | ❌ 是容器    | 事件在里面流转并传播给多个 Handler            |
| **ChannelHandler**  | 逻辑处理器                 | ❌ 是类      | 处理事件，如解码器、业务处理器、异常处理器    |
| **ByteBuf**         | 数据缓存容器               | ❌           | 替代 ByteBuffer，更灵活支持读写索引           |

## 五、事件驱动模型核心思想（Reactor 模式）

Netty 基于**Reactor 模式**构建：

> 由一个 Selector 持续监听多个 Channel 上的事件，通过少量线程高效调度大量连接，事件就绪后触发处理链（Pipeline）逐步处理。

其底层由操作系统的 `epoll/kqueue/select` 提供多路复用能力，避免传统 OIO 的一线程一连接模型。

## 六、BossGroup vs WorkerGroup 职责区别

| BossGroup                                 | WorkerGroup                               |
| ----------------------------------------- | ----------------------------------------- |
| 负责监听端口，接收客户端连接（accept）    | 负责具体读写事件（read/write）            |
| 每个 EventLoop 只监听 ServerSocketChannel | 每个 EventLoop 监听多个 SocketChannel     |
| 创建连接后将 Channel 分配给 WorkerGroup   | 被分配 Channel 后，处理其整个生命周期事件 |

## 七、完整事件链触发流程（以“客户端发来请求”为例）

```markdown
1. 客户端连接 → BossGroup 的 Selector 检测到 accept
2. EventLoop 调用 accept，生成 NioSocketChannel
3. 分配到 WorkerGroup 中一个 EventLoop 线程
4. Worker EventLoop 将 Channel 注册到自己的 Selector
5. 后续客户端发来数据 → Selector 检测到 OP_READ
6. EventLoop 拿到 SelectionKey（知道哪个 Channel 可读）
7. 调用该 Channel 的 Pipeline.fireChannelRead(ByteBuf)
8. 在 Pipeline 中传递 → 一个个 Handler 被触发
   → 解码器 → 业务处理器 → 编码器 → 写出响应
```

























