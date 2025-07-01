# LightRpc
从0到1的手写rpc框架，基于netty实现了定制应用层协议，加入了序列化，压缩。还考虑到了使用zookeeper来实现服务发现和注册，以及对服务的负载均衡实现。最后针对于服务治理模块，加入了超时和重试。最后加上了配置和spi功能，只要添加上具体的实现类，指定配置就能生效。

后续考虑拓展加入集成Spring注解以及自动配置。



# 如何部署？

* （1）将项目克隆到本地，使用`IDEA`打开。
* （2）安装好 `zookeeper`，并且将zookeeper的配置，写入到项目`rpc-core`模块中的`rpc-properties`中。
* （3）首先运行模块`rpc-demo-server`中的启动类`ServerDemo`
* （4）其次运行模块`rpc-demo-client`中的启动类`ClientDemo`
* （5）观察输出日志，如果没有出现任何报错且客户端能收到服务端的消息，那么就是通了。



# 自定义协议格式

| 字段       | 长度 | 类型                        |
| ---------- | ---- | --------------------------- |
| 魔数       | 4B   | 固定为 0xCAFEBABE           |
| 版本       | 1B   | 固定为 1                    |
| 消息类型   | 1B   | 请求/响应等                 |
| 序列化类型 | 1B   | JDK/Kryo 等                 |
| 压缩类型   | 1B   | gzip/snappy                 |
| 请求 ID    | 8B   | long                        |
| 数据长度   | 4B   | int（data 字节数组长度）    |
| 数据体     | N    | 压缩 + 序列化之后的字节数组 |





# 如何从0到1写出rpc框架？思路和步骤？

* 下面可以参考一个小故事。（参考思路，具体的实现可以自行拓展）
* 先将具体的数据流跑通，再去做对应的拓展。



| 篇章  | 内容主题                                   | 目标                  |
| ----- | ------------------------------------------ | --------------------- |
| 第1章 | 起点：为啥你非得写 RPC？                   | 明确动机与背景        |
| 第2章 | 通信第一步：为什么选择 Netty？             | 构建 TCP 通信基础     |
| 第3章 | 通信协议：我们得自己造一个协议             | 自定义协议 + 粘包拆包 |
| 第4章 | 对象不能飞线：序列化机制入场               | 序列化选择与封装      |
| 第5章 | 谁来触发网络调用：动态代理机制             | 拦截方法 → 发请求     |
| 第6章 | 服务在哪？注册中心上场                     | ZK/Nacos 注册发现     |
| 第7章 | 拦不住的请求洪流：异步、线程池、心跳、重连 | 服务治理与并发控制    |
| 第8章 | 模块重构 + SPI 插件机制 + 扩展设计         | 变成架构级作品        |



# 第1章：起点 · 你为什么要自己手写 RPC 框架？

你是一个程序员，

某天你在项目里看到了这种代码：

```java
User user = userServiceClient.getUserById(1);
```

调用一个服务，就像调用本地方法一样——这太优雅了！

但你突然意识到：

> 明明这是跨网络调用，背后一定藏着很复杂的事情。那我要是能**手写出这样的东西**，我他妈也太牛逼了吧？

于是你打开 IDEA，新建了一个模块：`my-rpc-framework`，开始写这个「看似简单，实则复杂」的框架。

------

### ✅ 你明确了目标：

#### ✅ 框架目标（MVP）

- 拥有“像调用本地方法一样”调用远程服务的能力
- 不依赖 Spring、不使用 Dubbo/gRPC，**完全自己实现**
- 支持多个服务实例，支持注册发现
- 最好还能支持异步、心跳、序列化扩展等

------

### ✅ 接下来你盘了一下要做的模块：

| 模块名称                       | 职责（责任）             | 没它会怎样？             |
| ------------------------------ | ------------------------ | ------------------------ |
| 通信模块（Netty）              | 建立 TCP 通信            | 没它你根本发不出请求     |
| 协议设计（Header+Length+Body） | 拆包粘包+识别消息类型    | 没它你收不全一个请求     |
| 序列化模块（Kryo等）           | 对象↔字节流              | 没它你没法传 Java 对象   |
| 代理模块（JDK Proxy）          | 拦截调用 → 发请求        | 没它你得手动 new Request |
| 注册中心（ZK/Nacos）           | 找到服务地址             | 没它你不知道服务在哪     |
| 并发控制模块                   | 等待返回/超时/断线重连   | 没它你连不上就挂死       |
| SPI 插件机制                   | 解耦框架核心与可插拔组件 | 没它你框架变得不灵活     |

你心想：“卧槽，这他妈比我预期复杂多了……但看起来每个模块都挺有意思的。”

