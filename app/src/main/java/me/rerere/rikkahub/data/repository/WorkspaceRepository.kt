/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 */

package me.rerere.rikkahub.data.repository

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import me.rerere.rikkahub.data.datastore.SettingsStore
import me.rerere.rikkahub.data.db.dao.WorkspaceDAO
import me.rerere.rikkahub.data.db.entity.WorkspaceEntity
import me.rerere.rikkahub.utils.JsonInstant
import me.rerere.workspace.RootfsInstallProgress
import me.rerere.workspace.RootfsInstaller
import me.rerere.workspace.WorkspaceCommandResult
import me.rerere.workspace.WorkspaceFileEntry
import me.rerere.workspace.WorkspaceManager
import me.rerere.workspace.WorkspaceShellStatus
import me.rerere.workspace.WorkspaceStorageArea
import java.io.InputStream
import java.io.OutputStream
import java.io.File
import kotlin.uuid.Uuid

/** 内置预置 rootfs 在 assets 中的路径 (ubuntu-base 24.04 arm64, ~30MB) */
private const val BUNDLED_ROOTFS_ASSET = "rootfs/ubuntu-base-arm64.tar.gz"

class WorkspaceRepository(
    private val context: Context,
    private val dao: WorkspaceDAO,
    private val manager: WorkspaceManager,
    private val rootfsInstaller: RootfsInstaller,
    private val settingsStore: SettingsStore,
) {
    fun listFlow(): Flow<List<WorkspaceEntity>> = dao.listFlow()

    suspend fun checkIntegrity() = withContext(Dispatchers.IO) {
        val workspaces = dao.getAll()
        for (workspace in workspaces) {
            val dir = manager.workspaceDir(workspace.root)
            if (!dir.exists()) {
                Log.w(TAG, "Workspace directory missing, removing record: id=${workspace.id}, root=${workspace.root}")
                dao.deleteById(workspace.id)
                cleanupAssistantReferences(workspace.id)
                continue
            }
            val statusName = workspace.shellStatus
            if ((statusName == WorkspaceShellStatus.READY.name || statusName == WorkspaceShellStatus.INSTALLING.name)
                && !manager.hasRootfs(workspace.root)
            ) {
                Log.w(TAG, "Rootfs missing, resetting shell status: id=${workspace.id}")
                updateShellState(workspace.id, WorkspaceShellStatus.DISABLED.name)
            }
        }
    }

    suspend fun getById(id: String): WorkspaceEntity? = dao.getById(id)

    suspend fun create(name: String): WorkspaceEntity {
        val id = Uuid.random().toString()
        val now = System.currentTimeMillis()
        val finalName = name.trim().ifBlank { "Workspace" }
        require(!isNameTaken(finalName, excludeId = null)) {
            "Workspace name already exists: $finalName"
        }
        val workspace = WorkspaceEntity(
            id = id,
            name = finalName,
            root = id,
            createdAt = now,
            updatedAt = now,
            lastAccessAt = null,
        )
        manager.ensureWorkspace(workspace.root)
        dao.upsert(workspace)
        return workspace
    }

    suspend fun rename(id: String, name: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        val finalName = name.trim().ifBlank { workspace.name }
        require(!isNameTaken(finalName, excludeId = id)) {
            "Workspace name already exists: $finalName"
        }
        dao.upsert(
            workspace.copy(
                name = finalName,
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    /** 名字是否已被其他 workspace 占用（trim 后精确匹配，排除 [excludeId] 自身） */
    suspend fun isNameTaken(name: String, excludeId: String?): Boolean {
        val target = name.trim()
        return dao.getAll().any { it.id != excludeId && it.name.trim() == target }
    }

    suspend fun setToolApproval(id: String, toolName: String, needsApproval: Boolean): Boolean {
        val workspace = dao.getById(id) ?: return false
        val overrides = workspace.toolApprovalOverrides() + (toolName to needsApproval)
        dao.upsert(
            workspace.copy(
                toolApprovals = JsonInstant.encodeToString(overrides),
                updatedAt = System.currentTimeMillis(),
            )
        )
        return true
    }

    suspend fun installRootfs(
        id: String,
        url: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean {
        val workspace = dao.getById(id) ?: return false
        updateShellState(workspace, WorkspaceShellStatus.INSTALLING.name)
        try {
            // runInterruptible 让协程取消转成线程中断, 打断 install 内阻塞的下载/解压循环
            runInterruptible(Dispatchers.IO) {
                rootfsInstaller.install(workspace.root, url, onProgress)
            }
            updateShellState(workspace, WorkspaceShellStatus.READY.name)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw e
        } catch (e: InterruptedException) {
            withContext(NonCancellable) {
                restoreShellState(workspace)
            }
            throw CancellationException("Rootfs install cancelled").also { it.initCause(e) }
        } catch (e: Throwable) {
            Log.e(TAG, "installRootfs failed: workspace=${workspace.id}, root=${workspace.root}, url=$url", e)
            updateShellState(workspace, WorkspaceShellStatus.BROKEN.name)
            throw e
        }
    }

    suspend fun installBundledRootfs(
        id: String,
        onProgress: (RootfsInstallProgress) -> Unit = {},
    ): Boolean {
        val workspace = dao.getById(id) ?: return false
        updateShellState(workspace, WorkspaceShellStatus.INSTALLING.name)
        try {
            // 从应用内置 assets 安装预置 rootfs (免下载), 完成后 shell 直接 READY
            runInterruptible(Dispatchers.IO) {
                val cacheFile = File(context.cacheDir, "bundled-rootfs.tar.gz")
                try {
                    context.assets.open(BUNDLED_ROOTFS_ASSET).use { input ->
                        cacheFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    rootfsInstaller.installFromFile(workspace.root, cacheFile, onProgress)
                } finally {
                    cacheFile.delete()
                }
            }
            updateShellState(workspace, WorkspaceShellStatus.READY.name)
            return true
        } catch (e: CancellationException) {
            withContext(NonCancellable) { restoreShellState(workspace) }
            throw e
        } catch (e: InterruptedException) {
            withContext(NonCancellable) { restoreShellState(workspace) }
            throw CancellationException("Rootfs install cancelled").also { it.initCause(e) }
        } catch (e: Throwable) {
            Log.e(TAG, "installBundledRootfs failed: workspace=${workspace.id}", e)
            updateShellState(workspace, WorkspaceShellStatus.BROKEN.name)
            throw e
        }
    }

    suspend fun listFiles(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): List<WorkspaceFileEntry> = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: return@withContext emptyList()
        manager.ensureWorkspace(workspace.root)
        manager.listFiles(workspace.root, path, area)
    }

    suspend fun readText(
        id: String,
        path: String,
    ): String = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.readText(workspace.root, path)
    }

    suspend fun writeText(
        id: String,
        path: String,
        text: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.writeText(workspace.root, path, text, overwrite)
    }

    suspend fun importFile(
        id: String,
        area: WorkspaceStorageArea,
        destinationPath: String,
        fileName: String,
        inputStream: InputStream,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.importFile(workspace.root, destinationPath, area, fileName, inputStream)
    }

    suspend fun fileSize(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
    ): Long = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.fileSize(workspace.root, path, area)
    }

    suspend fun exportFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        outputStream: OutputStream,
    ) = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.exportFile(workspace.root, path, area, outputStream)
    }

    suspend fun deleteFile(
        id: String,
        area: WorkspaceStorageArea,
        path: String,
        recursive: Boolean,
    ): Boolean {
        val deleted = withContext(Dispatchers.IO) {
            val workspace = dao.getById(id) ?: return@withContext false
            manager.deleteFile(workspace.root, path, recursive, area)
        }
        return deleted
    }

    suspend fun moveFile(
        id: String,
        source: String,
        target: String,
        overwrite: Boolean,
    ): WorkspaceFileEntry = withContext(Dispatchers.IO) {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        manager.ensureWorkspace(workspace.root)
        manager.moveFile(workspace.root, source, target, overwrite)
    }

    suspend fun executeCommand(
        id: String,
        command: String,
        cwd: String = "",
        timeoutMillis: Long = WorkspaceManager.DEFAULT_COMMAND_TIMEOUT_MS,
        stdin: ByteArray? = null,
    ): WorkspaceCommandResult {
        val workspace = dao.getById(id) ?: error("Workspace not found: $id")
        // runInterruptible 让协程取消转化为线程中断，从而打断阻塞的 Process.waitFor 并杀掉进程
        return runInterruptible(Dispatchers.IO) {
            manager.ensureWorkspace(workspace.root)
            manager.executeCommand(workspace.root, command, cwd, timeoutMillis, stdin)
        }
    }

    /**
     * 自动安装编程环境与常用工具 (git/curl/wget/python3/pip/build-essential/nano/unzip)。
     * 需 shell 已 READY (rootfs 已就绪)。后台调用, 失败抛异常由调用方容错。
     */
    suspend fun installProgrammingTools(
        id: String,
        onProgress: (String) -> Unit = {},
    ) {
        val workspace = dao.getById(id) ?: return
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) return

        onProgress("配置网络...")
        // ubuntu-base 的 /etc/resolv.conf 为空, 先写入公共 DNS 才能 apt
        val dns = executeCommand(
            id,
            "printf 'nameserver 8.8.8.8\\nnameserver 223.5.5.5\\n' > /etc/resolv.conf",
            timeoutMillis = 30_000,
        )
        if (dns.exitCode != 0) {
            Log.w(TAG, "write resolv.conf failed: ${dns.stderr.take(120)}")
        }

        onProgress("更新软件源...")
        val update = executeCommand(id, "apt-get update -y", timeoutMillis = 600_000)
        if (update.exitCode != 0) {
            throw RuntimeException("apt update failed: ${update.stderr.take(200)}")
        }

        onProgress("安装编程环境 (git/curl/wget/python3/pip/gcc/make/nano)...")
        val install = executeCommand(
            id,
            "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends " +
                "git curl wget python3 python3-pip build-essential nano unzip",
            timeoutMillis = 1_200_000,
        )
        if (install.exitCode != 0) {
            throw RuntimeException("apt install failed: ${install.stderr.take(300)}")
        }
    }

    /**
     * 自动安装 APK 反编译工具链: Java 运行时 + apktool + jadx。
     * 需 shell 已 READY。后台调用, 失败抛异常由调用方容错。
     */
    suspend fun installReverseTools(
        id: String,
        onProgress: (String) -> Unit = {},
    ) {
        val workspace = dao.getById(id) ?: return
        if (workspace.shellStatus != WorkspaceShellStatus.READY.name) return

        onProgress("安装 Java 运行时 (openjdk-17)...")
        val java = executeCommand(
            id,
            "DEBIAN_FRONTEND=noninteractive apt-get install -y --no-install-recommends openjdk-17-jre-headless",
            timeoutMillis = 1_200_000,
        )
        if (java.exitCode != 0) {
            throw RuntimeException("java install failed: ${java.stderr.take(200)}")
        }

        onProgress("安装 apktool (资源/smali 反编译)...")
        val apktool = executeCommand(
            id,
            "curl -sL -o /usr/local/bin/apktool.jar https://github.com/iBotPeaches/Apktool/releases/download/v2.10.0/apktool_2.10.0.jar && " +
                "printf '#!/bin/bash\\nexec java -jar /usr/local/bin/apktool.jar \"\\$@\"\\n' > /usr/local/bin/apktool && " +
                "chmod +x /usr/local/bin/apktool",
            timeoutMillis = 600_000,
        )
        if (apktool.exitCode != 0) {
            throw RuntimeException("apktool install failed: ${apktool.stderr.take(200)}")
        }

        onProgress("安装 jadx (Java 源码反编译)...")
        val jadx = executeCommand(
            id,
            "mkdir -p /opt && curl -sL -o /tmp/jadx.zip https://github.com/skylot/jadx/releases/download/v1.5.1/jadx-1.5.1.zip && " +
                "unzip -q -o /tmp/jadx.zip -d /opt/ && rm -f /tmp/jadx.zip && " +
                "ln -sf /opt/jadx-1.5.1/bin/jadx /usr/local/bin/jadx && " +
                "ln -sf /opt/jadx-1.5.1/bin/jadx-gui /usr/local/bin/jadx-gui",
            timeoutMillis = 600_000,
        )
        if (jadx.exitCode != 0) {
            throw RuntimeException("jadx install failed: ${jadx.stderr.take(200)}")
        }
    }

    suspend fun delete(id: String): Boolean {
        val workspace = dao.getById(id) ?: return false
        dao.deleteById(id)
        withContext(Dispatchers.IO) {
            manager.deleteWorkspace(workspace.root)
        }
        cleanupAssistantReferences(id)
        return true
    }

    private suspend fun cleanupAssistantReferences(workspaceId: String) {
        settingsStore.update { settings ->
            settings.copy(
                assistants = settings.assistants.map { assistant ->
                    if (assistant.workspaceId?.toString() == workspaceId) {
                        assistant.copy(workspaceId = null)
                    } else {
                        assistant
                    }
                }
            )
        }
    }

    private suspend fun restoreShellState(workspace: WorkspaceEntity) {
        updateShellState(workspace.id, workspace.shellStatus)
    }

    private suspend fun updateShellState(
        workspace: WorkspaceEntity,
        shellStatus: String,
    ) = updateShellState(workspace.id, shellStatus)

    private suspend fun updateShellState(
        workspaceId: String,
        shellStatus: String,
    ) {
        dao.updateShellStatus(
            id = workspaceId,
            shellStatus = shellStatus,
            updatedAt = System.currentTimeMillis(),
        )
    }

    companion object {
        private const val TAG = "WorkspaceRepository"
    }
}
