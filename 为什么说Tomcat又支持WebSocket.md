# Tomcat 本来是干 HTTP 的，为什么它还能搞 WebSocket？

## 🔍 问题本质是两个：

1. **Tomcat 本来是干 HTTP 的，为什么它还能搞 WebSocket？**
2. **Servlet 不就是 HTTP 的抽象吗？为什么 Servlet API 居然也支持 WebSocket？这不是越界了吗？**

## ✅ 先说结论：

### ❗不是 Servlet 支持了 WebSocket，而是：

> Java EE（Jakarta EE）从 **Servlet 3.1 开始**引入了一个**新的独立规范**叫做：`Java WebSocket API`（JSR 356），这套 API 允许我们在 Servlet 容器中运行 WebSocket 服务。

也就是说：

- **Servlet 专门管 HTTP**
- **WebSocket 是单独一套 API**
- **Tomcat 只是作为“容器”**，可以同时支持这两套协议。

## 📦 Tomcat 是什么？——“容器 + 协议栈”

Tomcat 的本质是一个**HTTP 协议通信容器**，但它也通过插件支持了 WebSocket 协议：

| 协议      | 是否 Tomcat 内建支持 | 通过什么支持                    |
| --------- | -------------------- | ------------------------------- |
| HTTP      | ✅ 是（核心支持）     | `org.apache.coyote.http11.*`    |
| HTTPS     | ✅ 是                 | `SSL` + `HTTP`                  |
| WebSocket | ✅ 是（插件模块）     | `org.apache.tomcat.websocket.*` |

也就是说，**Tomcat 作为通信容器，不止支持 HTTP**，它也通过插件模块支持 WebSocket。

## 💡 为什么 Servlet 应用也能用 WebSocket？

### 因为 WebSocket 是 **JavaEE 的另一个规范**：

Java EE 引入了 `javax.websocket` 规范，它定义了 WebSocket 如何在 Java 里运行：

- `@ServerEndpoint("/ws")` 就是 Java EE 标准的一部分。
- 它是通过 Servlet 容器（如 Tomcat）托管的，但不是 Servlet 的一部分。
- Tomcat 之所以能跑这个规范，是因为它实现了这个规范的运行时支持（WebSocket Engine）。

### 举个例子：

```java
@ServerEndpoint("/chat")
public class ChatEndpoint {
    @OnMessage
    public String onMessage(String msg) {
        return "echo: " + msg;
    }
}
```

这玩意儿**不是 Servlet**，也不是 HTTP 请求，它走的是**WebSocket 协议栈**，但它仍然部署在 Tomcat 里。

## 🤔 为什么不另开一套服务器搞 WebSocket？

你可以另开，比如用 Netty 纯写 WebSocket，但 Java EE 生态更希望用**统一容器**（Tomcat、Jetty、Undertow）来跑所有协议，方便部署和管理。

所以 Tomcat 做了：

1. 默认支持 HTTP 协议（靠 Coyote 处理器）
2. 同时嵌入 WebSocket 支持（实现 Java WebSocket 规范）

## 🧠 回到你的问题 —— 一句话总结：

> **Tomcat 支持 WebSocket 是因为它实现了 JavaEE 中 WebSocket 规范的容器支持，而不是 Servlet 自己支持 WebSocket。**

- Servlet 规范只支持 HTTP。
- WebSocket 是 JavaEE 的另一套规范。
- Tomcat 实现了这两套规范的支持，所以你**可以在一个 Servlet 项目中用 WebSocket**，但这两者底层是**不同协议栈、不同机制**。



