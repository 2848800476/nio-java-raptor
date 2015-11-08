2014/1/3
1.0.11 aplha 发布
update:
1.修改内存分配策略，降低mem lack时的分配新的heap内存的频率，优先使用池化内存，有效控制服务端资源使用率，降低oom风险
2.修正发送mem lack时，protobuf 编码失败的bug
3.修改protobuf协议，于netty protobuf协议兼容

---

2013/11/13
1.0.9 发布
update:
1.修正内存池释放bug
2.内存池调整为多个processor使用一个
3.解码移动到worker线程中进行处理，tps上升
4.增强性能，在阻塞同步通信下，性能增强30%tps

1169byte消息，响应大小137byte，protobuf编解码，单tcp连接双工的非阻塞同步通信平均tps 97800 ,千兆网卡，平均112MB

1169byte消息，响应大小137byte，protobuf编解码，单tcp连接，客户端阻塞同步通信 平均tps 3900 ,千兆网卡流量4.7MB

测试环境：双核虚拟机

---














设计初衷是提供方便易用，且高效率的nio框架，一部分实现上参考了mina。还包括线程池，编解码，内存池等机制，以便于开发高性能tcp程序。文档后续会慢慢的补上。
整体实现上尽量少的使用锁，避免cpu浪费。
整体框架提供了服务线程池，对于一个连接来讲，这个连接的事件将会在线程池中执行，不过这个过程保证是顺序执行的，例如对于一个连接，在差不多同时的时间接收到了两个包，那么，我们可以认为，在第一次接受事件未执行完，第二个事件不会被执行，即这两个事件不是分别被发送到两条线程中执行，可以认为是被串行化到同一条线程中（注意：此处一条线程，并不是真的在同一条线程中执行，这两个事件可能在不同线程中，只不过框架严格保证执行的先后顺序了，所以对于ThreadLocal变量的使用，是不安全的）。由于线程池的添加，我们在开过程中，无需再考虑业务层消耗，直接按事件触发式就可以了，也不用再将数据包投递到另外的线程中。使用方式参见TestProtocolClient,TestProtocolServer

框架还提供了完全不使用线程池的方式，请参见TestClient,TestServer

当然，如果你需要部分事件置入到线程池，也是可以的，后续，我会补一下这反面的例子代码


循环内存池的机制，实际上是为了提供一种避免gc的一种方式，当然如果你的程序不需要内存池也可以取消掉，内存池采用的是块分配，例如块大小1024，如果需要4096的话，内存池会返回4个块，具体使用可以参考TextLineProtocol类.

TextLineProtocol提供了按行的协议解析，字符采用的unicode编码,在类似这种情况下，内存池的作用就体现出来了，不用每次new新的byte数组了。

性能测试：

使用协议为java序列化协议（实际上此协议占用的空间较大，应为需要存储一些类的信息）。
客户端5000活动的tcp连接,请求应答式，吞吐量 8000tps ，带宽使用20mbps(客户端和服务器带宽30mbps左右)
> 单一客户端请求时，服务器Cpu(s):  2.2%us,  0.9%sy,  0.0%ni, 96.6%id,  0.0%wa,                 0.0%hi,  0.3%si,  0.0%st
> 单一客户端纯请求连接，20000次连接/s

服务器单机测试
> 服务器和客户端均在一台服务其上，客户端采用20个tcp连接，请求应答式，请求1k，应答1k,
40000tps

双服务器异步双工测试：
服务器为e5520  ,网络为1000m,请求128byte,应答64byte,140000+ tps ,网络使用率 20%
请参见cn.com.sparkle.raptor.test.TestAynscClientObjectProtocol

