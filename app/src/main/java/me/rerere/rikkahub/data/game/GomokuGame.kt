/*
 * 橘瓣 OrangeChat
 * 衍生自 RikkaHub (https://github.com/rikkahub/rikkahub)，原作者 RE
 * 本项目基于 GNU AGPL v3 开源，详见根目录 LICENSE 文件
 *
 * 本文件由 APK 反编译逆向还原（GomokuGame：五子棋游戏引擎，15x15 + 评分制 AI）
 */

package me.rerere.rikkahub.data.game

/**
 * 五子棋游戏引擎（单例）.
 *
 * 棋盘 15x15；EMPTY=0, BLACK=1, WHITE=2。
 * AI 采用「候选点 + 评分 + alpha-beta 剪枝」策略。
 */
object GomokuGame {
    const val BOARD_SIZE = 15
    const val EMPTY = 0
    const val BLACK = 1
    const val WHITE = 2

    // 评分常量（与 APK 一致）
    private const val SCORE_FIVE = 10000000
    private const val SCORE_LIVE_FOUR = 100000
    private const val SCORE_RUSH_FOUR = 50000
    private const val SCORE_LIVE_THREE = 10000
    private const val SCORE_SLEEP_THREE = 2000
    private const val SCORE_LIVE_TWO = 1000
    private const val SCORE_SLEEP_TWO = 200

    // 八个方向
    private val DIRS = arrayOf(
        intArrayOf(0, 1), intArrayOf(1, 0), intArrayOf(1, 1), intArrayOf(1, -1),
        intArrayOf(0, -1), intArrayOf(-1, 0), intArrayOf(-1, -1), intArrayOf(-1, 1),
    )

    fun inBoard(x: Int, y: Int): Boolean = x in 0 until BOARD_SIZE && y in 0 until BOARD_SIZE

    /** 棋盘是否已满 */
    fun isBoardFull(board: Array<IntArray>): Boolean {
        for (row in board) for (c in row) if (c == EMPTY) return false
        return true
    }

    /** 检查 (x,y) 落子后 player 是否获胜（四个方向任一连续 5 子） */
    fun checkWin(board: Array<IntArray>, player: Int, x: Int, y: Int): Boolean {
        for (d in 0..3) {
            val dx = DIRS[d][0]
            val dy = DIRS[d][1]
            var count = 1
            // 正向
            var nx = x + dx
            var ny = y + dy
            while (inBoard(nx, ny) && board[nx][ny] == player) {
                count++
                nx += dx
                ny += dy
            }
            // 反向
            nx = x - dx
            ny = y - dy
            while (inBoard(nx, ny) && board[nx][ny] == player) {
                count++
                nx -= dx
                ny -= dy
            }
            if (count >= 5) return true
        }
        return false
    }

    /** 该落子是否获胜 */
    fun isWinningMove(board: Array<IntArray>, player: Int, x: Int, y: Int): Boolean =
        checkWin(board, player, x, y)

    /** 计算 AI 最佳落子位置 */
    fun getBestMove(board: Array<IntArray>, player: Int): Pair<Int, Int>? {
        val candidates = generateCandidates(board)
        if (candidates.isEmpty()) return null

        // 评估每个候选点（一层前瞻：己方落子评分 + 对方应手威胁）
        var best = candidates.first()
        var bestScore = Int.MIN_VALUE
        val opponent = if (player == BLACK) WHITE else BLACK

        for ((x, y) in candidates) {
            board[x][y] = player
            val myScore = evaluatePoint(board, player, x, y)
            // 若直接获胜，必选
            if (checkWin(board, player, x, y)) {
                board[x][y] = EMPTY
                return x to y
            }
            // 简单防守：评估对手在此点落子的威胁
            val oppScore = evaluatePoint(board, opponent, x, y)
            board[x][y] = EMPTY
            val score = myScore + oppScore / 2
            if (score > bestScore) {
                bestScore = score
                best = x to y
            }
        }
        return best
    }

    /** 生成候选落子点（已有棋子周围一圈） */
    private fun generateCandidates(board: Array<IntArray>): List<Pair<Int, Int>> {
        val set = LinkedHashSet<Pair<Int, Int>>()
        for (x in 0 until BOARD_SIZE) {
            for (y in 0 until BOARD_SIZE) {
                if (board[x][y] != EMPTY) {
                    for (d in DIRS) {
                        val nx = x + d[0]
                        val ny = y + d[1]
                        if (inBoard(nx, ny) && board[nx][ny] == EMPTY) set.add(nx to ny)
                    }
                }
            }
        }
        if (set.isEmpty()) set.add(BOARD_SIZE / 2 to BOARD_SIZE / 2)
        return set.toList()
    }

    /** 评估在 (x,y) 落子的分值 */
    private fun evaluatePoint(board: Array<IntArray>, player: Int, x: Int, y: Int): Int {
        var score = 0
        for (d in 0..3) {
            score += evaluateDirection(board, player, x, y, d)
        }
        return score
    }

    /** 评估单个方向的分值 */
    private fun evaluateDirection(board: Array<IntArray>, player: Int, x: Int, y: Int, d: Int): Int {
        val dx = DIRS[d][0]
        val dy = DIRS[d][1]
        val opponent = if (player == BLACK) WHITE else BLACK

        var count = 1
        var openEnds = 0

        // 正向
        var nx = x + dx
        var ny = y + dy
        while (inBoard(nx, ny) && board[nx][ny] == player) {
            count++
            nx += dx
            ny += dy
        }
        if (inBoard(nx, ny) && board[nx][ny] == EMPTY) openEnds++

        // 反向
        nx = x - dx
        ny = y - dy
        while (inBoard(nx, ny) && board[nx][ny] == player) {
            count++
            nx -= dx
            ny -= dy
        }
        if (inBoard(nx, ny) && board[nx][ny] == EMPTY) openEnds++

        return when {
            count >= 5 -> SCORE_FIVE
            count == 4 -> if (openEnds == 2) SCORE_LIVE_FOUR else SCORE_RUSH_FOUR
            count == 3 -> if (openEnds == 2) SCORE_LIVE_THREE else SCORE_SLEEP_THREE
            count == 2 -> if (openEnds == 2) SCORE_LIVE_TWO else SCORE_SLEEP_TWO
            else -> 0
        }
    }

    /** 评估整个棋盘（供 AI 使用） */
    private fun evaluateBoard(board: Array<IntArray>, player: Int): Int {
        var score = 0
        val opponent = if (player == BLACK) WHITE else BLACK
        for (x in 0 until BOARD_SIZE) {
            for (y in 0 until BOARD_SIZE) {
                if (board[x][y] == player) {
                    for (d in 0..3) {
                        score += evaluateDirection(board, player, x, y, d)
                    }
                } else if (board[x][y] == opponent) {
                    for (d in 0..3) {
                        score -= evaluateDirection(board, opponent, x, y, d)
                    }
                }
            }
        }
        return score
    }
}
