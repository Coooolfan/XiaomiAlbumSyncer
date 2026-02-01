# XiaoMiApi 删除照片 API 测试说明

## 测试文件

`XiaoMiApiDeleteAssetTest.kt` - 包含 `deleteAsset` 方法的单元测试

## 测试场景

### 1. 成功删除照片
- **预期行为**：当 API 返回 `result="ok"` 且 `code=0` 时，方法应该返回 `true`
- **验证点**：
  - 返回值为 `true`
  - 记录成功日志

### 2. API 返回错误代码
- **预期行为**：当 API 返回 `result!="ok"` 或 `code!=0` 时，方法应该返回 `false`
- **测试用例**：
  - `result="error"`, `code=50050` (照片不存在)
  - `result="ok"`, `code=1` (部分成功)
  - `result="fail"`, `code=0` (失败)

### 3. 网络错误
- **预期行为**：当发生网络错误时，方法应该捕获异常并返回 `false`
- **测试用例**：
  - HTTP 500 Internal Server Error
  - HTTP 404 Not Found
  - 连接超时
  - JSON 解析错误

## 当前测试状态

由于项目中没有 MockWebServer 和 Mockito 依赖，当前的测试被标记为 `@Disabled`。

要运行这些测试，有两种方案：

### 方案 1：添加测试依赖（推荐）

在 `build.gradle.kts` 中添加：

```kotlin
dependencies {
    // ... 现有依赖 ...
    
    // 测试依赖
    testImplementation("com.squareup.okhttp3:mockwebserver:5.3.2")
    testImplementation("org.mockito.kotlin:mockito-kotlin:5.2.1")
}
```

然后重写测试以使用 MockWebServer 模拟 HTTP 响应。

### 方案 2：集成测试

使用真实的账号和照片 ID 进行集成测试（不推荐用于自动化测试）。

## 运行测试

```bash
# 运行所有测试
./gradlew test

# 运行特定测试类
./gradlew test --tests "XiaoMiApiDeleteAssetTest"

# 运行特定测试方法
./gradlew test --tests "XiaoMiApiDeleteAssetTest.测试方法存在性"
```

## 测试覆盖的代码逻辑

`deleteAsset` 方法的实现逻辑：

1. 构建 HTTP POST 请求到 `https://i.mi.com/gallery/info/delete`
2. 添加认证头（通过 TokenManager）
3. 发送请求参数：`id` 和 `serviceToken`
4. 解析 JSON 响应
5. 检查 `result` 和 `code` 字段
6. 返回 `true`（成功）或 `false`（失败）
7. 捕获异常并返回 `false`

## 注意事项

1. **认证**：测试需要有效的 TokenManager 来提供认证信息
2. **网络**：集成测试需要网络连接
3. **副作用**：删除操作会实际删除云端照片，测试时要小心
4. **日志**：测试会产生日志输出，注意检查日志级别

## 改进建议

1. 添加 MockWebServer 依赖以支持单元测试
2. 创建测试专用的 TokenManager mock
3. 添加更多边界情况测试
4. 添加性能测试（批量删除）
5. 添加并发测试