于是你决定按顺序，从最底层“通信”做起。



# 第2章：通信第一步，为什么必须是 Netty？

### 🧠 场景：你面对第一个关键抉择

你想实现“客户端调用接口 → 服务端返回结果”这一行为，那肯定要网络通信。

你最开始想的可能是：

- **用 HTTP 吧？Spring Boot 很方便啊**
- **要不直接用 Java Socket 试试？**

但很快你发现这些方案都不合适。

------

### ❌ 为什么不能用 HTTP / Socket？

| 技术选型             | 问题                                          |
| -------------------- | --------------------------------------------- |
| `HttpURLConnection`  | 太原始，不能控制请求头、协议结构，阻塞严重    |
| Servlet + SpringBoot | 走 Servlet 模型，和你的目标“自己写框架”冲突   |
| Java Socket          | 要手动写 Selector、Buffer，太繁琐，没连接管理 |
| gRPC / Feign         | 是别人造好的轮子，不适合“自己动手造框架”      |

于是你找到了 Netty：

> “一个异步事件驱动的高性能网络框架，是 Java 网络通信的天花板。”

### ✅ 为什么必须是 Netty？

| 优势                                       | 说明                                |
| ------------------------------------------ | ----------------------------------- |
| 异步非阻塞 NIO 封装好                      | 不用操心 Selector、Channel 注册流程 |
| 有高性能内存池                             | ByteBuf 池化，避免频繁 GC           |
| 支持 TCP / UDP / HTTP / WebSocket          | 灵活强大，可玩性高                  |
| 内置编解码器、心跳、重连机制               | RPC 框架能直接复用                  |
| 大量成熟应用案例（Dubbo、gRPC 底层都用它） | 经受住考验的工业级框架              |

### ✅ 所以你开始写下人生第一个 Netty 服务端

```java
public class RpcNettyServer {
    public static void main(String[] args) throws Exception {
        EventLoopGroup boss = new NioEventLoopGroup(1); // 接收连接
        EventLoopGroup worker = new NioEventLoopGroup(); // 处理 IO

        ServerBootstrap bootstrap = new ServerBootstrap();
        bootstrap.group(boss, worker)
                 .channel(NioServerSocketChannel.class)
                 .childHandler(new ChannelInitializer<SocketChannel>() {
                     protected void initChannel(SocketChannel ch) {
                         ch.pipeline().addLast(new ServerBusinessHandler());
                     }
                 });

        ChannelFuture future = bootstrap.bind(9000).sync();
        System.out.println("RPC 服务启动成功！");
        future.channel().closeFuture().sync();
    }
}
```

处理器：

```java
public class ServerBusinessHandler extends ChannelInboundHandlerAdapter {
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        System.out.println("服务端收到消息：" + msg);
        ctx.writeAndFlush("服务端响应");
    }
}
```

------

### ✅ 你又写了个 Client：

```java
public class RpcNettyClient {
    public static void main(String[] args) throws Exception {
        EventLoopGroup group = new NioEventLoopGroup();

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                 .channel(NioSocketChannel.class)
                 .handler(new ChannelInitializer<SocketChannel>() {
                     protected void initChannel(SocketChannel ch) {
                         ch.pipeline().addLast(new ClientBusinessHandler());
                     }
                 });

        ChannelFuture future = bootstrap.connect("localhost", 9000).sync();
        future.channel().writeAndFlush("你好RPC！");
        future.channel().closeFuture().sync();
    }
}
```

------

### ✅ 成功！你打通了 TCP 连接

你激动得不行：这不就是调用链的起点吗？你感受到：

> RPC 不再神秘，只是我把方法调用转换成“网络发包 → 服务器收包 → 返回结果”。

但很快你意识到：

> “我传的是字符串，那我怎么传 Java 对象？我怎么知道这条数据完整了吗？”

你直觉地知道，**下一步你得设计一个属于你自己的协议**。

------

### ✅ 本章总结

| 你解决了什么？                      | 方法                  |
| ----------------------------------- | --------------------- |
| 如何构建高性能 TCP 通信             | 用 Netty              |
| 如何分清责任线程池（Boss / Worker） | 用 `group()` 配置     |
| 如何让数据在两端流动                | `ChannelHandler` 读写 |
| 初步拥有“服务调用”的基础能力        | 发包/收包已经打通     |



#  第3章：通信协议 · 不再发字符串，我要设计自己的协议！你已经用 Netty 搭好了 TCP 通信的桥梁。

但你心里明白一个事儿：

> “发字符串只是玩具，**RPC 是发 Java 对象的，还是带有明确结构、可反序列化的那种**。”

