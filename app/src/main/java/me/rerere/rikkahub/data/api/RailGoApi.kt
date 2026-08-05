/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（RailGoApi：火车票/车站查询）
 * 接口基址: https://data.railgo.zenglingkun.cn
 */

package me.rerere.rikkahub.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/**
 * 车站信息.
 */
data class RailStation(
    val name: String,
    val telecode: String,
    val pinyin: String,
)

/**
 * 车站车次查询结果.
 */
data class RailStationResult(
    val name: String,
    val telecode: String,
    val trainList: List<String>,
    val traffic: String,
    val error: String = "",
)

/**
 * 车次详细信息查询结果.
 */
data class RailTrainResult(
    val trainNo: String,
    val bureauName: String,
    val car: String,
    val carOwner: String,
    val rundays: List<String>,
    val timetable: List<String>,
    val error: String = "",
)

/**
 * RailGo 火车票/车站查询 API 客户端（单例）.
 */
object RailGoApi {
    private const val BASE = "https://data.railgo.zenglingkun.cn"

    private val json = Json { ignoreUnknownKeys = true }

    /** 常用车站（内置，供快速选择与拼音匹配） */
    val COMMON_STATIONS: List<RailStation> = listOf(
        RailStation("北京", "BJP", "beijing"),
        RailStation("北京南", "VNP", "beijingnan"),
        RailStation("北京西", "BXP", "beijingxi"),
        RailStation("北京朝阳", "IVP", "beijingchaoyang"),
        RailStation("上海", "SHH", "shanghai"),
        RailStation("上海虹桥", "AOH", "shanghaihongqiao"),
        RailStation("上海南", "SNH", "shanghainan"),
        RailStation("广州", "GZQ", "guangzhou"),
        RailStation("广州南", "IZQ", "guangzhounan"),
        RailStation("深圳", "SZQ", "shenzhen"),
        RailStation("深圳北", "IOQ", "shenzhenbei"),
        RailStation("成都东", "ICW", "chengdudong"),
        RailStation("重庆北", "CUW", "chongqingbei"),
        RailStation("武汉", "WHN", "wuhan"),
        RailStation("杭州东", "HGH", "hangzhoudong"),
        RailStation("南京南", "NKH", "nanjingnan"),
        RailStation("西安北", "EAY", "xianbei"),
        RailStation("天津", "TJP", "tianjin"),
        RailStation("长沙南", "CWQ", "changshanan"),
        RailStation("郑州东", "ZAF", "zhengzhoudong"),
        RailStation("沈阳北", "SBT", "shenyangbei"),
        RailStation("哈尔滨西", "VAB", "haerbinxi"),
        RailStation("石家庄", "SJP", "shijiazhuang"),
        RailStation("济南西", "JGK", "jinanxi"),
        RailStation("青岛", "QDK", "qingdao"),
        RailStation("昆明南", "KOM", "kunmingnan"),
        RailStation("贵阳北", "KQW", "guiyangbei"),
        RailStation("南昌西", "NXG", "nanchangxi"),
        RailStation("福州", "FZS", "fuzhou"),
        RailStation("厦门北", "XKS", "xiamenbei"),
    )

    /** 汉字 → 拼音（用于拼音首字母匹配） */
    private val PINYIN_MAP: Map<Char, String> = mapOf(
        '三' to "san",
        '上' to "shang",
        '东' to "dong",
        '九' to "jiu",
        '亚' to "ya",
        '京' to "jing",
        '佛' to "fo",
        '兰' to "lan",
        '兴' to "xing",
        '北' to "bei",
        '华' to "hua",
        '南' to "nan",
        '厦' to "xia",
        '口' to "kou",
        '合' to "he",
        '呼' to "hu",
        '哈' to "ha",
        '嘉' to "jia",
        '圳' to "zhen",
        '坊' to "fang",
        '天' to "tian",
        '太' to "tai",
        '头' to "tou",
        '宁' to "ning",
        '安' to "an",
        '宜' to "yi",
        '家' to "jia",
        '封' to "feng",
        '尔' to "er",
        '山' to "shan",
        '岛' to "dao",
        '川' to "chuan",
        '州' to "zhou",
        '广' to "guang",
        '庄' to "zhuang",
        '庆' to "qing",
        '开' to "kai",
        '徐' to "xu",
        '德' to "de",
        '惠' to "hui",
        '成' to "cheng",
        '拉' to "la",
        '昆' to "kun",
        '明' to "ming",
        '春' to "chun",
        '曲' to "qu",
        '木' to "mu",
        '杭' to "hang",
        '林' to "lin",
        '枣' to "zao",
        '柳' to "liu",
        '武' to "wu",
        '汉' to "han",
        '江' to "jiang",
        '沂' to "yi",
        '沈' to "shen",
        '沙' to "sha",
        '波' to "bo",
        '洛' to "luo",
        '津' to "jin",
        '济' to "ji",
        '浩' to "hao",
        '海' to "hai",
        '深' to "shen",
        '温' to "wen",
        '滨' to "bin",
        '潍' to "wei",
        '烟' to "yan",
        '特' to "te",
        '珠' to "zhu",
        '石' to "shi",
        '福' to "fu",
        '绍' to "shao",
        '中' to "zhong",
        '和' to "he",
        '原' to "yuan",
        '乌' to "wu",
        '无' to "wu",
        '昌' to "chang",
        '桂' to "gui",
        '门' to "men",
        '鲁' to "lu",
        '萨' to "sa",
        '郑' to "zheng",
        '哈' to "ha",
    )

