package com.adonis.createfisheryindustry.api;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Claude API 客户端
 * 用于调用 Anthropic 的 Claude API
 */
public class ClaudeApiClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String API_BASE_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";
    
    private final OkHttpClient httpClient;
    private final String apiKey;
    private final String model;
    
    /**
     * 创建 Claude API 客户端
     * @param apiKey Anthropic API 密钥
     * @param model 要使用的模型（例如: "claude-3-5-sonnet-20241022"）
     */
    public ClaudeApiClient(@NotNull String apiKey, @NotNull String model) {
        this.apiKey = apiKey;
        this.model = model;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .writeTimeout(60, TimeUnit.SECONDS)
                .build();
    }
    
    /**
     * 发送消息到 Claude API
     * @param messages 消息列表
     * @param maxTokens 最大生成token数
     * @return API响应，失败时返回null
     */
    @Nullable
    public ClaudeApiResponse sendMessage(@NotNull List<ClaudeMessage> messages, int maxTokens) {
        return sendMessage(messages, maxTokens, null);
    }
    
    /**
     * 发送消息到 Claude API
     * @param messages 消息列表
     * @param maxTokens 最大生成token数
     * @param systemPrompt 系统提示词（可选）
     * @return API响应，失败时返回null
     */
    @Nullable
    public ClaudeApiResponse sendMessage(@NotNull List<ClaudeMessage> messages, int maxTokens, @Nullable String systemPrompt) {
        try {
            // 构建请求体
            ClaudeApiRequest request = new ClaudeApiRequest();
            request.model = this.model;
            request.maxTokens = maxTokens;
            request.messages = messages;
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                request.system = systemPrompt;
            }
            
            String jsonBody = GSON.toJson(request);
            
            // 创建HTTP请求
            Request httpRequest = new Request.Builder()
                    .url(API_BASE_URL)
                    .post(RequestBody.create(jsonBody, MediaType.get("application/json")))
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", API_VERSION)
                    .header("Content-Type", "application/json")
                    .build();
            
            // 发送请求
            try (Response response = httpClient.newCall(httpRequest).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "无响应体";
                    LOGGER.error("Claude API 请求失败: {} - {}", response.code(), errorBody);
                    return null;
                }
                
                ResponseBody body = response.body();
                if (body == null) {
                    LOGGER.error("Claude API 响应体为空");
                    return null;
                }
                
                String responseJson = body.string();
                ClaudeApiResponse apiResponse = GSON.fromJson(responseJson, ClaudeApiResponse.class);
                
                if (apiResponse != null && apiResponse.content != null && !apiResponse.content.isEmpty()) {
                    LOGGER.debug("Claude API 响应成功: {}", apiResponse.content.get(0).text);
                }
                
                return apiResponse;
            }
        } catch (IOException e) {
            LOGGER.error("调用 Claude API 时发生错误", e);
            return null;
        }
    }
    
    /**
     * 发送简单文本消息（便捷方法）
     * @param userMessage 用户消息
     * @param maxTokens 最大生成token数
     * @return Claude的回复文本，失败时返回null
     */
    @Nullable
    public String sendSimpleMessage(@NotNull String userMessage, int maxTokens) {
        List<ClaudeMessage> messages = new ArrayList<>();
        messages.add(new ClaudeMessage("user", userMessage));
        
        ClaudeApiResponse response = sendMessage(messages, maxTokens);
        if (response != null && response.content != null && !response.content.isEmpty()) {
            return response.content.get(0).text;
        }
        return null;
    }
    
    /**
     * 关闭HTTP客户端
     */
    public void close() {
        httpClient.dispatcher().executorService().shutdown();
        httpClient.connectionPool().evictAll();
    }
    
    /**
     * API 请求类
     */
    public static class ClaudeApiRequest {
        public String model;
        public int maxTokens;
        public List<ClaudeMessage> messages;
        public String system;
    }
    
    /**
     * Claude 消息类
     */
    public static class ClaudeMessage {
        public String role; // "user" 或 "assistant"
        public String content;
        
        public ClaudeMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }
    }
    
    /**
     * API 响应类
     */
    public static class ClaudeApiResponse {
        public String id;
        public String type;
        public String role;
        public List<ContentBlock> content;
        public String model;
        public String stopReason;
        public StopSequence stopSequence;
        public Usage usage;
        
        public static class ContentBlock {
            public String type;
            public String text;
        }
        
        public static class StopSequence {
            // 停止序列相关字段
        }
        
        public static class Usage {
            public int inputTokens;
            public int outputTokens;
        }
    }
}

