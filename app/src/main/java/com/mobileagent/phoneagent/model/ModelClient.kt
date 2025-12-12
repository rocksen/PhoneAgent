/**
 * AI 模型客户端 - 通过 HTTP API 调用远程模型服务
 * 
 * 项目地址: https://github.com/MR-MaoJiu/PhoneAgent
 * 
 * 支持多个AI服务商，兼容不同的输入输出格式
 */
package com.mobileagent.phoneagent.model

import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/**
 * AI 模型客户端 - 通过 HTTP API 调用远程模型服务
 * 支持多个AI服务商，兼容不同的输入输出格式
 */
class ModelClient(
    private val baseUrl: String,
    private val modelName: String,
    private val apiKey: String = "ollama",
    private val provider: ModelProvider = ModelProvider.OLLAMA,
    private val temperature: Float = 0.1f,
    private val topP: Float = 0.85f
) {
    private val TAG = "ModelClient"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val gson = Gson()
    private val mediaType = "application/json".toMediaType()

    /**
     * 发送请求到模型
     */
    suspend fun request(messages: List<Message>): ModelResponse = withContext(Dispatchers.IO) {
        Log.d(TAG, "========================================")
        Log.d(TAG, "🤖 开始调用 AI 模型")
        Log.d(TAG, "URL: $baseUrl/chat/completions")
        Log.d(TAG, "模型: $modelName")
        Log.d(TAG, "消息数量: ${messages.size}")
        
        // 根据服务商构建消息 JSON，支持不同的格式
        val messagesJson = messages.map { message ->
            val messageObj = JsonObject()
            messageObj.addProperty("role", message.role)
            
            when (val content = message.content) {
                is String -> {
                    messageObj.addProperty("content", content)
                }
                is List<*> -> {
                    // 根据服务商使用不同的内容格式
                    when (provider.responseFormat) {
                        ResponseFormat.OPENAI_COMPATIBLE, ResponseFormat.GLM -> {
                            // OpenAI兼容格式和GLM格式：content数组
                            val contentArray = com.google.gson.JsonArray()
                            content.filterIsInstance<ContentItem>().forEach { item ->
                                val itemObj = JsonObject()
                                itemObj.addProperty("type", item.type)
                                
                                when (item.type) {
                                    "text" -> {
                                        item.text?.let { itemObj.addProperty("text", it) }
                                    }
                                    "image_url" -> {
                                        val imageUrlObj = JsonObject()
                                        // 根据服务商的图片格式处理
                                        val imageUrl = when (provider.imageFormat) {
                                            ImageFormat.DATA_URL -> {
                                                item.imageUrl?.url ?: ""
                                            }
                                            ImageFormat.BASE64 -> {
                                                // 提取base64部分
                                                val url = item.imageUrl?.url ?: ""
                                                if (url.startsWith("data:image")) {
                                                    url.substringAfter(",")
                                                } else {
                                                    url
                                                }
                                            }
                                        }
                                        imageUrlObj.addProperty("url", imageUrl)
                                        itemObj.add("image_url", imageUrlObj)
                                    }
                                }
                                contentArray.add(itemObj)
                            }
                            messageObj.add("content", contentArray)
                        }
                        ResponseFormat.ANTHROPIC -> {
                            // Anthropic格式：content数组，图片使用base64
                            val contentArray = com.google.gson.JsonArray()
                            content.filterIsInstance<ContentItem>().forEach { item ->
                                val itemObj = JsonObject()
                                when (item.type) {
                                    "text" -> {
                                        itemObj.addProperty("type", "text")
                                        item.text?.let { itemObj.addProperty("text", it) }
                                    }
                                    "image_url" -> {
                                        itemObj.addProperty("type", "image")
                                        val imageUrlObj = JsonObject()
                                        val url = item.imageUrl?.url ?: ""
                                        val base64Data = if (url.startsWith("data:image")) {
                                            url.substringAfter(",")
                                        } else {
                                            url
                                        }
                                        imageUrlObj.addProperty("source", base64Data)
                                        imageUrlObj.addProperty("media_type", "image/png")
                                        itemObj.add("source", imageUrlObj)
                                    }
                                }
                                contentArray.add(itemObj)
                            }
                            messageObj.add("content", contentArray)
                        }
                        ResponseFormat.GOOGLE -> {
                            // Google格式：parts数组
                            val partsArray = com.google.gson.JsonArray()
                            content.filterIsInstance<ContentItem>().forEach { item ->
                                when (item.type) {
                                    "text" -> {
                                        val textObj = JsonObject()
                                        item.text?.let { textObj.addProperty("text", it) }
                                        partsArray.add(textObj)
                                    }
                                    "image_url" -> {
                                        val imageObj = JsonObject()
                                        val inlineDataObj = JsonObject()
                                        val url = item.imageUrl?.url ?: ""
                                        val base64Data = if (url.startsWith("data:image")) {
                                            url.substringAfter(",")
                                        } else {
                                            url
                                        }
                                        inlineDataObj.addProperty("mime_type", "image/png")
                                        inlineDataObj.addProperty("data", base64Data)
                                        imageObj.add("inline_data", inlineDataObj)
                                        partsArray.add(imageObj)
                                    }
                                }
                            }
                            messageObj.add("parts", partsArray)
                        }
                    }
                }
                else -> {
                    messageObj.add("content", gson.toJsonTree(content))
                }
            }
            messageObj
        }
        
        val messagesArray = com.google.gson.JsonArray()
        messagesJson.forEach { messagesArray.add(it) }
        
        val requestBody = JsonObject().apply {
            addProperty("model", modelName)
//            addProperty("max_tokens", 30000)
            addProperty("temperature", temperature)
            addProperty("top_p", topP)
            add("messages", messagesArray)
            
            // GLM (智谱AI) 支持思考模式，启用 thinking 字段
            if (provider == ModelProvider.GLM) {
                val thinkingObj = JsonObject()
                thinkingObj.addProperty("type", "enabled")
                add("thinking", thinkingObj)
                Log.d(TAG, "✅ 已启用 GLM 思考模式")
            }
        }

        val requestJson = requestBody.toString()
        Log.d(TAG, "请求体大小: ${requestJson.length} 字符")
        Log.d(TAG, "最后一条消息预览: ${getLastMessagePreview(messages)}")

        // 检查是否包含 thinking 参数（GLM）
        if (provider == ModelProvider.GLM) {
            val hasThinking = requestJson.contains("\"thinking\"")
            Log.d(TAG, "GLM 思考模式检查: ${if (hasThinking) "✅ 已包含 thinking 参数" else "❌ 未找到 thinking 参数"}")
            if (hasThinking) {
                // 提取 thinking 部分用于日志
                val thinkingMatch = "\"thinking\"\\s*:\\s*\\{[^}]+\\}".toRegex().find(requestJson)
                if (thinkingMatch != null) {
                    Log.d(TAG, "Thinking 参数内容: ${thinkingMatch.value}")
                }
            }
        }
        
        Log.d(TAG, "请求 JSON 预览: ${requestJson.take(500)}${if (requestJson.length > 500) "..." else ""}")

        // 根据服务商构建请求URL和Headers
        val requestUrl = when (provider) {
            ModelProvider.GOOGLE -> "$baseUrl/models/$modelName:generateContent"
            else -> "$baseUrl/chat/completions"
        }
        
        val requestBuilder = Request.Builder()
            .url(requestUrl)
            .post(requestJson.toRequestBody(mediaType))
            .addHeader("Content-Type", "application/json")
        
        // 根据服务商添加不同的认证头
        when (provider) {
            ModelProvider.ANTHROPIC -> {
                requestBuilder.addHeader("x-api-key", apiKey)
                requestBuilder.addHeader("anthropic-version", "2023-06-01")
            }
            ModelProvider.GOOGLE -> {
                requestBuilder.addHeader("x-goog-api-key", apiKey)
            }
            ModelProvider.GLM -> {
                // 智谱AI 使用 Authorization: Bearer 格式
                requestBuilder.addHeader("Authorization", "Bearer $apiKey")
            }
            else -> {
                if (apiKey.isNotEmpty() && apiKey != "ollama") {
                    requestBuilder.addHeader("Authorization", "Bearer $apiKey")
                }
            }
        }
        
        val request = requestBuilder.build()

        Log.d(TAG, "发送请求...")
        val startTime = System.currentTimeMillis()
        
        val response = client.newCall(request).execute()
        val responseBody = response.body?.string() ?: throw Exception("响应为空")
        val duration = System.currentTimeMillis() - startTime

        Log.d(TAG, "响应状态: ${response.code}")
        Log.d(TAG, "响应时间: ${duration}ms")
        Log.d(TAG, "响应体: $responseBody")
        Log.d(TAG, "响应体大小: ${responseBody.length} 字符")

        if (!response.isSuccessful) {
            Log.e(TAG, "❌ 请求失败: ${response.code}")
            Log.e(TAG, "错误响应: $responseBody")
            throw Exception("请求失败: ${response.code} - $responseBody")
        }

        val modelResponse = parseResponse(responseBody)
        Log.d(TAG, "✅ 模型响应解析成功")
        Log.d(TAG, "思考过程: ${modelResponse.thinking}")
        Log.d(TAG, "操作指令: ${modelResponse.action}")
        Log.d(TAG, "========================================")
        
        modelResponse
    }
    
    /**
     * 获取最后一条消息的预览
     */
    private fun getLastMessagePreview(messages: List<Message>): String {
        if (messages.isEmpty()) return "无消息"
        val lastMessage = messages.last()
        return when (val content = lastMessage.content) {
            is String -> content.take(100)
            is List<*> -> {
                val items = content.filterIsInstance<ContentItem>()
                items.joinToString(", ") { it.text?.take(50) ?: "图片" }
            }
            else -> "未知类型"
        }
    }

    /**
     * 解析模型响应（支持不同服务商的格式）
     */
    private fun parseResponse(responseBody: String): ModelResponse {
        val json = JsonParser.parseString(responseBody).asJsonObject
        val content: String
        val thinking: String
        
        when (provider.responseFormat) {
            ResponseFormat.GOOGLE -> {
                // Google格式
                val candidates = json.getAsJsonArray("candidates")
                val firstCandidate = candidates[0].asJsonObject
                val contentObj = firstCandidate.getAsJsonObject("content")
                val parts = contentObj.getAsJsonArray("parts")
                val textPart = parts[0].asJsonObject
                content = textPart.get("text").asString
                thinking = "" // Google格式不包含thinking
            }
            ResponseFormat.ANTHROPIC -> {
                // Anthropic格式
                val contentArray = json.getAsJsonArray("content")
                val textContent = contentArray[0].asJsonObject
                content = textContent.get("text").asString
                thinking = "" // Anthropic格式不包含thinking
            }
            ResponseFormat.GLM -> {
                val choices = json.getAsJsonArray("choices")
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                content = message.get("content").asString
                thinking = try {
                    // 优先从 message.reasoning_content 
                    message.get("reasoning_content")?.asString
                        // 兼容其他可能的字段名
                        ?: message.get("thinking")?.asString
                        ?: message.get("reasoning")?.asString
                        // 如果 message 中没有，尝试从 choice 中获取
                        ?: firstChoice.get("reasoning_content")?.asString
                        ?: firstChoice.get("thinking")?.asString
                        ?: firstChoice.get("reasoning")?.asString
                        ?: ""
                } catch (e: Exception) {
                    Log.w(TAG, "获取 GLM thinking 字段失败", e)
                    ""
                }
                
                if (thinking.isNotEmpty()) {
                    Log.d(TAG, "✅ 成功获取 GLM thinking 字段（reasoning_content），长度: ${thinking.length}")
                    Log.d(TAG, "思考内容: ${thinking.take(200)}${if (thinking.length > 200) "..." else ""}")
                } else {
                    Log.w(TAG, "⚠️ 未获取到 GLM thinking 字段，请检查是否启用了思考模式")
                }
            }
            else -> {
                // OpenAI兼容格式
                val choices = json.getAsJsonArray("choices")
                val firstChoice = choices[0].asJsonObject
                val message = firstChoice.getAsJsonObject("message")
                content = message.get("content").asString
                // 尝试获取thinking字段（某些模型支持）
                thinking = try {
                    message.get("reasoning")?.asString
                        ?: message.get("thinking")?.asString
                        ?: ""
                } catch (e: Exception) {
                    ""
                }
            }
        }

        // 解析: <answer> 标签
        val action: String = if (content.contains("<answer>")) {
            val parts = content.split("<answer>", limit = 2)
            parts[1].replace("</answer>", "").trim()
        } else {
            content
        }

        return ModelResponse(
            thinking = thinking,
            action = action,
            rawContent = content
        )
    }
}

/**
 * 消息数据类
 */
data class Message(
    val role: String,
    val content: Any // 可以是 String 或 List<ContentItem>
)

/**
 * 内容项（用于多模态消息）
 */
data class ContentItem(
    val type: String,
    val text: String? = null,
    val imageUrl: ImageUrl? = null
)

/**
 * 图片 URL
 */
data class ImageUrl(
    val url: String
)

/**
 * 模型响应
 */
data class ModelResponse(
    val thinking: String,
    val action: String,
    val rawContent: String
)

