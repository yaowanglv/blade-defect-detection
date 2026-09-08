package com.example.springb.service;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * AI大模型服务
 * 支持DeepSeek、GLM(智谱)、Kimi等主流大模型API调用
 */
@Service
public class AIService {

    private static final Logger log = LoggerFactory.getLogger(AIService.class);
    private static final String AI_CONFIG_GROUP = "ai_model";

    @Resource
    private SystemConfigService systemConfigService;

    // DeepSeek配置
    @Value("${ai.models.deepseek.api-key:}")
    private String deepseekApiKey;

    @Value("${ai.models.deepseek.endpoint:https://api.deepseek.com/chat/completions}")
    private String deepseekEndpoint;

    @Value("${ai.models.deepseek.model-name:deepseek-v4-flash}")
    private String deepseekModel;

    // GLM(智谱)配置
    @Value("${ai.models.glm.api-key:}")
    private String glmApiKey;

    @Value("${ai.models.glm.endpoint:https://open.bigmodel.cn/api/paas/v4/chat/completions}")
    private String glmEndpoint;

    @Value("${ai.models.glm.model-name:glm-4}")
    private String glmModel;

    // Kimi配置
    @Value("${ai.models.kimi.api-key:}")
    private String kimiApiKey;

    @Value("${ai.models.kimi.endpoint:https://api.moonshot.cn/v1/chat/completions}")
    private String kimiEndpoint;

    @Value("${ai.models.kimi.model-name:${KIMI_MODEL:kimi-k2.5-preview}}")
    private String kimiModel;

    /**
     * 调用AI模型进行分析
     *
     * @param model  模型名称: deepseek/glm/doubao
     * @param prompt 提示词
     * @return AI分析结果
     */
    public String analyze(String model, String prompt) {
        switch (model.toLowerCase()) {
            case "deepseek":
                return callDeepSeek(prompt);
            case "glm":
                return callGLM(prompt);
            case "kimi":
                return callKimi(prompt);
            default:
                throw new IllegalArgumentException("不支持的AI模型: " + model);
        }
    }

    /**
     * 测试AI模型连接，使用更短的输出长度以减少测试调用成本。
     */
    public String testConnection(String model) {
        switch (model.toLowerCase()) {
            case "deepseek":
                return callDeepSeek("你好，请只回复：连接成功", 32);
            case "glm":
                return callGLM("你好，请只回复：连接成功", 32);
            case "kimi":
                return callKimi("你好，请只回复：连接成功", 32);
            default:
                throw new IllegalArgumentException("不支持的AI模型: " + model);
        }
    }

    /**
     * 调用DeepSeek API
     */
    private String callDeepSeek(String prompt) {
        return callDeepSeek(prompt, 2000);
    }