你也知道：

- TCP 是 **字节流协议，没有边界**；
- 你发一条完整消息，服务端可能收到一半（拆包）或多条粘一起（粘包）；
- 所以你得自己搞一个协议，明确告诉对方：

> “我发的这一坨数据，从第几字节到第几字节，是一条完整的请求。请求里面包含什么信息。”

------

### ✅ 第一步：你设计了 RPC 自定义协议结构

你画了一张协议图：

```
+--------------+----------+---------+---------+----------+---------+
| 魔数 (4B)     | 版本 (1B) | 消息类型 (1B) | 序列化类型 (1B) | 数据长度 (4B) | 数据体 (N) |
+--------------+----------+---------+---------+----------+---------+
```

| 字段名     | 说明                                                  |
| ---------- | ----------------------------------------------------- |
| 魔数       | 校验是否是你的 RPC 协议，比如 `0xCAFEBABE`            |
| 版本号     | 支持未来兼容升级                                      |
| 消息类型   | 请求、响应、心跳 ping、pong 等                        |
| 序列化方式 | Kryo？Hessian？                                       |
| 数据长度   | 数据体的字节长度                                      |
| 数据体     | 真正的 `RpcRequest` 或 `RpcResponse` 对象（序列化后） |

你把这东西封装为 `RpcMessage`：

```java
public class RpcMessage {
    private byte version;
    private byte messageType;
    private byte serializerType;
    private Object data; // RpcRequest 或 RpcResponse
}
```

### ✅ 第二步：解决拆包粘包，注册 Netty 的解码器

你用的是：

```
LengthFieldBasedFrameDecoder
```

拆包神器。你设置：

```java
pipeline.addLast(new LengthFieldBasedFrameDecoder(
    1024 * 1024,  // 最大帧长
    7,            // lengthFieldOffset：从魔数(4) + 版本(1) + 类型(1) + 序列化(1) = 7
    4,            // lengthFieldLength：长度字段本身占 4 字节
    0,            // lengthAdjustment：无偏移
    0             // initialBytesToStrip：不跳过头部
));
```

它会帮你在 TCP 字节流中切出一条条完整的“消息帧”。

### ✅ 第三步：你写了编码器和解码器

#### 👇 编码器

```java
public class RpcMessageEncoder extends MessageToByteEncoder<RpcMessage> {
    protected void encode(ChannelHandlerContext ctx, RpcMessage msg, ByteBuf out) {
        out.writeInt(0xCAFEBABE);                 // 魔数
        out.writeByte(msg.getVersion());          // 版本
        out.writeByte(msg.getMessageType());      // 类型
        out.writeByte(msg.getSerializerType());   // 序列化方式

        byte[] body = serializer.serialize(msg.getData());
        out.writeInt(body.length);                // 数据长度
        out.writeBytes(body);                     // 数据体
    }
}
```

#### 👇 解码器

```java
public class RpcMessageDecoder extends ByteToMessageDecoder {
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) {
        in.readInt();         // 魔数
        byte version = in.readByte();
        byte messageType = in.readByte();
        byte serializerType = in.readByte();
        int length = in.readInt();

        byte[] bytes = new byte[length];
        in.readBytes(bytes);

        Serializer serializer = getByType(serializerType);
        Object data = switch (messageType) {
            case REQUEST -> serializer.deserialize(bytes, RpcRequest.class);
            case RESPONSE -> serializer.deserialize(bytes, RpcResponse.class);
            default -> null;
        };

        RpcMessage msg = new RpcMessage(version, messageType, serializerType, data);
        out.add(msg);
    }
}
```

### ✅ 第四步：你的消息对象终于通了！

```java
RpcRequest req = new RpcRequest();
req.setInterfaceName("UserService");
req.setMethodName("getUser");
req.setArgs(new Object[]{1});

RpcMessage msg = new RpcMessage();
msg.setMessageType(REQUEST);
msg.setData(req);

channel.writeAndFlush(msg);
```

服务端收到完整、解码好的 `RpcRequest`，你可以开始处理业务了！

### ✅ 你现在拥有了这些能力：

| 能力               | 说明                             |
| ------------------ | -------------------------------- |
| 设计自定义协议     | 清晰表示消息结构，不再依赖字符串 |
| 解决粘包拆包问题   | TCP 流式接收不会出错             |
| 自动识别消息类型   | 请求/响应/心跳一目了然           |
| 支持多种序列化方式 | 为 SPI 做好准备                  |



# 第4章：对象不能飞线，序列化机制登场！

你上章已经实现了“完整数据包”的发送能力。

但你马上意识到：

