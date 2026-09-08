# 钉钉 MCP 服务器使用指南

## 已安装的MCP服务

### 1. dingtalk-mcp (官方)
**功能**：发送消息、工作通知、群消息、文件发送

**启动方式**：
```bash
set DINGTALK_Client_ID=dingxxx
set DINGTALK_Client_Secret=xxx
dingtalk-mcp
```

**提供的能力**：
- `send_message` - 发送普通消息
- `send_work_notification` - 发送工作通知
- `send_file_message` - 发送文件消息（支持PDF）
- `get_user_info` - 获取用户信息
- `create_chat` - 创建群聊

---

### 2. mcp-dingtalk-doc (社区版)
**功能**：钉钉文档管理、文档解析、PDF导出

**启动方式**：
```bash
set DING_DOC_COOKIE=your_cookie
mcp-dingtalk-doc
```

**提供的能力**：
- `list_documents` - 列出文档
- `get_document_content` - 获取文档内容
- `export_to_pdf` - 导出文档为PDF
- `search_documents` - 搜索文档

---

## 获取钉钉应用凭证

1. 访问 [钉钉开放平台](https://open-dev.dingtalk.com/)
2. 创建「企业内部应用」
3. 获取 **Client ID (AppKey)** 和 **Client Secret**
4. 添加权限：
   - `Contact.User.Read` - 读取用户信息
   - `ChatMessage.Write` - 发送群消息
   - `WorkNotification.Write` - 发送工作通知
   - `File.Write` - 上传文件

---

## 与现有系统整合

### 场景：发送检测报告PDF到钉钉

```
Detect.vue 导出PDF
    ↓
调用 Spring Boot 接口 /api/dingtalk/send
    ↓
Java后端调用 dingtalk-mcp (端口默认3000)
    ↓
PDF发送到钉钉用户/群
```

### Spring Boot调用示例

```java
@Service
public class DingTalkService {
    
    @Value("${mcp.dingtalk.url:http://localhost:3000}")
    private String mcpUrl;
    
    public void sendPdf(String userId, byte[] pdfBytes, String filename) {
        // 通过MCP发送文件到钉钉
        RestTemplate rest = new RestTemplate();
        
        Map<String, Object> request = new HashMap<>();
        request.put("tool", "send_file_message");
        request.put("params", Map.of(
            "userId", userId,
            "file", pdfBytes,
            "filename", filename
        ));
        
        rest.postForObject(mcpUrl + "/invoke", request, String.class);
    }
}
```

---

## 启动命令

```bash
# 方式1：使用脚本
	.\mcp\dingtalk\start.bat

# 方式2：手动启动
dingtalk-mcp
mcp-dingtalk-doc
```

---

## 端口说明

| 服务 | 默认端口 | 协议 |
|------|----------|------|
| dingtalk-mcp | 3000 | stdio / http |
| mcp-dingtalk-doc | 3001 | stdio / http |
