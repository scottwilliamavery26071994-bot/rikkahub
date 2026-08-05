plugins {
    alias(libs.plugins.android.library)
}

val webUiDir = rootProject.layout.projectDirectory.dir("web-ui")
val webStaticResourcesDir = layout.projectDirectory.dir("src/main/resources/static")

val buildWebUi = tasks.register("buildWebUi") {
    group = "build"
    description = "Build web-ui and copy its static output into the web module resources (auto-skip if pnpm unavailable)."

    inputs.files(
        webUiDir.file("package.json"),
        webUiDir.file("pnpm-lock.yaml"),
        webUiDir.file("components.json"),
        webUiDir.file("copy.ts"),
        webUiDir.file("react-router.config.ts"),
        webUiDir.file("tsconfig.json"),
        webUiDir.file("vite.config.ts"),
        webUiDir.file("vite-env.d.ts")
    )
    inputs.dir(webUiDir.dir("app"))
    inputs.dir(webUiDir.dir("public"))
    outputs.dir(webStaticResourcesDir)

    doLast {
        // 若环境可用 pnpm 则真实构建前端；否则创建空资源目录，保证无前端工具链时编译仍可进行
        val outDir = webStaticResourcesDir.asFile
        val pnpmAvailable = runCatching {
            ProcessBuilder("pnpm", "--version")
                .redirectErrorStream(true)
                .start().apply { waitFor() }
        }.getOrNull()?.exitValue() == 0
        if (pnpmAvailable) {
            val p = ProcessBuilder("pnpm", "run", "build")
                .directory(webUiDir.asFile)
                .inheritIO()
                .start()
            if (p.waitFor() != 0) throw GradleException("web-ui build failed")
        } else {
            outDir.mkdirs()
            println("pnpm not found - skipping web-ui build (empty static resources)")
        }
    }
}

android {
    namespace = "me.rerere.rikkahub.web"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

tasks.named("preBuild") {
    dependsOn(buildWebUi)
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)

    // ktor server
    implementation(libs.ktor.server.default.headers)
    implementation(libs.ktor.server.conditional.headers)
    implementation(libs.ktor.server.compression)
    implementation(libs.ktor.server.cors)
    api(libs.ktor.server.auth)
    api(libs.ktor.server.auth.jwt)
    api(libs.ktor.server.core)
    implementation(libs.ktor.server.host.common)
    api(libs.ktor.server.content.negotiation)
    api(libs.ktor.server.status.pages)
    api(libs.ktor.server.sse)
    api(libs.ktor.server.cio)
    api(libs.ktor.server.websockets)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
