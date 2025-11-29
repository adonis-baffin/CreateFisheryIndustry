# Claude API 集成使用说明

本模块提供了与 Anthropic Claude API 集成的功能，允许你在 Minecraft 模组中调用 Claude AI。

## 快速开始

### 1. 获取 API 密钥

1. 访问 [Anthropic 官网](https://www.anthropic.com/)
2. 注册账号并获取 API 密钥
3. 将 API 密钥设置为环境变量：
   ```bash
   # Windows PowerShell
   $env:ANTHROPIC_API_KEY="your-api-key-here"
   
   # Windows CMD
   set ANTHROPIC_API_KEY=your-api-key-here
   
   # Linux/Mac
   export ANTHROPIC_API_KEY="your-api-key-here"
   ```

### 2. 基本使用

```java
import com.adonis.createfisheryindustry.api.ClaudeApiClient;

// 创建客户端
String apiKey = System.getenv("ANTHROPIC_API_KEY");
ClaudeApiClient client = new ClaudeApiClient(apiKey, "claude-3-5-sonnet-20241022");

// 发送简单消息
String response = client.sendSimpleMessage("你好，Claude！", 1000);
if (response != null) {
    System.out.println("Claude回复: " + response);
}

// 关闭客户端
client.close();
```

### 3. 多轮对话

```java
List<ClaudeApiClient.ClaudeMessage> messages = new ArrayList<>();
messages.add(new ClaudeApiClient.ClaudeMessage("user", "什么是Java？"));

ClaudeApiClient.ClaudeApiResponse response = client.sendMessage(messages, 2000, 
    "你是一个编程助手。");
    
if (response != null && response.content != null) {
    String assistantReply = response.content.get(0).text;
    
    // 继续对话
    messages.add(new ClaudeApiClient.ClaudeMessage("assistant", assistantReply));
    messages.add(new ClaudeApiClient.ClaudeMessage("user", "如何创建类？"));
    
    ClaudeApiClient.ClaudeApiResponse followUp = client.sendMessage(messages, 2000);
}
```

## 可用模型

- `claude-3-5-sonnet-20241022` - 推荐的默认模型
- `claude-3-5-haiku-20241022` - 更快速、更经济的模型
- `claude-3-opus-20240229` - 最强大的模型

## 注意事项

1. **API 密钥安全**: 不要将 API 密钥硬编码在代码中，使用环境变量或配置文件
2. **异步调用**: 当前实现是同步的，如果需要异步，建议在单独的线程中调用
3. **错误处理**: 始终检查返回值为 null 的情况，表示请求失败
4. **资源管理**: 使用完毕后调用 `close()` 方法释放资源
5. **API 费用**: 注意 Claude API 是按使用量计费的，请合理使用

## API 响应结构

`ClaudeApiResponse` 包含以下字段：
- `id`: 请求ID
- `content`: 响应内容列表（包含文本）
- `model`: 使用的模型
- `stopReason`: 停止原因
- `usage`: Token使用情况（inputTokens, outputTokens）