> 虽然我在协议里能携带一个 `byte[] body`，但 Java 的对象是不能直接“过线”的。

你必须回答两个灵魂问题：

1. **怎么把 Java 对象变成字节流？**
2. **怎么在对方机器上还原出原始对象？**

这，就是“序列化机制”的职责。

------

### ✅ 第一步：你定义了一个统一的 `Serializer` 接口

你要封装底层细节，形成统一抽象：

```
java复制编辑public interface Serializer {
    byte[] serialize(Object obj);
    <T> T deserialize(byte[] data, Class<T> clazz);
    byte getCode(); // 绑定协议头中的标识符
}
```

------

### ✅ 第二步：你实现了多个序列化方式

#### 🔸 JDK 原生序列化（最垃圾，但通用）

```java
public class JdkSerializer implements Serializer {
    public byte[] serialize(Object obj) {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(bos)) {
            oos.writeObject(obj);
            return bos.toByteArray();
        }
    }

    public <T> T deserialize(byte[] data, Class<T> clazz) {
        try (ObjectInputStream ois = new ObjectInputStream(new ByteArrayInputStream(data))) {
            return clazz.cast(ois.readObject());
        }
    }

    public byte getCode() {
        return 0x01;
    }
}
```

#### 🔸 Kryo（高性能、高压缩比）

```java
public class KryoSerializer implements Serializer {
    private static final ThreadLocal<Kryo> kryoThreadLocal = ThreadLocal.withInitial(Kryo::new);

    public byte[] serialize(Object obj) {
        Kryo kryo = kryoThreadLocal.get();
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        Output output = new Output(bos);
        kryo.writeClassAndObject(output, obj);
        output.close();
        return bos.toByteArray();
    }

    public <T> T deserialize(byte[] data, Class<T> clazz) {
        Kryo kryo = kryoThreadLocal.get();
        Input input = new Input(new ByteArrayInputStream(data));
        return clazz.cast(kryo.readClassAndObject(input));
    }

    public byte getCode() {
        return 0x02;
    }
}
```

------

### ✅ 第三步：你设计了一个注册中心用于切换序列化方式

```java
public class SerializerFactory {
    private static final Map<Byte, Serializer> map = new HashMap<>();
    static {
        Serializer jdk = new JdkSerializer();
        Serializer kryo = new KryoSerializer();
        map.put(jdk.getCode(), jdk);
        map.put(kryo.getCode(), kryo);
    }

    public static Serializer getByCode(byte code) {
        return map.get(code);
    }
}
```

你可以支持更多扩展（如 JSON、Hessian、Protostuff），甚至 SPI 加载机制（第8章再说）。

------

### ✅ 第四步：你做了序列化性能测试（不要糊弄自己）

你用下面代码比较 JDK vs Kryo：

```java
long start = System.nanoTime();
for (int i = 0; i < 10000; i++) {
    byte[] data = serializer.serialize(obj);
    serializer.deserialize(data, obj.getClass());
}
long end = System.nanoTime();
System.out.println("耗时(ms): " + (end - start) / 1_000_000);
```

结果你发现：

- Kryo 是 JDK 的 5~10 倍快；
- Kryo 序列化后的字节数只有 JDK 的一半甚至更少；
- 所以你把 Kryo 设为默认序列化器。

------

### ✅ 你现在获得了这些能力：

| 能力                           | 说明                             |
| ------------------------------ | -------------------------------- |
| 让 Java 对象可跨网络传输       | serialize → send → deserialize   |
| 支持多种序列化协议             | JDK/Kryo 等，可选可扩展          |
| 每条消息都标明序列化类型       | byte serializerCode → serializer |
| 拥有了 “结构与编码分离” 的能力 | 协议结构统一，序列化器独立       |

### ✅ 插曲：你甚至预留好了可扩展机制

你想到未来某天别人可能想加上：

- JSON（调试用）
- Hessian（兼容 Dubbo）
- Protobuf（跨语言）

于是你干脆设计成：

```
interface Serializer {
    String getName();
}
```

并为每种方式注册到：

```
Map<String, Serializer> spiLoader
```

用 SPI 加载 + 反射实例化（这就是后面第8章讲的“插件机制”雏形）





# 第5章：你不想手动写请求，你要用动态代理！

### 🧠 场景

你此时已经解决了：

- 通信 ✅
- 协议结构 ✅
- 拆包粘包 ✅
- 对象序列化 ✅

但你却发现一个令人窒息的问题：

> “现在每次发请求，我都得手动 new RpcRequest，还得手动设置方法名、参数、参数类型，太原始了。”

你想要这样：

