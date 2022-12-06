# 后台管理API介绍

## 后台管理配置
在config.ini global中配置:
- admin_server_port：admin server对外的端口，通过该端口访问相关控制API
- admin_api_token：控制API的鉴权Token，敏感API必须带上该参数才能授权访问

## 重新拨号
对某个端口手动更换代理通道

调用方式:
> http://127.0.0.1:9085/reDial?mappingPort=36000

## 设置白名单IP列表
用于白名单鉴权模式下，客户端出口IP经常变化时，动态添加白名单列表的情景，例如群控等场景。

### 参数解析
- apiToken 鉴权API
- mappingPort 将根据端口查询对应的source配置
- action 目前支持add/list，用于添加或查询source配置
- add action下，whiteIps代表期望添加的白名单IP列表，以,分割

### 添加白名单IP列表
调用方式:
> http://127.0.0.1:9085/whiteIps?apiToken=23742432dshfusi1233&mappingPort=36000&action=add&whiteIps=1233,127.0.0.1
> http://127.0.0.1:9085/whiteIps?apiToken=23742432dshfusi1233&mappingPort=36011&action=add&whiteIps=192.168.24.4

返回结果:

```
// 添加成功的IP列表，会自动过滤非法IP格式和去重
{
    "code":0,
    "data":[
        "127.0.0.1"
    ]
}
```

### 查询白名单IP列表

调用方式:
> http://127.0.0.1:9085/whiteIps?apiToken=23742432dshfusi1233&mappingPort=36000&action=list

```
{
    "code":0,
    "data":{
        "authConfig":{
            "authMode":"WHITE_IP_ONLY",
            "blackIps":[

            ],
            "whiteIps":[
                "127.0.0.3",
                "127.0.0.4"
            ]
        },
        "looper":{

        },
        "name":"本地测试代理源",
        "protocol":"http/https",
        "sourceUrl":"http://127.0.0.1:8080/proxy-mocker"
    }
}
```