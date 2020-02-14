# spring-cloud-gateway
网关原理如图所示：

![image](https://github.com/zk185/spring-cloud-gateway/blob/master/images/spring_cloud_gateway_diagram.png)

客户端向Spring Cloud Gateway发出请求。如果网关处理程序映射确定请求与路由匹配，则将其发送到网关Web处理程序。此处理程序运行通过特定于请求的过滤器链发送请求。滤波器被虚线划分的原因是滤波器可以在发送代理请求之前或之后执行逻辑。执行所有“预”过滤器逻辑，然后进行代理请求。在发出代理请求之后，执行“post”过滤器逻辑。

[注意]
在没有端口的路由中定义的URI将分别为HTTP和HTTPS URI获取默认端口设置为80和443。

## 1.起步依赖
引入pom依赖，Spring Cloud版本为Greenwich.SR1
```
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-starter-gateway</artifactId>
</dependency>
```
## 2.过滤器
过滤器分为GatewayFilter和GlobalFilter，其他有待考察。

- GatewayFilter有很多内置工厂，需要与配置文件结合使用
- GlobalFilter作用于所有路由请求

### 2.1 实现过滤器功能
下面主要针对自定义的GatewayFilterFactory做出demo示例。
#### 2.1.1 继承AbstractGatewayFilterFactory抽象类，并重写apply()方法
类命名要求：前缀+GatewayFilterFactory,用于配置文件匹配过滤器
```
public class TokenCheckGatewayFilterFactory extends AbstractGatewayFilterFactory<TokenCheckGatewayFilterFactory.Config> {

    public TokenCheckGatewayFilterFactory() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            String token = exchange.getRequest().getHeaders().getFirst("Authorization");
            //校验jwtToken的合法性
            if (token != null) {
                // 合法
                // 将用户id作为参数传递下去
                return chain.filter(exchange);
            }

            //不合法(响应未登录的异常)
            ServerHttpResponse response = exchange.getResponse();
            //设置headers
            HttpHeaders httpHeaders = response.getHeaders();
            httpHeaders.add("Content-Type", "application/json; charset=UTF-8");
            httpHeaders.add("Cache-Control", "no-store, no-cache, must-revalidate, max-age=0");
            //设置body
            String warningStr = "未登录或登录超时";
            DataBuffer bodyDataBuffer = response.bufferFactory().wrap(warningStr.getBytes());

            return response.writeWith(Mono.just(bodyDataBuffer));
        };
    }
    static class Config {}
}
```
#### 2.1.2 注入bean
```
@Configuration
public class FilterConfig {

    @Bean
    public TokenCheckGatewayFilterFactory getJwtCheck() {
        return new TokenCheckGatewayFilterFactory();
    }
}
```
#### 2.1.3 配置文件
```
spring:
  cloud:
    gateway:
      routes:
      # 登录路由，不验证token
      - id: login_route
        uri: http://10.1.7.81:8081
        predicates:
        - Path=/gateway/login/**
        filters:
        - StripPrefix=1
      - id: server_route
        # 转发地址
        uri: http://10.1.7.81:8081
        predicates:
        # 拦截请求路径
        - Path=/gateway/**
        filters:
        # 跳过1个前缀:
        # localhost:8080/gateway/info
        # localhost:8080/info
        - StripPrefix = 1
        # token身份认证 TokenCheckGatewayFilterFactory
        - TokenCheck
        # 请求流量限制，默认5MB
        - name: RequestSize
          args:
            maxSize: 5000000
      # sockJS路由需要与websocket路由结合使用
      - id: sockJS_route
        uri: http://10.1.7.81:8081
        predicates:
          - Path=/websocket/**
      - id: websocket_route
        uri: ws://localhost:8081
        predicates:
          - Path=/websocket/**
      # 开启/关闭网关
      enabled: true
      loadbalancer:
        use404: true
  application:
    name: gateway
```
配置解释：
- routes 可以包含多个id，每个id有自己的拦截配置，默认按照先后顺序匹配，直到匹配到第一个符合条件的路由，不再执行其他路由。
所以注意将范围较大的规则放在后面匹配，如先写web/info/** 后写web/**。
- id 保证唯一即可
- uri 要转发的地址
- predicates 可以转发的条件，包含多种参数，可结合使用，如Path、Header、Host等
- Path 拦截请求地址，配置为/gateway/** ，表示前端请求gateway/a、gateway/b都可以被拦截。
- filters 选择过滤器，可以多种过滤器结合。
- StripPrefix =1表示转发路径跳过1个前缀，如localhost:8080/gateway/info,转发后的地址为localhost:8081/info
- TokenCheck 匹配的是TokenCheckGatewayFilterFactory过滤器
## 3.如何使用
前端请求网关IP+port+配置中-Path包含的部分+真实地址，如果使用上述配置，以下请求将被拦截
- localhost:8080/gateway/login/hello?name=zk 转发后地址 localhost:8081/login/hello?name=zk
- localhost:8080/gateway/aa 转发后地址 localhost:8081/aa 并进入TokenCheckGatewayFilterFactory过滤器

websocket使用后续补充
