package me.rerere.rikkahub.data.ai.tools

import kotlinx.serialization.json.*
import me.rerere.ai.core.InputSchema
import me.rerere.ai.core.Tool
import me.rerere.ai.ui.UIMessagePart
import me.rerere.rikkahub.data.repository.WorkspaceRepository

private fun wsExec(repo: WorkspaceRepository, cmd: String, timeout: Long = 60000): String {
    return try {
        repo.executeCommand("default", cmd, timeoutMillis = timeout).stdout
    } catch(e: Exception) { """{"error":"${e.message?.take(200)}"}""" }
}

private fun wsId(repo: WorkspaceRepository): String {
    return try { repo.listFlow().first().firstOrNull()?.id ?: "default" } catch(_: Exception) { "default" }
}

fun buildApkReverseMcpTools(workspaceRepository: WorkspaceRepository): List<Tool> {
    val repo = workspaceRepository

    return listOf(
        // === APK 解码 ===
        Tool(name="apk_decode",
            description="用 apktool 解码 APK 文件。Params: apk_path(APK路径), output_dir(输出目录,可选), no_res(不解码资源,可选), no_src(不解码源码,可选)。",
            needsApproval=true,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("apk_path",buildJsonObject{put("type","string");put("description","APK文件路径")})
                put("output_dir",buildJsonObject{put("type","string");put("description","输出目录(可选)")})
                put("no_res",buildJsonObject{put("type","boolean");put("description","不解码资源")})
                put("no_src",buildJsonObject{put("type","boolean");put("description","不解码源码")})
            },required=listOf("apk_path")) },
            execute={ args ->
                val o=args.jsonObject
                val apk=o["apk_path"]?.jsonPrimitive?.contentOrNull?:error("apk_path")
                val out=o["output_dir"]?.jsonPrimitive?.contentOrNull?:apk.removeSuffix(".apk")+"_decoded"
                val opts=buildString{
                    if(o["no_res"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()==true) append(" -r")
                    if(o["no_src"]?.jsonPrimitive?.contentOrNull?.toBooleanStrictOrNull()==true) append(" -s")
                }
                val ws=wsId(repo)
                val r=wsExec(repo,"apktool d -f$opts \"$apk\" -o \"$out\" 2>&1",120000)
                listOf(UIMessagePart.Text(buildJsonObject{
                    put("success",r.contains("I: Using Apktool")||r.contains("I: Smaling"))
                    put("output",r.take(3000)); put("output_dir",out)
                    if(!r.contains("error",true)) put("next","用 apk_manifest/apk_smali_list/apk_resources 分析解码结果")
                }.toString()))
            },
        ),

        // === APK 打包 ===
        Tool(name="apk_build",
            description="用 apktool 重新打包 APK。Params: project_dir(解码后的项目目录)。",
            needsApproval=true,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
            },required=listOf("project_dir")) },
            execute={ args ->
                val dir=args.jsonObject["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val ws=wsId(repo)
                val r=wsExec(repo,"apktool b \"$dir\" 2>&1",120000)
                listOf(UIMessagePart.Text(r.take(3000)))
            },
        ),

        // === 读取 AndroidManifest ===
        Tool(name="apk_manifest",
            description="读取解码后 APK 的 AndroidManifest.xml。Params: project_dir(项目目录)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
            },required=listOf("project_dir")) },
            execute={ args ->
                val dir=args.jsonObject["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val ws=wsId(repo)
                val r=wsExec(repo,"cat \"$dir/AndroidManifest.xml\" 2>/dev/null | head -500",10000)
                listOf(UIMessagePart.Text(r.ifBlank{"AndroidManifest.xml 不存在"}))
            },
        ),

        // === 列出 smali 文件 ===
        Tool(name="apk_smali_list",
            description="列出解码后 APK 的 smali 文件。Params: project_dir, package(包名过滤,可选)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
                put("package",buildJsonObject{put("type","string");put("description","包名过滤如 com.example")})
            },required=listOf("project_dir")) },
            execute={ args ->
                val o=args.jsonObject
                val dir=o["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val pkg=o["package"]?.jsonPrimitive?.contentOrNull
                val ws=wsId(repo)
                val path=if(pkg!=null) "$dir/smali/${pkg.replace(".","/")}" else "$dir/smali"
                val r=wsExec(repo,"find \"$path\" -name '*.smali' -type f 2>/dev/null | head -100",10000)
                listOf(UIMessagePart.Text(r.ifBlank{"无smali文件"}.lines().joinToString("\n"){it.removePrefix("$dir/")}))
            },
        ),

        // === 读取 smali 文件 ===
        Tool(name="apk_smali_read",
            description="读取 smali 文件内容。Params: project_dir, class_name(完整类名如 com.example.MainActivity)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
                put("class_name",buildJsonObject{put("type","string");put("description","完整类名")})
            },required=listOf("project_dir","class_name")) },
            execute={ args ->
                val o=args.jsonObject
                val dir=o["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val cn=o["class_name"]?.jsonPrimitive?.contentOrNull?:error("class_name")
                val ws=wsId(repo)
                // 在所有smali目录中查找
                val r=wsExec(repo,"for d in \"$dir\"/smali*; do f=\"\$d/${cn.replace(\".\",\"/\")}.smali\"; [ -f \"\$f\" ] && cat \"\$f\" && break; done 2>/dev/null",10000)
                listOf(UIMessagePart.Text(r.ifBlank{"类 $cn 未找到"}))
            },
        ),

        // === 搜索 smali ===
        Tool(name="apk_search",
            description="在解码后的APK中搜索字符串/方法/类。Params: project_dir, pattern(搜索模式), type(smali/xml/all,默认all)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
                put("pattern",buildJsonObject{put("type","string");put("description","搜索模式")})
                put("type",buildJsonObject{put("type","string");put("description","smali/xml/all")})
            },required=listOf("project_dir","pattern")) },
            execute={ args ->
                val o=args.jsonObject
                val dir=o["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val pat=o["pattern"]?.jsonPrimitive?.contentOrNull?:error("pattern")
                val tp=o["type"]?.jsonPrimitive?.contentOrNull?:"all"
                val ws=wsId(repo)
                val ext=when(tp){"smali"->".smali";"xml"->".xml";else->""}
                val r=wsExec(repo,"grep -rn --include='*$ext' \"$pat\" \"$dir\" 2>/dev/null | head -50",30000)
                listOf(UIMessagePart.Text(r.ifBlank{"未找到匹配"}))
            },
        ),

        // === 资源列表 ===
        Tool(name="apk_resources",
            description="列出解码后APK的资源文件。Params: project_dir, type(layout/drawable/values等,可选)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
                put("type",buildJsonObject{put("type","string");put("description","资源类型")})
            },required=listOf("project_dir")) },
            execute={ args ->
                val o=args.jsonObject
                val dir=o["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val tp=o["type"]?.jsonPrimitive?.contentOrNull
                val ws=wsId(repo)
                val path=if(tp!=null) "$dir/res/$tp" else "$dir/res"
                val r=wsExec(repo,"find \"$path\" -type f 2>/dev/null | head -100",10000)
                listOf(UIMessagePart.Text(r.ifBlank{"无资源文件"}.lines().joinToString("\n"){it.removePrefix("$dir/")}))
            },
        ),

        // === JADX 反编译 Java ===
        Tool(name="jadx_decompile",
            description="用 jadx 反编译 APK 为 Java 源码。Params: apk_path(APK路径), output_dir(输出目录,可选), class_filter(类名过滤,可选)。",
            needsApproval=true,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("apk_path",buildJsonObject{put("type","string");put("description","APK路径")})
                put("output_dir",buildJsonObject{put("type","string");put("description","输出目录")})
                put("class_filter",buildJsonObject{put("type","string");put("description","类名过滤")})
            },required=listOf("apk_path")) },
            execute={ args ->
                val o=args.jsonObject
                val apk=o["apk_path"]?.jsonPrimitive?.contentOrNull?:error("apk_path")
                val out=o["output_dir"]?.jsonPrimitive?.contentOrNull?:apk.removeSuffix(".apk")+"_java"
                val cf=o["class_filter"]?.jsonPrimitive?.contentOrNull
                val ws=wsId(repo)
                val jadx=wsExec(repo,"which jadx 2>/dev/null || which jadx-gui 2>/dev/null || echo ''",5000).trim()
                if(jadx.isBlank()) return@Tool listOf(UIMessagePart.Text("""{"error":"jadx未安装。安装: apt install jadx 或从 https://github.com/skylot/jadx/releases 下载"}"""))
                val cmd=buildString{
                    append("jadx -d \"$out\"")
                    if(cf!=null) append(" --deobf")
                    append(" \"$apk\" 2>&1")
                }
                val r=wsExec(repo,cmd,300000)
                listOf(UIMessagePart.Text(buildJsonObject{
                    put("success",!r.contains("error",true))
                    put("output",r.take(2000)); put("output_dir",out)
                    put("next","用 jadx_read_class 读取反编译后的Java源码")
                }.toString()))
            },
        ),

        // === 读取 Java 源码 ===
        Tool(name="jadx_read_class",
            description="读取 jadx 反编译后的 Java 源码。Params: output_dir(jadx输出目录), class_name(完整类名如 com.example.MainActivity)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("output_dir",buildJsonObject{put("type","string");put("description","jadx输出目录")})
                put("class_name",buildJsonObject{put("type","string");put("description","完整类名")})
            },required=listOf("output_dir","class_name")) },
            execute={ args ->
                val o=args.jsonObject
                val dir=o["output_dir"]?.jsonPrimitive?.contentOrNull?:error("output_dir")
                val cn=o["class_name"]?.jsonPrimitive?.contentOrNull?:error("class_name")
                val ws=wsId(repo)
                // jadx 输出在 sources/ 下
                val r=wsExec(repo,"find \"$dir/sources\" -path \"*/${cn.replace(\".\",\"/\")}.java\" -o -path \"*/${cn.replace(\".\",\"/\")}.java\" 2>/dev/null | head -1 | xargs cat 2>/dev/null | head -500",10000)
                listOf(UIMessagePart.Text(r.ifBlank{"类 $cn 未找到。检查 output_dir 和 class_name"}))
            },
        ),

        // === 搜索 Java 源码 ===
        Tool(name="jadx_search",
            description="在 jadx 反编译的 Java 源码中搜索。Params: output_dir, pattern(搜索模式)。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("output_dir",buildJsonObject{put("type","string");put("description","jadx输出目录")})
                put("pattern",buildJsonObject{put("type","string");put("description","搜索模式")})
            },required=listOf("output_dir","pattern")) },
            execute={ args ->
                val o=args.jsonObject
                val dir=o["output_dir"]?.jsonPrimitive?.contentOrNull?:error("output_dir")
                val pat=o["pattern"]?.jsonPrimitive?.contentOrNull?:error("pattern")
                val ws=wsId(repo)
                val r=wsExec(repo,"grep -rn \"$pat\" \"$dir/sources\" 2>/dev/null | head -50",30000)
                listOf(UIMessagePart.Text(r.ifBlank{"未找到匹配"}))
            },
        ),

        // === 项目分析 ===
        Tool(name="apk_analyze",
            description="分析解码后的APK项目结构：包名/权限/Activity/Service/资源/签名等。Params: project_dir。",
            needsApproval=false,
            parameters={ InputSchema.Obj(properties=buildJsonObject{
                put("project_dir",buildJsonObject{put("type","string");put("description","项目目录")})
            },required=listOf("project_dir")) },
            execute={ args ->
                val dir=args.jsonObject["project_dir"]?.jsonPrimitive?.contentOrNull?:error("project_dir")
                val ws=wsId(repo)
                val sb=StringBuilder()
                // 包名
                sb.append("📦 包名: ").append(wsExec(repo,"grep -oP 'package=\"([^\"]+)\"' \"$dir/AndroidManifest.xml\" 2>/dev/null | head -1",5000).trim()).append("\n\n")
                // 权限
                sb.append("🔐 权限:\n").append(wsExec(repo,"grep -oP 'android:name=\"[^\"]*permission[^\"]*\"' \"$dir/AndroidManifest.xml\" 2>/dev/null | head -20",5000)).append("\n")
                // Activity
                sb.append("📱 Activity:\n").append(wsExec(repo,"grep -oP 'android:name=\"[^\"]*\"' \"$dir/AndroidManifest.xml\" 2>/dev/null | grep -i activity | head -20",5000)).append("\n")
                // smali统计
                sb.append("📊 统计:\n").append(wsExec(repo,"echo smali文件: $(find \"$dir\" -name '*.smali' | wc -l); echo 资源文件: $(find \"$dir/res\" -type f 2>/dev/null | wc -l); echo 项目大小: $(du -sh \"$dir\" 2>/dev/null | cut -f1)",5000))
                listOf(UIMessagePart.Text(sb.toString()))
            },
        ),
    )
}