    private String callDeepSeek(String prompt, int maxTokens) {
        String apiKey = normalizeApiKey(resolveApiKey("deepseek"));
        if (!isValidApiKey(apiKey)) {
            throw new RuntimeException("DeepSeek API Key未配置");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", deepseekModel);
        requestBody.set("messages", new JSONArray()
            .put(new JSONObject()
                .set("role", "system")
                .set("content", "你是一位专业的风力发电机缺陷检测与运维分析专家，擅长分析风机图像检测结果并提供巡检维护建议。"))
            .put(new JSONObject()
                .set("role", "user")
                .set("content", prompt))
        );
        requestBody.set("temperature", 0.7);
        requestBody.set("max_tokens", maxTokens);

        String endpoint = deepseekEndpoint == null ? "" : deepseekEndpoint.trim();
        log.info("调用DeepSeek API, endpoint: {}, model: {}, key: {}", endpoint, deepseekModel, maskApiKey(apiKey));

        HttpResponse response = HttpRequest.post(endpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        if (response.getStatus() == 401) {
            log.error("DeepSeek API鉴权失败: status={}, body={}", response.getStatus(), response.body());
            throw new RuntimeException("DeepSeek API鉴权失败(HTTP 401)：请检查 DEEPSEEK_API_KEY 或 application.yml 中的密钥是否为 DeepSeek 开放平台的真实 API Key，确认没有空格、引号或占位值，并重启后端服务");
        }

        return parseResponse(response, "DeepSeek");
    }

    /**
     * 调用GLM(智谱) API
     */
    private String callGLM(String prompt) {
        return callGLM(prompt, 2000);
    }

    private String callGLM(String prompt, int maxTokens) {
        String apiKey = normalizeApiKey(resolveApiKey("glm"));
        if (!isValidApiKey(apiKey)) {
            throw new RuntimeException("GLM API Key未配置");
        }

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", glmModel);
        requestBody.set("messages", new JSONArray()
            .put(new JSONObject()
                .set("role", "system")
                .set("content", "你是一位专业的风力发电机缺陷检测与运维分析专家，擅长分析风机图像检测结果并提供巡检维护建议。"))
            .put(new JSONObject()
                .set("role", "user")
                .set("content", prompt))
        );
        requestBody.set("temperature", 0.7);
        requestBody.set("max_tokens", maxTokens);

        log.info("调用GLM API, model: {}, key: {}", glmModel, maskApiKey(apiKey));

        HttpResponse response = HttpRequest.post(glmEndpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        return parseResponse(response, "GLM");
    }

    /**
     * 调用Kimi API
     */
    private String callKimi(String prompt) {
        return callKimi(prompt, 2000);
    }

    private String callKimi(String prompt, int maxTokens) {
        String apiKey = normalizeApiKey(resolveApiKey("kimi"));
        if (!isValidApiKey(apiKey)) {
            throw new RuntimeException("Kimi API Key未配置");
        }

        log.info("调用Kimi API, model: {}, key: {}", kimiModel, maskApiKey(apiKey));

        JSONObject requestBody = new JSONObject();
        requestBody.set("model", kimiModel);
        requestBody.set("messages", new JSONArray()
            .put(new JSONObject()
                .set("role", "system")
                .set("content", "你是一位专业的风力发电机缺陷检测与运维分析专家，擅长分析风机图像检测结果并提供巡检维护建议。"))
            .put(new JSONObject()
                .set("role", "user")
                .set("content", prompt))
        );
        requestBody.set("temperature", 0.7);
        requestBody.set("max_tokens", maxTokens);

        log.debug("Kimi请求体: {}", requestBody.toString());

        HttpResponse response = HttpRequest.post(kimiEndpoint)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody.toString())
                .timeout(60000)
                .execute();

        log.info("Kimi响应状态: {}, 内容: {}", response.getStatus(), response.body());
        return parseResponse(response, "Kimi");
    }

    /**
     * 解析API响应
     */
    private String parseResponse(HttpResponse response, String modelName) {
        if (response.getStatus() != 200) {
            log.error("{} API响应错误: {}, body: {}", modelName, response.getStatus(), response.body());
            throw new RuntimeException(buildApiErrorMessage(response, modelName));
        }

        String body = response.body();
        JSONObject jsonResponse = JSONUtil.parseObj(body);

        // 检查错误
        if (jsonResponse.containsKey("error")) {
            JSONObject error = jsonResponse.getJSONObject("error");
            String errorCode = error.getStr("code", "");
            String errorMsg = error.getStr("message", "未知错误");
            log.error("{} API返回错误: code={}, message={}", modelName, errorCode, errorMsg);
            throw new RuntimeException(formatProviderError(modelName, 200, errorCode, errorMsg));
        }

        // 提取内容
        JSONArray choices = jsonResponse.getJSONArray("choices");
        if (choices == null || choices.isEmpty()) {
            throw new RuntimeException(modelName + " API返回结果为空");
        }

        JSONObject message = choices.getJSONObject(0).getJSONObject("message");
        String content = message.getStr("content", "");

        log.info("{} API调用成功, 返回内容长度: {}", modelName, content.length());
        return content.trim();
    }

    private String buildApiErrorMessage(HttpResponse response, String modelName) {
        String body = response.body();
        String errorCode = "";
        String errorMsg = "";
        if (body != null && !body.trim().isEmpty()) {
            try {
                JSONObject jsonResponse = JSONUtil.parseObj(body);
                if (jsonResponse.containsKey("error")) {
                    JSONObject error = jsonResponse.getJSONObject("error");
                    errorCode = error.getStr("code", "");
                    errorMsg = error.getStr("message", "");
                } else {
                    errorCode = jsonResponse.getStr("code", "");
                    errorMsg = jsonResponse.getStr("message", jsonResponse.getStr("msg", ""));
                }
            } catch (Exception ex) {
                errorMsg = body.length() > 300 ? body.substring(0, 300) : body;
            }
        }
        return formatProviderError(modelName, response.getStatus(), errorCode, errorMsg);
    }

    private String formatProviderError(String modelName, int httpStatus, String errorCode, String errorMsg) {
        StringBuilder message = new StringBuilder();
        message.append(modelName).append(" API调用失败: HTTP ").append(httpStatus);
        if (errorCode != null && !errorCode.trim().isEmpty()) {
            message.append(", 业务错误码 ").append(errorCode.trim());
        }
        if (errorMsg != null && !errorMsg.trim().isEmpty()) {
            message.append(", ").append(errorMsg.trim());
        }
        if ("GLM".equalsIgnoreCase(modelName) && httpStatus == 429) {
            message.append("。根据智谱AI官方文档，429通常表示请求并发/频率超限、账户余额不足、账户异常或模型调用限额已达上限；请稍后重试，检查智谱控制台余额与速率限制，必要时切换低负载模型或联系智谱商务/客服。");
        }
        return message.toString();
    }

    /**
     * 检查指定模型是否已配置API Key
     */
    public boolean isConfigured(String model) {
        return isValidApiKey(resolveApiKey(model));
    }

    /**
     * 获取模型配置状态
     */
    public Map<String, Boolean> getConfigStatus() {
        Map<String, Boolean> status = new HashMap<>();
        status.put("deepseek", isConfigured("deepseek"));
        status.put("glm", isConfigured("glm"));
        status.put("kimi", isConfigured("kimi"));
        return status;
    }

    /**
     * 解析 API Key: 优先读取数据库配置，数据库为空时回退到 application.yml。
     */
    private String resolveApiKey(String model) {
        if (model == null || model.trim().isEmpty()) {
            return "";
        }

        String normalizedModel = model.trim().toLowerCase(Locale.ROOT);
        String dbValue = systemConfigService.getValue(normalizedModel + "_api_key", AI_CONFIG_GROUP);
        if (isValidApiKey(dbValue)) {
            return dbValue;
        }

        switch (normalizedModel) {
            case "deepseek":
                return deepseekApiKey;
            case "glm":
                return glmApiKey;
            case "kimi":
                return kimiApiKey;
            default:
                return "";
        }
    }

    private String normalizeApiKey(String apiKey) {
        if (apiKey == null) {
            return "";
        }
        String key = apiKey.trim();
        if (key.toLowerCase().startsWith("bearer ")) {
            key = key.substring("Bearer ".length()).trim();
        }
        return key;
    }

    private boolean isValidApiKey(String apiKey) {
        String key = normalizeApiKey(apiKey);
        if (key.isEmpty()) {
            return false;
        }
        String lowerKey = key.toLowerCase();
        return !lowerKey.contains("your_")
                && !lowerKey.contains("your-")
                && !lowerKey.contains("api_key_here")
                && !lowerKey.contains("placeholder");
    }

    private String maskApiKey(String apiKey) {
        String key = normalizeApiKey(apiKey);
        if (key.length() <= 8) {
            return "****";
        }
        return key.substring(0, 4) + "****" + key.substring(key.length() - 4);
    }
}
