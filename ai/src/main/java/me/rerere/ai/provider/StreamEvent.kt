package me.rerere.ai.provider

import me.rerere.ai.ui.MessageChunk

/**
 * 统一 Provider 流事件抽象（#1648 重构的第一步）。
 *
 * 目标：让各 Provider 只发出通用流事件，把流式处理（合并/Transform）从
 * UIMessage 中拆分出来，减少 Provider 层重复工作。
 *
 * 当前为纯新增抽象层（不改变现有 Provider 实现），后续可逐步迁移：
 * 1. Provider 内部把 MessageChunk 转换为 [ProviderStreamEvent]
 * 2. GenerationHandler 消费 [ProviderStreamEvent] 统一做增量合并
 * 3. 保持 [MessageChunk] 作为对外稳定接口，兼容现有 UI
 */
sealed interface ProviderStreamEvent {
    /** 增量文本/思考/工具片段 */
    data class Delta(val chunk: MessageChunk) : ProviderStreamEvent

    /** 整段消息（非流式 provider 或恢复流时使用） */
    data class Full(val chunk: MessageChunk) : ProviderStreamEvent

    /** 流结束（含用量汇总） */
    data class Done(val usage: me.rerere.ai.core.TokenUsage? = null) : ProviderStreamEvent

    /** 流错误（可携带部分已产出内容） */
    data class Error(val throwable: Throwable, val partial: MessageChunk? = null) : ProviderStreamEvent
}
