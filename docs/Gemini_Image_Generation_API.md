# Gemini 图像生成 API 文档

## 概述

本文档描述了 Google Gemini 模型的图像生成请求接口，支持通过文本描述和参考图片生成图像。

---

## 1. 图像生成接口

### 1.1 接口信息

**接口地址：**
```
POST {baseUrl}/v1beta/models/{modelEndpoint}:generateContent
```

**路径参数：**
- `baseUrl`: API 基础地址（例如：`https://yunwu.ai` 或官方地址）
- `modelEndpoint`: 模型端点名称
  - `gemini-2.5-flash-image` - Gemini 2.5 Flash 模型
  - `gemini-3-pro-image-preview` - Gemini 3 Pro 模型

**完整示例：**
```
POST https://yunwu.ai/v1beta/models/gemini-2.5-flash-image:generateContent
POST https://yunwu.ai/v1beta/models/gemini-3-pro-image-preview:generateContent
```

---

### 1.2 请求头（Headers）

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `Content-Type` | String | 是 | `application/json` |
| `Authorization` | String | 条件 | Bearer Token 认证方式时使用 |
| `x-goog-api-key` | String | 条件 | 官方 API Key 认证方式时使用 |

**认证方式说明：**

**方式一：Bearer Token（第三方代理）**
```
Authorization: Bearer {token}
```

**方式二：官方 API Key**
```
x-goog-api-key: {api_key}
```

---

### 1.3 请求体（Request Body）

#### 1.3.1 完整结构

```json
{
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "text": "a beautiful sunset over the ocean"
        },
        {
          "inlineData": {
            "mimeType": "image/png",
            "data": "iVBORw0KGgoAAAANSUhEUgAA..."
          }
        }
      ]
    }
  ],
  "generationConfig": {
    "responseModalities": ["IMAGE"],
    "imageConfig": {
      "aspectRatio": "16:9",
      "imageSize": "2K"
    }
  }
}
```

#### 1.3.2 字段说明

**根对象：**

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `contents` | Array | 是 | 消息内容数组 |
| `generationConfig` | Object | 是 | 生成配置 |

**contents 数组项：**

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `role` | String | 是 | 角色，固定为 `"user"` |
| `parts` | Array | 是 | 内容部分数组 |

**parts 数组项（支持两种类型）：**

**类型一：文本内容**
```json
{
  "text": "图像描述文本"
}
```

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `text` | String | 是 | 图像生成的文本提示词 |

**类型二：图片内容**
```json
{
  "inlineData": {
    "mimeType": "image/png",
    "data": "base64编码的图片数据"
  }
}
```

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `inlineData` | Object | 是 | 内联图片数据 |
| `inlineData.mimeType` | String | 是 | MIME 类型，如 `image/png`、`image/jpeg`、`image/webp` |
| `inlineData.data` | String | 是 | Base64 编码的图片数据（不含 `data:image/...;base64,` 前缀） |

**generationConfig 对象：**

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `responseModalities` | Array | 是 | 响应模态，固定为 `["IMAGE"]` |
| `imageConfig` | Object | 是 | 图像配置 |

**imageConfig 对象：**

| 字段名 | 类型 | 必填 | 说明 |
|--------|------|------|------|
| `aspectRatio` | String | 是 | 宽高比，可选值见下表 |
| `imageSize` | String | 否 | 图像尺寸（仅 Gemini 3 Pro 支持），可选值见下表 |

**aspectRatio 可选值：**

| 值 | 说明 | 比例 |
|----|------|------|
| `"1:1"` | 正方形 | 1:1 |
| `"2:3"` | 肖像 | 2:3 |
| `"3:2"` | 风景 | 3:2 |
| `"3:4"` | 标准竖版 | 3:4 |
| `"4:3"` | 标准横版 | 4:3 |
| `"16:9"` | 宽屏 | 16:9 |
| `"9:16"` | 手机竖屏 | 9:16 |

**imageSize 可选值（仅 Gemini 3 Pro）：**