```java
UserService service = RpcProxyFactory.getProxy(UserService.class);
User user = service.getUserById(1);  // 👈 就像调用本地方法一样！
```

让“方法调用”变成“网络请求”，让开发者毫无感知地调用远程服务。

这，正是动态代理的用武之地。

## ✅ 一、你使用 JDK 的动态代理机制

你写了一个代理工厂：

```java
public class RpcClientProxy implements InvocationHandler {

    private final Class<?> targetInterface;

    public RpcClientProxy(Class<?> targetInterface) {
        this.targetInterface = targetInterface;
    }

    public Object invoke(Object proxy, Method method, Object[] args) {
        // 封装 RpcRequest
        RpcRequest request = new RpcRequest();
        request.setInterfaceName(targetInterface.getName());
        request.setMethodName(method.getName());
        request.setParameterTypes(method.getParameterTypes());
        request.setArgs(args);
        request.setRequestId(UUID.randomUUID().toString());

        // 发送请求 → 等待响应
        RpcClientTransport transport = TransportFactory.getTransport(); // 使用 Netty
        RpcResponse response = transport.send(request);

        // 返回结果（同步 or 异步）
        return response.getData();
    }

    @SuppressWarnings("unchecked")
    public static <T> T getProxy(Class<T> interfaceClass) {
        return (T) Proxy.newProxyInstance(
            interfaceClass.getClassLoader(),
            new Class[]{interfaceClass},
            new RpcClientProxy(interfaceClass)
        );
    }
}
```

------

## ✅ 二、你解决了“怎么等返回结果”的问题

你知道，Netty 是异步的。你不能像调 `channel.writeAndFlush()` 就 blocking 等返回。

所以你设计了异步响应回填机制。

### 👉 你为每个请求分配一个唯一的 requestId：

```java
request.setRequestId("abc-123-xyz");
```

然后在客户端写一个 Map：

```java
ConcurrentMap<String, CompletableFuture<RpcResponse>> futureMap = new ConcurrentHashMap<>();
```

发送前：

```java
CompletableFuture<RpcResponse> future = new CompletableFuture<>();
futureMap.put(request.getRequestId(), future);

channel.writeAndFlush(msg);
return future.get(3, TimeUnit.SECONDS); // 设置超时，避免永久阻塞
```

收到响应：

```java
public class ClientHandler extends SimpleChannelInboundHandler<RpcMessage> {
    protected void channelRead0(ChannelHandlerContext ctx, RpcMessage msg) {
        RpcResponse response = (RpcResponse) msg.getData();
        String requestId = response.getRequestId();
        futureMap.get(requestId).complete(response); // 回填
    }
}
```

## ✅ 三、你为框架实现了“异步调用”支持

你支持了这样的语法：

```
CompletableFuture<User> future = client.callAsync(() -> userService.getUserById(1));
```

你底层返回的本来就是 `CompletableFuture`，只是上层默认 `.get()` 了。

## ✅ 四、你现在获得了这些能力：

| 能力                    | 说明                           |
| ----------------------- | ------------------------------ |
| 无感代理接口 → 网络请求 | 方法调用 → RpcRequest 自动构造 |
| 响应异步回填            | 不阻塞主线程，提升性能         |
| 请求超时控制            | 避免无限 hang 死               |
| 自动绑定 requestId      | 精准配对请求与响应             |

## ✅ 你此时的调用链如下：

```
调用方法 userService.getUser(1)
 ↓
代理拦截 → 构造 RpcRequest
 ↓
序列化 → 协议封装 → Netty 发送
 ↓
等待 CompletableFuture
 ↓
服务端处理 → 构造 RpcResponse → 写回
 ↓
客户端收到响应 → 回填 Future → 返回结果
```

你实现了整个“调用链条”的闭环。

你猛然发现，**这已经不是 toy 项目了，这是“工程化组件化 RPC 框架”的雏形了**。





#  第6章：你要找到服务在哪，注册中心要登场！

### 🧠 场景：

你现在有这样的调用逻辑：

```java
UserService userService = RpcProxyFactory.getProxy(UserService.class);
userService.getUserById(1);
```

但是你突然愣住了：

> “卧槽……我这请求到底要发给谁？？IP？端口？难道还写死在代码里？”

这要是分布式部署、有多个服务节点，甚至节点会随时挂掉……你代码怎么维护？

你意识到：

> 我得搞个 **注册中心** 来动态管理服务地址。

------

## ✅ 一、你选择了 ZooKeeper 当注册中心

你选它的理由是：