    @Volatile
    private var stationCache: List<RailStation>? = null

    /**
     * 简单 HTTP GET（与 APK 原实现一致：HttpURLConnection + 15s 超时 + 浏览器 UA）.
     */
    private suspend fun httpGet(url: String): String = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000
            connection.setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/131.0.0.0",
            )
            connection.setRequestProperty("Accept", "application/json")
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: ""
        } finally {
            connection.disconnect()
        }
    }

    /**
     * 从 12306 官方数据源拉取全部车站列表（带缓存）.
     */
    private suspend fun getAllStations(): List<RailStation> {
        stationCache?.let { return it }
        val raw = httpGet("https://kyfw.12306.cn/otn/resources/js/framework/station_name.js")
        val stations = raw.split("@").drop(1).mapNotNull { entry ->
            val parts = entry.split("|")
            if (parts.size >= 4) {
                RailStation(name = parts[1], telecode = parts[2], pinyin = parts[3])
            } else {
                null
            }
        }
        stationCache = stations
        return stations
    }

    /**
     * 预选车次：输入关键字，返回匹配的车次号列表.
     */
    suspend fun preselect(keyword: String): List<String> {
        val url = "$BASE/api/train/preselect?keyword=${URLEncoder.encode(keyword, "UTF-8")}"
        val body = httpGet(url)
        return runCatching {
            json.parseToJsonElement(body).jsonArray.map { it.jsonPrimitive.contentOrNull ?: "" }
        }.getOrDefault(emptyList())
    }

    /**
     * 按电报码查询车站当日车次.
     */
    suspend fun queryStation(telecode: String): RailStationResult {
        val url = "$BASE/api/station/query?telecode=${URLEncoder.encode(telecode.trim(), "UTF-8")}"
        val body = httpGet(url)
        return runCatching {
            val obj = json.parseToJsonElement(body).jsonObject
            val trainList = obj["trainList"]?.jsonArray?.mapNotNull { item ->
                item.jsonObject["number"]?.jsonPrimitive?.contentOrNull
            } ?: emptyList()
            RailStationResult(
                name = obj["name"]?.jsonPrimitive?.contentOrNull ?: "",
                telecode = obj["telecode"]?.jsonPrimitive?.contentOrNull ?: "",
                trainList = trainList,
                traffic = obj["traffic"]?.jsonPrimitive?.contentOrNull ?: "",
                error = obj["error"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }.getOrElse {
            RailStationResult(name = "", telecode = telecode, trainList = emptyList(), traffic = "", error = it.message ?: "")
        }
    }

    /**
     * 按车次号查询详细时刻信息.
     */
    suspend fun queryTrain(train: String): RailTrainResult {
        val url = "$BASE/api/train/query?train=${URLEncoder.encode(train.trim(), "UTF-8")}"
        val body = httpGet(url)
        return runCatching {
            val root = json.parseToJsonElement(body).jsonObject
            val bodyObj = root["body"]?.jsonObject ?: root
            RailTrainResult(
                trainNo = bodyObj["trainNo"]?.jsonPrimitive?.contentOrNull ?: train.trim(),
                bureauName = bodyObj["bureauName"]?.jsonPrimitive?.contentOrNull ?: "",
                car = bodyObj["car"]?.jsonPrimitive?.contentOrNull ?: "",
                carOwner = bodyObj["carOwner"]?.jsonPrimitive?.contentOrNull ?: "",
                rundays = bodyObj["rundays"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
                timetable = bodyObj["timetable"]?.jsonArray?.map { it.jsonPrimitive.contentOrNull ?: "" } ?: emptyList(),
                error = root["error"]?.jsonPrimitive?.contentOrNull ?: "",
            )
        }.getOrElse {
            RailTrainResult(trainNo = train.trim(), bureauName = "", car = "", carOwner = "", rundays = emptyList(), timetable = emptyList(), error = it.message ?: "")
        }
    }

    /**
     * 本地搜索车站（全量车站 + 中文/拼音/电报码/拼音首字母匹配）.
     */
    suspend fun searchStation(keyword: String): List<RailStation> {
        val kw = keyword.trim().lowercase(Locale.ROOT)
        if (kw.isEmpty()) return emptyList()
        val all = getAllStations()
        return all.filter { station ->
            val name = station.name
            name.contains(kw, ignoreCase = true) ||
                station.telecode.contains(kw, ignoreCase = true) ||
                name.contains(kw, ignoreCase = false) ||
                pinyinMatch(name, kw)
        }
    }

    /**
     * 拼音首字母匹配：将中文站名转成拼音首字母串，再与关键字比较.
     */
    private fun pinyinMatch(hanzi: String, query: String): Boolean {
        val initials = buildString {
            for (i in hanzi.indices) {
                val pinyin = PINYIN_MAP[hanzi[i]]
                if (pinyin != null && pinyin.isNotEmpty()) {
                    append(pinyin[0])
                }
            }
        }
        if (initials.isEmpty()) return false
        return initials.contains(query, ignoreCase = true) || query.contains(initials, ignoreCase = true)
    }
}