| 值 | 说明 |
|----|------|
| `"1K"` | 1K 分辨率 |
| `"2K"` | 2K 分辨率 |
| `"4K"` | 4K 分辨率 |

---

### 1.4 响应结构（Response）

#### 1.4.1 成功响应（200 OK）

**响应示例：**
1. nano banana(gemini-2.5-flash-image)
```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "好的，请看这是为您打造的全身照，保持了模特的姿态和面部表情，并搭配了您选择的服装和配饰：\n\n"
          },
          {
            "inlineData": {
              "mimeType": "image/png",
              "data": "iVBORw0KGgoAAAANSUhEUgAA..."
            }
          }
        ]
      }
    }
  ],
  "promptFeedback": {
      "safetyRatings": null
  },
  "usageMetadata": {
      "promptTokenCount": 586,
      "candidatesTokenCount": 1324,
      "totalTokenCount": 1910,
      "thoughtsTokenCount": 0,
      "promptTokensDetails": [
          {
              "modality": "TEXT",
              "tokenCount": 70
          },
          {
              "modality": "IMAGE",
              "tokenCount": 516
          }
      ]
  },
  "modelVersion": "gemini-2.5-flash-image",
  "responseId": "yCVzafuvPOu9z7IPqMyZ6Qc"
}
```

2. nano banana pro(gemini-3-pro-image-preview)
```json
{
    "candidates": [
        {
            "content": {
                "role": "model",
                "parts": [
                    {
                        "thoughtSignature": "EqCzQgqcs0IBcsjafEVyOV...",
                        "inlineData": {
                            "mimeType": "image/jpeg",
                            "data": "/9j/4AAQSkZJRgABAQEB..."
                        }
                    }
                ]
            },
            "finishReason": "STOP",
            "index": 0,
            "safetyRatings": null
        }
    ],
    "promptFeedback": {
        "safetyRatings": null
    },
    "usageMetadata": {
        "promptTokenCount": 147,
        "candidatesTokenCount": 1220,
        "totalTokenCount": 1582,
        "thoughtsTokenCount": 215,
        "promptTokensDetails": [
            {
                "modality": "TEXT",
                "tokenCount": 147
            }
        ]
    },
    "modelVersion": "gemini-3-pro-image-preview",
    "responseId": "KStzabiTDuClqtsP9KiFkQE"
}
```

**响应字段说明：**

| 字段名 | 类型 | 说明 |
|--------|------|------|
| `candidates` | Array | 候选结果数组 |
| `candidates[].content` | Object | 内容对象 |
| `candidates[].content.parts` | Array | 内容部分数组 |
| `candidates[].content.parts[].inlineData` | Object | 图片数据对象 |
| `candidates[].content.parts[].inlineData.mimeType` | String | 图片 MIME 类型 |
| `candidates[].content.parts[].inlineData.data` | String | Base64 编码的图片数据 |

**注意：**
- 响应可能包含多张图片（一次请求生成多张）
- 响应体可能很大，建议使用流式解析
- Base64 数据字段名可能为 `"data"` 或 `"data": "`（带空格）

#### 1.4.2 错误响应

**HTTP 状态码：** 非 200-299 范围

**响应体示例：**
```json
{
  "error": {
    "code": 400,
    "message": "Invalid request",
    "status": "INVALID_ARGUMENT"
  }
}
```

**常见错误码：**

| HTTP 状态码 | 说明 |
|------------|------|
| 400 | 请求参数错误 |
| 401 | 认证失败 |
| 403 | 权限不足 |
| 429 | 请求频率限制 |
| 500 | 服务器内部错误 |

---

## 2. 代码示例

### 3.1 Kotlin 示例（使用 Ktor）