| 优势         | 说明                                         |
| ------------ | -------------------------------------------- |
| 临时节点机制 | 服务端宕机 zk 自动删除节点，天然服务健康检测 |
| Watcher 机制 | 客户端可实时监听节点变化，感知上下线         |
| 轻量易用     | 单机本地就能跑，Curator 封装好用             |
| 工业级常用   | Dubbo、Hadoop、Kafka 都用它做协调器          |

## ✅ 二、你设计了服务注册路径结构

你决定将所有服务统一挂在 `/rpc` 下：

```java
/rpc
  └── /com.example.UserService
         ├── /192.168.1.10:9000    ← 服务节点1（临时节点）
         └── /192.168.1.11:9000    ← 服务节点2（临时节点）
```

------

## ✅ 三、你写了服务注册模块（服务端）

```java
public class ZkServiceRegistry {
    private final CuratorFramework client;

    public ZkServiceRegistry(String zkAddress) {
        client = CuratorFrameworkFactory.builder()
            .connectString(zkAddress)
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .namespace("rpc") // 自动加上前缀
            .build();
        client.start();
    }

    public void register(String serviceName, String hostPort) throws Exception {
        String path = "/" + serviceName + "/" + hostPort;
        client.create()
              .creatingParentsIfNeeded()
              .withMode(CreateMode.EPHEMERAL)
              .forPath(path);
        System.out.println("注册服务到ZK成功：" + path);
    }
}
```

你在服务端 Netty 启动后调用：

```
zkServiceRegistry.register("com.example.UserService", "192.168.1.10:9000");
```

------

## ✅ 四、你写了服务发现模块（客户端）

```java
public class ZkServiceDiscovery {
    private final CuratorFramework client;
    private final Map<String, List<String>> cache = new ConcurrentHashMap<>();

    public ZkServiceDiscovery(String zkAddress) {
        client = CuratorFrameworkFactory.builder()
            .connectString(zkAddress)
            .retryPolicy(new ExponentialBackoffRetry(1000, 3))
            .namespace("rpc")
            .build();
        client.start();
    }

    public List<String> getServiceAddresses(String serviceName) throws Exception {
        if (!cache.containsKey(serviceName)) {
            watchService(serviceName);
        }
        return cache.get(serviceName);
    }

    private void watchService(String serviceName) throws Exception {
        PathChildrenCache cacheWatcher = new PathChildrenCache(client, "/" + serviceName, true);
        cacheWatcher.getListenable().addListener((cli, event) -> {
            List<String> addresses = cli.getChildren().forPath("/" + serviceName);
            cache.put(serviceName, addresses);
            System.out.println("服务列表已更新: " + addresses);
        });
        cacheWatcher.start();

        // 初始加载
        List<String> addresses = client.getChildren().forPath("/" + serviceName);
        cache.put(serviceName, addresses);
    }
}
```

你将这个模块在代理层注入，从中拿到服务地址：

```java
String address = loadBalancer.select(serviceDiscovery.getServiceAddresses(interfaceName));
```

------

## ✅ 五、你封装了一个负载均衡策略接口

```java
public interface LoadBalancer {
    String select(List<String> addresses);
}
```

实现方式很多种：

| 策略                    | 描述                     |
| ----------------------- | ------------------------ |
| RandomLoadBalancer      | 随机选一个，简单实用     |
| RoundRobinLoadBalancer  | 轮询方式，平衡分发       |
| LeastActiveLoadBalancer | 最小连接数（高级加分项） |

你实现了随机版：

```java
public class RandomLoadBalancer implements LoadBalancer {
    private final Random random = new Random();
    public String select(List<String> addresses) {
        return addresses.get(random.nextInt(addresses.size()));
    }
}
```

## ✅ 六、你打通了服务注册与发现的闭环

完整流程：

```
服务端 → 启动后注册 /rpc/ServiceName/IP:Port（临时节点）

客户端 → 启动后监听 /rpc/ServiceName → 得到可用服务地址列表

客户端发起调用时 → 从地址列表中选一个 → 与之建立连接 → 发起请求

服务端宕机 → zk 自动删除临时节点 → 客户端感知变化 → 地址列表更新
```

你现在终于可以做到 **完全动态服务调用**。

## ✅ 你目前拥有的能力：

| 模块         | 作用                         |
| ------------ | ---------------------------- |
| ZK 注册模块  | 服务端启动时上报自己         |
| ZK 监听模块  | 客户端动态发现服务           |
| 负载均衡模块 | 多节点之间智能选择           |
| 健康检测能力 | 利用 zk 临时节点机制自动实现 |





#  第7章：拦不住的请求洪流，服务治理与并发控制

你已经构建好了一个能打通请求链条、自动发现服务的 RPC 框架。

但你突然意识到一个问题：

