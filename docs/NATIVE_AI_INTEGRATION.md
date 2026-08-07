# 原生 AI 集成指南（#34 MNN / #1657 sqlite-vec）

这两个功能需要编译 Android 原生库（`.so`）。本文档提供完整接入路径，
代码骨架已在项目预留，构建 `.so` 后放入即可启用。

## 一、#1657 sqlite-vec 向量检索（C 扩展）

### 1. 获取/编译 so
- 官方仓库: https://github.com/asg017/sqlite-vec
- Android 编译（需要 NDK）:
  ```bash
  git clone https://github.com/asg017/sqlite-vec
  cd sqlite-vec
  # 用 Android NDK 交叉编译 aarch64-linux-android
  CC=$NDK_HOME/toolchains/llvm/prebuilt/linux-x86_64/bin/aarch64-linux-android21-clang \
    make loadable
  ```
- 产出 `dist/vec0.so`，复制到 `app/src/main/jniLibs/arm64-v8a/libvec0.so`

### 2. 代码接入点（已预留骨架）
在 `KnowledgeBaseTool.kt` 的 `knowledge_search` 中，当前使用词频评分。
接入向量检索后，将 `scoreDoc` 替换为 sqlite-vec 的余弦相似度查询：
```kotlin
// 加载 so（首次调用时）
System.loadLibrary("vec0")
// 通过 SQLite + sqlite-vec 扩展执行
// SELECT rowid, distance FROM vec_documents
// WHERE embedding MATCH ? ORDER BY distance LIMIT ?
```

### 3. 嵌入向量来源
- 可选方案：调用 LLM 生成文本嵌入（`generateEmbedding` 已实现）
- 或本地哈希/词袋向量（离线、零成本）

## 二、#34 MNN 本地 LLM 推理

### 1. 获取/编译 MNN
- 官方仓库: https://github.com/alibaba/MNN
- 编译 Android so:
  ```bash
  cd MNN
  ./schema/generate.sh
  mkdir build && cd build
  cmake .. -DANDROID_ABI=arm64-v8a -DCMAKE_TOOLCHAIN_FILE=$NDK/build/cmake/android.toolchain.cmake
  make MNN
  ```
- 产出 `libMNN.so` → `app/src/main/jniLibs/arm64-v8a/libMNN.so`

### 2. 模型文件
- 用户需准备量化后的 MNN 模型（如 Qwen2-0.5B 转换）
- 放入 `Android/data/<pkg>/files/models/` 或内置 assets

### 3. 代码接入点
标题总结等轻量场景：
```kotlin
// 新增 MnnTTS/标题生成 Provider 时：
System.loadLibrary("MNN")
val interpreter = MNNInterpreter.createFromFile(modelPath)
// interpreter.runSession(...) 输入 prompt，输出 token
```

### 4. 接入位置
- `generateTitle` 的本地回退可配置为 MNN 推理
- 或新增 `MnnProvider` 接入 Provider 体系

## 构建 CI 说明
- GitHub Actions 已配置 Android SDK
- 如需自动编译 so，在 workflow 中加 NDK 安装步骤
- 或使用 sqlite-vec/MNN 官方发布的预编译 aar（如有）

## 现状
- #1657: 纯 Kotlin 词频检索基础版已可用（`knowledge_search`）
- #34: 本地规则标题已可用（`fallbackTitleFromFirstMessage`）
- 完整向量/本地 LLM 能力在 so 就位后自动升级
