# 配置介绍

## 客户端鉴权
source配置项中的upstream_auth_user/upstream_auth_password是用于配置上游代理接入的鉴权。

如果想要控制哪些客户端可以使用本框架暴露的端口，可以通过auth_mode相关配置进行控制。

以下鉴权模式支持：
1. 为global全局配置
2. 为每个source单独配置，且优先级高于global全局配置
3. source未配置鉴权时，会尝试查找global鉴权配置，若global也未配置，则不进行鉴权

### 客户端鉴权模式，
可选以下五种,也可以为每个source单独配置，默认为不鉴权
- NONE 不鉴权
- ALL 以下所有模式的配置同时生效，生效条件为：用户密码通过并且不在黑名单 或者 在白名单IP配置中
- USER_ONLY 用户+密码模式，需要配置auth_username和auth_password，客户端使用时与正常代理配置用户密码一致
- WHITE_IP_ONLY IP白名单模式，需要配置auth_white_ips
- BLACK_IP IP黑名单模式，需要配置auth_black_ips，可以同时配置auth_username和auth_password进行用户鉴权

### 无鉴权模式
默认为不鉴权，不需要配置，或者配置auth_mode为NONE

```
auth_mode = NONE
```

### ALL模式
```
auth_mode = ALL
# IP列表由,分割
auth_white_ips = 127.0.0.3,127.0.0.4
auth_username = test
auth_password = hello_world
auth_black_ips = 127.0.0.10
```

### IP白名单模式
白名单模式下只鉴权客户端IP。

只有配置在白名单列表中的客户端IP才有权限使用，配置样例：

```
auth_mode = WHITE_IP_ONLY
# IP列表由,分割
auth_white_ips = 127.0.0.3,127.0.0.4
```

注意，如果本工程由Nginx进行转发，请检查NG转发配置中是否配置了X-Real-IP 请求头携带真实的客户端IP，否则IP鉴权无法获取到真实IP。
例如：
```
proxy_set_header X-Real-IP $remote_addr;
```

### 账号密码模式

```

auth_mode = USER_ONLY
auth_username = test
auth_password = hello_world

```

使用账号密码模式之后，auth_white_ips和auth_black_ips配置将不生效，即账号密码模式只鉴权账号和密码。

测试代理可用性：
```
#带上用户名密码
curl -v "http://xxxx/testproxy" -x username:password@122.96.59.105:23068

#socks5
curl -v "http://xxxx/testproxy" --socks5 username:password@122.96.59.105:23068
```

### IP黑名单模式
黑名单模式可以配置：
- auth_username
- auth_password
- auth_black_ips

```
auth_mode = BLACK_IP
# IP列表由,分割
auth_black_ips = 127.0.0.3,127.0.0.4
auth_username = test
auth_password = hello_world
```