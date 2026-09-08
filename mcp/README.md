# MCP 辅助服务

本目录存放 AI 编程客户端用的 MCP 配置，与产品运行（Vue / Spring Boot / 检测服务）无关。

```
mcp/
├── mysql/      MySQL MCP（依赖本目录 node_modules）
├── feishu/     飞书 lark-mcp
└── dingtalk/   钉钉 dingtalk-mcp / mcp-dingtalk-doc
```

## MySQL

1. `cd mcp/mysql`
2. 复制 `.env.mcp.example` 为 `.env.mcp`，填写本机 MySQL 账号
3. 如无 `node_modules`：`npm install`
4. 双击 `start.bat`

## 飞书

1. `cd mcp/feishu`
2. 按 `.env.example` 填写 App ID / Secret
3. 双击 `start.bat`

## 钉钉

1. `cd mcp/dingtalk`
2. 按 `.env.example` 填写 Client ID / Secret
3. 说明见 `DINGTALK_MCP_GUIDE.md`
4. 双击 `start.bat`