```kotlin
import io.ktor.client.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.serialization.json.*

suspend fun generateImage(
    baseUrl: String,
    modelEndpoint: String,
    apiKey: String,
    prompt: String,
    images: List<ImageData>,
    aspectRatio: String,
    imageSize: String? = null
): List<String> {
    val endpoint = "$baseUrl/v1beta/models/$modelEndpoint:generateContent"
    
    // 构建 parts 数组
    val parts = buildJsonArray {
        add(buildJsonObject { put("text", prompt) })
        images.forEach { img ->
            add(buildJsonObject {
                put("inlineData", buildJsonObject {
                    put("mimeType", img.mimeType)
                    put("data", img.base64Data)
                })
            })
        }
    }
    
    // 构建 imageConfig
    val imageConfig = buildJsonObject {
        put("aspectRatio", aspectRatio)
        imageSize?.let { put("imageSize", it) }
    }
    
    // 构建请求体
    val requestBody = buildJsonObject {
        put("contents", buildJsonArray {
            add(buildJsonObject {
                put("role", "user")
                put("parts", parts)
            })
        })
        put("generationConfig", buildJsonObject {
            put("responseModalities", buildJsonArray { add("IMAGE") })
            put("imageConfig", imageConfig)
        })
    }
    
    // 发送请求
    val response = httpClient.post(endpoint) {
        header("x-goog-api-key", apiKey)
        contentType(ContentType.Application.Json)
        setBody(requestBody.toString())
    }
    
    // 解析响应（流式解析）
    return parseImageResponse(response)
}

data class ImageData(
    val mimeType: String,
    val base64Data: String
)
```

### 3.2 JavaScript 示例

```javascript
async function generateImage(baseUrl, modelEndpoint, apiKey, prompt, images, aspectRatio, imageSize) {
    const endpoint = `${baseUrl}/v1beta/models/${modelEndpoint}:generateContent`;
    
    // 构建 parts 数组
    const parts = [
        { text: prompt },
        ...images.map(img => ({
            inlineData: {
                mimeType: img.mimeType,
                data: img.base64Data
            }
        }))
    ];
    
    // 构建 imageConfig
    const imageConfig = {
        aspectRatio: aspectRatio
    };
    if (imageSize) {
        imageConfig.imageSize = imageSize;
    }
    
    // 构建请求体
    const requestBody = {
        contents: [{
            role: "user",
            parts: parts
        }],
        generationConfig: {
            responseModalities: ["IMAGE"],
            imageConfig: imageConfig
        }
    };
    
    // 发送请求
    const response = await fetch(endpoint, {
        method: 'POST',
        headers: {
            'Content-Type': 'application/json',
            'x-goog-api-key': apiKey
        },
        body: JSON.stringify(requestBody)
    });
    
    // 解析响应
    const data = await response.json();
    return extractImagesFromResponse(data);
}
```

---

## 3. 注意事项

### 4.1 图片数据格式

- 图片必须以 Base64 编码
- 不需要包含 `data:image/...;base64,` 前缀
- 支持的 MIME 类型：`image/png`、`image/jpeg`、`image/webp`

### 4.2 流式响应处理

- 响应体可能很大，建议使用流式解析
- 响应中可能包含多张图片
- Base64 数据可能跨多个数据块，需要拼接处理

### 4.3 模型差异

| 特性 | Gemini 2.5 Flash | Gemini 3 Pro |
|------|------------------|--------------|
| 图像尺寸配置 | ❌ 不支持 | ✅ 支持（1K/2K/4K） |
| 生成速度 | 较快 | 较慢 |
| 图像质量 | 良好 | 优秀 |

### 4.4 错误处理

- 网络超时：建议设置合理的超时时间（如 300 秒）
- 认证失败：检查 API Key 或 Token 是否正确
- 频率限制：实现请求重试机制，使用指数退避策略
- 内存管理：使用流式解析避免 OOM

---

## 4. 参考资源

- [Gemini API 官方文档](https://ai.google.dev/docs)
- [图像生成最佳实践](https://ai.google.dev/docs/gemini_api_overview)

---

**文档版本：** 1.0  
**最后更新：** 2026-01-23  
**维护者：** Live Wallpaper Project
