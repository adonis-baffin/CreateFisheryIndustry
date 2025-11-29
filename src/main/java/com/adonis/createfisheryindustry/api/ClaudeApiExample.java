package com.adonis.createfisheryindustry.api;

import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Claude API 使用示例
 * 演示如何使用 ClaudeApiClient
 */
public class ClaudeApiExample {
    private static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * 使用示例
     */
    public static void exampleUsage() {
        // 1. 创建客户端（需要从配置或环境变量获取API密钥）
        String apiKey = System.getenv("ANTHROPIC_API_KEY"); // 或者从配置文件读取
        if (apiKey == null || apiKey.isEmpty()) {
            LOGGER.warn("未找到 ANTHROPIC_API_KEY 环境变量，请设置你的API密钥");
            return;
        }
        
        ClaudeApiClient client = new ClaudeApiClient(apiKey, "claude-3-5-sonnet-20241022");
        
        try {
            // 方式1: 发送简单消息
            String response = client.sendSimpleMessage("你好，请介绍一下Java编程语言", 1000);
            if (response != null) {
                LOGGER.info("Claude回复: {}", response);
            } else {
                LOGGER.error("未能获取Claude的回复");
            }
            
            // 方式2: 发送多轮对话
            List<ClaudeApiClient.ClaudeMessage> messages = new ArrayList<>();
            messages.add(new ClaudeApiClient.ClaudeMessage("user", "什么是Minecraft模组开发？"));
            
            ClaudeApiClient.ClaudeApiResponse apiResponse = client.sendMessage(messages, 2000, 
                "你是一个专业的Minecraft模组开发助手。");
            
            if (apiResponse != null && apiResponse.content != null && !apiResponse.content.isEmpty()) {
                String assistantReply = apiResponse.content.get(0).text;
                LOGGER.info("Claude回复: {}", assistantReply);
                
                // 继续对话
                messages.add(new ClaudeApiClient.ClaudeMessage("assistant", assistantReply));
                messages.add(new ClaudeApiClient.ClaudeMessage("user", "如何创建一个新的方块？"));
                
                ClaudeApiClient.ClaudeApiResponse followUpResponse = client.sendMessage(messages, 2000);
                if (followUpResponse != null && followUpResponse.content != null) {
                    LOGGER.info("Claude继续回复: {}", followUpResponse.content.get(0).text);
                }
            }
            
        } finally {
            // 3. 关闭客户端（释放资源）
            client.close();
        }
    }
}

