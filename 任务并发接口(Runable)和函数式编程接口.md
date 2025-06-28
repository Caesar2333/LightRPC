# Runable接口和函数式编程接口

## ✅ 一图总览：Runnable / Callable vs Consumer / Supplier / Function

| 接口       | 所属体系            | 是否有返回值 | 是否可抛异常 | 设计目的                       |
| ---------- | ------------------- | ------------ | ------------ | ------------------------------ |
| `Runnable` | 并发执行模型        | ❌ 无返回值   | ❌ 不抛异常   | 提交任务（线程/线程池）        |
| `Callable` | 并发执行模型        | ✅ 有返回值   | ✅ 可抛异常   | 异步任务 + 结果（如 `Future`） |
| `Consumer` | 函数式接口（JDK 8） | ❌ 无返回值   | ❌ 不抛异常   | 对输入做操作（如打印、消费）   |
| `Supplier` | 函数式接口（JDK 8） | ✅ 有返回值   | ❌ 不抛异常   | 提供一个值（无输入）           |
| `Function` | 函数式接口（JDK 8） | ✅ 有返回值   | ❌ 不抛异常   | 输入 → 输出函数                |

## ✅ 二、设计目的完全不同

### ☑ 1. `Runnable` 和 `Callable` 是为 **线程模型设计的接口**

- 你丢给线程池、或者创建线程时：

  ```java
  new Thread(Runnable).start();
  pool.submit(Callable);
  ```

- 它们的目的是：“我要把一段逻辑 **扔进另一个线程跑**。”

- 是否要结果、是否能抛异常，是为了配合 `Future` 来控制**任务生命周期**。

📌 **关键词：线程调度、任务提交、执行过程控制。**

------

### ☑ 2. `Consumer` / `Supplier` / `Function` 是为 **函数式编程设计的接口**

- JDK 8 提出“函数式接口”概念（配合 Lambda 表达式）：

  ```java
  list.forEach(Consumer);
  Stream.map(Function);
  Optional.orElseGet(Supplier);
  ```

- 它们是为“声明一个逻辑片段”设计的——不是给线程池跑，而是作为**传参工具、回调函数、链式操作的语义单元**。

📌 **关键词：函数传参、声明行为、组合式 API 设计。**

## ✅ 三、它们到底能不能替代彼此？

### ✅ 从签名上看，有些确实类似：

- `Runnable` ≈ `() -> void`
- `Supplier<T>` ≈ `() -> T`
- `Consumer<T>` ≈ `(T t) -> void`

### ❌ 但是它们**不能随便互相替代**，因为：

1. **设计目的不同：**
   - `Runnable` 是给线程池执行用的；
   - `Consumer` 是给 `Stream.forEach` 这种数据操作框架用的；
   - `Supplier` 是为延迟计算、懒加载用的。
2. **异常机制不同：**
   - `Callable` 允许 `throws Exception`，但 `Supplier` 不允许；
   - 所以你不能把一个抛异常的 `Callable` 当成 `Supplier` 用。
3. **生命周期和线程模型不同：**
   - `Runnable/Callable` 是“任务” → 会被线程调度执行；
   - `Function/Consumer` 是“行为片段” → 不涉及线程调度，是同步执行的。

## ✅ 四、举个例子帮你感知差异

### 1. Runnable 是“交给线程池干活”的

```java
Runnable task = () -> System.out.println("干活去了！");
new Thread(task).start(); // 线程启动后异步干
```

### 2. Consumer 是“把这个操作应用到数据上”的

```java
List<String> list = List.of("a", "b", "c");
list.forEach((str) -> System.out.println("打印：" + str)); // 同步、串行执行
```

### 3. Callable 是“任务 + 返回值 + 异常”，适合异步任务

```java
Callable<Integer> task = () -> {
    Thread.sleep(1000);
    return 123;
};
Future<Integer> result = pool.submit(task);
System.out.println(result.get()); // 等结果
```

### 4. Supplier 是“我要一个值，你去提供”

```java
Supplier<Double> randomSupplier = () -> Math.random();
System.out.println(randomSupplier.get()); // 同步获取
```

## ✅ 五、小结一句话解释它们

| 接口     | 一句话解释                 |
| -------- | -------------------------- |
| Runnable | “执行一段任务（无返回值）” |
| Callable | “执行一段任务并返回结果”   |
| Supplier | “给我一个值”               |
| Consumer | “拿去干一件事”             |
| Function | “输入 → 输出”              |