> “如果我并发发出 10000 个请求，Netty 会不会崩？如果某个服务处理太慢怎么办？我该怎么做超时控制？服务断了我能感知吗？”

你开始思考：

- 怎么做连接保活？
- 怎么做超时重试？
- 怎么做线程隔离？
- 怎么做心跳机制？

这不是“可有可无”的功能，而是 **服务生存能力的关键**。

------

## ✅ 一、你为客户端建立了连接管理模块

你发现每次发送请求都新建连接，是个性能灾难。你写了个连接池或单连接复用逻辑：

```java
public class ChannelProvider {
    private static final Map<String, Channel> map = new ConcurrentHashMap<>();

    public static Channel get(String address) {
        if (map.containsKey(address) && map.get(address).isActive()) {
            return map.get(address);
        }
        return createChannel(address);
    }

    private static Channel createChannel(String address) {
        // bootstrap.connect(...) → 创建 Netty 连接
        // 保存到 map 中
        return channel;
    }
}
```

你开始把所有请求 **复用一个连接**（长连接）。

------

## ✅ 二、你引入 Netty 的心跳机制

你知道 TCP 不会自动通知连接断开，你必须“主动 ping 一下”。

你在 pipeline 中加上：

```java
pipeline.addLast(new IdleStateHandler(15, 5, 0, TimeUnit.SECONDS)); // 5 秒写空闲
pipeline.addLast(new HeartbeatHandler());
```

你定义心跳类型：

```java
public class MessageType {
    public static final byte REQUEST = 0x01;
    public static final byte RESPONSE = 0x02;
    public static final byte HEARTBEAT_PING = 0x10;
    public static final byte HEARTBEAT_PONG = 0x11;
}
```

每隔 5 秒你就发：

```java
RpcMessage ping = new RpcMessage();
ping.setMessageType(MessageType.HEARTBEAT_PING);
channel.writeAndFlush(ping);
```

服务端收到后回复 `PONG`，客户端就知道连接还活着。

------

## ✅ 三、你加入了超时控制机制

你意识到不能让客户端 `CompletableFuture.get()` 永远等。

你加了超时控制：

```java
CompletableFuture<RpcResponse> future = new CompletableFuture<>();
futureMap.put(requestId, future);
channel.writeAndFlush(msg);

try {
    return future.get(3, TimeUnit.SECONDS); // 超过 3 秒直接抛异常
} catch (TimeoutException e) {
    throw new RpcException("调用超时");
}
```

------

## ✅ 四、你加入了请求重试机制（可选加分项）

你写了个简单重试器：

```java
public class Retryer {
    public <T> T call(Callable<T> callable, int retryCount) throws Exception {
        for (int i = 0; i < retryCount; i++) {
            try {
                return callable.call();
            } catch (Exception e) {
                if (i == retryCount - 1) throw e;
                Thread.sleep(100); // 延迟重试
            }
        }
        throw new RuntimeException("重试失败");
    }
}
```

配合使用：

```java
Retryer retryer = new Retryer();
RpcResponse response = retryer.call(() -> rpcClient.send(request), 3);
```

------

## ✅ 五、你加入了线程池，做请求隔离

你怕某个慢服务拖垮整个框架，所以你加了线程池 + 限流：

```java
ExecutorService serviceExecutor = Executors.newFixedThreadPool(50);

channel.pipeline().addLast(new ChannelInboundHandlerAdapter() {
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        serviceExecutor.submit(() -> handleRequest(ctx, msg));
    }
});
```

高级玩法：你可以引入 `Hystrix-like` 的隔离逻辑，甚至加令牌桶限流器（加分项）。

## ✅ 六、你加上了断线自动重连机制（高可用加分）

你监听 Netty 的连接关闭事件：

```java
@Override
public void channelInactive(ChannelHandlerContext ctx) {
    log.warn("连接断开，准备重连...");
    reconnect();
}
```

你通过 Bootstrap 注册 listener，设置定时重连逻辑：

```java
bootstrap.connect().addListener(f -> {
    if (!f.isSuccess()) {
        group.schedule(this::reconnect, 3, TimeUnit.SECONDS);
    }
});
```

## ✅ 七、你此时的请求模型变成这样：

```
客户端调用接口 → JDK Proxy 生成请求 → 放入线程池异步执行
 ↓
封装 RpcRequest → Netty 写出
 ↓
绑定 requestId → CompletableFuture 等待 → 设置超时
 ↓
服务端处理完成 → 写回响应 → 客户端收到回填
 ↓
客户端 Future 完成 → 返回数据
```

你已经完全打通了：

