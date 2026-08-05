/*
 * 灵犀 Lingxi
 * 衍生自 Lingxi (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（SettingGomokuPage：五子棋人机对战）
 */

package me.rerere.rikkahub.ui.pages.setting
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowLeft01

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.rerere.rikkahub.data.game.GomokuGame
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
import androidx.compose.ui.unit.sp

/**
 * 五子棋：玩家执黑先手，AI 执白.
 */
@Composable
fun SettingGomokuPage(onBack: () -> Unit = {}) {
    val board = remember {
        Array(GomokuGame.BOARD_SIZE) { IntArray(GomokuGame.BOARD_SIZE) { GomokuGame.EMPTY } }
    }
    var currentPlayer by remember { mutableStateOf(GomokuGame.BLACK) }
    var winner by remember { mutableStateOf(0) }
    var statusText by remember { mutableStateOf("轮到你下棋（黑子）") }
    val scope = rememberCoroutineScope()

    fun resetGame() {
        for (x in board.indices) for (y in board.indices) board[x][y] = GomokuGame.EMPTY
        currentPlayer = GomokuGame.BLACK
        winner = 0
        statusText = "轮到你下棋（黑子）"
    }

    fun aiMove() {
        if (winner != 0) return
        val best = GomokuGame.getBestMove(board, GomokuGame.WHITE) ?: return
        board[best.first][best.second] = GomokuGame.WHITE
        if (GomokuGame.checkWin(board, GomokuGame.WHITE, best.first, best.second)) {
            winner = GomokuGame.WHITE
            statusText = "AI 获胜！"
        } else if (GomokuGame.isBoardFull(board)) {
            winner = -1
            statusText = "平局"
        } else {
            currentPlayer = GomokuGame.BLACK
            statusText = "轮到你下棋（黑子）"
        }
    }

    fun playerMove(x: Int, y: Int) {
        if (winner != 0 || currentPlayer != GomokuGame.BLACK) return
        if (board[x][y] != GomokuGame.EMPTY) return
        board[x][y] = GomokuGame.BLACK
        if (GomokuGame.checkWin(board, GomokuGame.BLACK, x, y)) {
            winner = GomokuGame.BLACK
            statusText = "你赢了！"
            return
        }
        if (GomokuGame.isBoardFull(board)) {
            winner = -1
            statusText = "平局"
            return
        }
        currentPlayer = GomokuGame.WHITE
        statusText = "AI 思考中..."
        scope.launch {
            delay(300)
            aiMove()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("五子棋") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        androidx.compose.material3.Icon(HugeIcons.ArrowLeft01, contentDescription = "返回")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(statusText, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            val cellSize = 20.dp
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                for (y in 0 until GomokuGame.BOARD_SIZE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        for (x in 0 until GomokuGame.BOARD_SIZE) {
                            val piece = board[x][y]
                            Box(
                                modifier = Modifier
                                    .size(cellSize)
                                    .clickable(enabled = winner == 0) { playerMove(x, y) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.fillMaxSize()) {
                                    val stroke = Stroke(width = 1.dp.toPx())
                                    drawLine(
                                        color = Color.Gray,
                                        start = Offset(0f, size.height / 2),
                                        end = Offset(size.width, size.height / 2),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    drawLine(
                                        color = Color.Gray,
                                        start = Offset(size.width / 2, 0f),
                                        end = Offset(size.width / 2, size.height),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                    if (piece == GomokuGame.BLACK) {
                                        drawCircle(Color.Black, radius = size.width * 0.4f)
                                    } else if (piece == GomokuGame.WHITE) {
                                        drawCircle(Color.White, radius = size.width * 0.4f)
                                        drawCircle(Color.Gray, radius = size.width * 0.4f, style = Stroke(1.dp.toPx()))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            Button(onClick = { resetGame() }) {
                Text("重新开始")
            }
            Spacer(Modifier.height(8.dp))
            Text("你执黑子先手，点击棋盘落子，AI 自动应手", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
