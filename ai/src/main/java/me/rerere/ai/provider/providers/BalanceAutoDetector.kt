/*
 * 灵犀 Lingxi - 通用余额自动检测
 */

package me.rerere.ai.provider.providers

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import me.rerere.ai.util.json
import me.rerere.common.http.getByKey
import okhttp3.OkHttpClient
import okhttp3.Request

private data class BalanceDetector(val path: String, val resultPath: String)

private val BALANCE_DETECTORS = listOf(
    BalanceDetector("/v1/dashboard/billing/usage", "total_usage"),
    BalanceDetector("/user/balance", "balance_infos[0].total_balance"),
    BalanceDetector("/user/balance", "available_balance"),
    BalanceDetector("/user/info", "data.totalBalance"),
    BalanceDetector("/credits", "data.total_credits"),
    BalanceDetector("/credits", "total_credits"),
    BalanceDetector("/dashboard/billing/usage", "total_usage"),
    BalanceDetector("/v1/usage", "total_usage"),
    BalanceDetector("*", "*"),
)

private val COMMON_BALANCE_KEYS = listOf(
    "total_balance", "available_balance", "total_credits",
    "total_usage", "balance", "credits", "remaining",
    "total_granted", "total_available", "hard_limit_usd",
)

suspend fun autoDetectBalance(
    client: OkHttpClient,
    baseUrl: String,
    apiKey: String,
    customPath: String = "",
    customResultPath: String = "",
): String {
    val detectors = mutableListOf<BalanceDetector>()
    if (customPath.isNotBlank()) {
        detectors.add(BalanceDetector(customPath, customResultPath.ifBlank { "*" }))
    }
    detectors.addAll(BALANCE_DETECTORS)

    for (detector in detectors.distinctBy { "${it.path}|${it.resultPath}" }) {
        try {
            val url = if (detector.path.startsWith("http")) detector.path
            else "$baseUrl${detector.path}"

            val request = Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer $apiKey")
                .get().build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) continue

            val bodyStr = response.body?.string() ?: continue
            val bodyElement = json.parseToJsonElement(bodyStr)
            val bodyJson = bodyElement.jsonObject

            if (detector.resultPath != "*") {
                val value = bodyElement.getByKey(detector.resultPath)
                value.toFloatOrNull()?.let { if (it > 0) return "%.2f".format(it) }
                if (value.isNotBlank() && value != "null") return value
            }

            val jsonObj = bodyJson.jsonObject
            for (ck in COMMON_BALANCE_KEYS) {
                jsonObj[ck]?.jsonPrimitive?.contentOrNull?.toFloatOrNull()?.let {
                    if (it > 0) return "%.2f".format(it)
                }
                val nested = bodyElement.getByKey("data.$ck")
                nested.toFloatOrNull()?.let { if (it > 0) return "%.2f".format(it) }
            }
        } catch (_: Exception) { }
    }

    error("无法获取余额: 所有已知接口均未返回有效数据")
}