- 通信层
- 负载均衡
- 编解码
- 异步处理
- 超时治理
- 连接保活
- 自动重连

## ✅ 你实现了以下核心治理功能：

| 功能项       | 状态 |
| ------------ | ---- |
| 请求超时控制 | ✅    |
| 自动重试机制 | ✅    |
| 线程池隔离   | ✅    |
| 连接心跳     | ✅    |
| 长连接复用   | ✅    |
| 自动重连     | ✅    |





# 📘 第8章：模块重构 + SPI 插件机制 + 架构封装

> “一个框架不是靠代码量取胜，而是靠 **结构化思维** 和 **可插拔设计**。”

你现在已经拥有了一个功能完善的 RPC 框架，但你知道：

> “它还只是一个功能性工程，不是架构性产品。”

这最后一章，你要把它变成一个 **可交付、可复用、可展示的工程化作品**。

------

## ✅ 一、你开始模块重构，划分工程结构

你决定按照“职责明确 + 层次清晰”的方式来拆分模块：

```
my-rpc-framework/
├── rpc-api/               ← 公共接口（如 UserService）
├── rpc-core/              ← 核心模型（Request/Response/Message）
├── rpc-protocol/          ← 编解码器、自定义协议结构
├── rpc-transport-netty/   ← Netty 通信实现
├── rpc-registry-zk/       ← Zookeeper 注册中心实现
├── rpc-serialization/     ← 序列化 SPI 接口 + 实现类 kryo/jdk
├── rpc-loadbalancer/      ← 负载均衡 SPI 接口 + 实现
├── rpc-spi/               ← 自定义 SPI 加载器
├── rpc-spring-boot-starter（可选） ← SpringBoot Starter 支持
├── rpc-demo-client/       ← 示例调用方
└── rpc-demo-server/       ← 示例服务端
```

------

## ✅ 二、你抽象了可插拔接口

你定义了以下 SPI 接口，每个都支持插件式扩展：

| 接口名称             | 功能                      |
| -------------------- | ------------------------- |
| `Serializer`         | 对象序列化                |
| `Registry`           | 注册中心                  |
| `LoadBalancer`       | 负载均衡策略              |
| `Compressor`（可选） | 数据压缩算法              |
| `TransportClient`    | 通信实现（Netty / HTTP）  |
| `ProxyFactory`       | 动态代理方式（JDK/CGLIB） |

## ✅ 三、你实现了自定义 SPI 加载机制

你模仿 Dubbo / Spring Boot 的插件机制，实现了：

```java
public class ExtensionLoader<T> {

    private final Map<String, Class<? extends T>> classMap = new HashMap<>();

    public ExtensionLoader(Class<T> type) {
        loadFromResources(type);
    }

    private void loadFromResources(Class<T> type) {
        String fileName = "META-INF/rpc/" + type.getName();
        InputStream is = getClass().getClassLoader().getResourceAsStream(fileName);
        // 读取文件，注册类名
    }

    public T getExtension(String name) {
        return classMap.get(name).newInstance(); // 支持反射创建
    }
}
```

你可以像这样注册：

```java
# META-INF/rpc/com.rpc.core.serialize.Serializer
jdk=com.rpc.core.serialize.impl.JdkSerializer
kryo=com.rpc.core.serialize.impl.KryoSerializer
```

------

## ✅ 四、你配置 SPI 加载方式

你可以支持配置文件 `rpc.properties`：

```java
rpc.serializer=kryo
rpc.registry=zk
rpc.loadbalancer=random
```

然后你在框架启动时读取：

```
Serializer serializer = ExtensionLoader.load(Serializer.class).getExtension("kryo");
```

## ✅ 五、你支持了 SpringBoot Starter（加分项）

你做了一个简单的 Starter：

```java
@Configuration
public class RpcAutoConfiguration {
    @Bean
    public RpcProxyFactory rpcProxyFactory() {
        return new RpcProxyFactory(); // 自动注入代理工具
    }
}
```

让开发者只需要：

```java
@RpcReference
private UserService userService;
```

就能自动注入远程代理。

## ✅ 六、你把整个调用栈封装成“框架思维”

```
用户 → 定义接口 UserService
   ↓
代理 → 拦截方法，封装请求
   ↓
注册中心 → 获取地址，负载均衡选择
   ↓
通信层 → Netty 连接池，发送请求
   ↓
编码协议 → Header + Length + 序列化数据
   ↓
解码 → 响应还原，CompletableFuture 完成
   ↓
用户 → 获取最终结果
```

你实现了完整的：

- 通信链
- 编解码链
- 动态代理链
- 服务注册链
- 异步响应链
- 插件扩展链





















