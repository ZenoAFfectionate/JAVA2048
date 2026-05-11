import java.util.*;

/**
 * 蒙特卡洛搜索 (Monte Carlo Rollout) — 方案 B 实现。
 *
 * <h3>设计要点（与原 UCT 树搜索的区别）</h3>
 * <ol>
 *   <li>完全去掉树：根节点的每个合法方向独立做 N 次 rollout，取平均得分作为该方向价值。</li>
 *   <li>每次 rollout 交替执行 "玩家动作 → 环境随机 spawn"，自然刻画 2048 的随机博弈结构。
 *       原实现的致命 BUG 是 select 阶段不 spawn → 跟真实环境完全脱节，故 200 迭代反而比 8 迭代更差。</li>
 *   <li>Rollout 内部用启发式贪心选动作（同 src 的 Utils.heuristic），稳定基线降低方差。</li>
 *   <li>单局 rollout 深度 ≤ ROLLOUT_DEPTH，截断后用启发式给"局面剩余价值"。</li>
 * </ol>
 *
 * <h3>统一口径（2026-05-11 重构）</h3>
 * <ul>
 *   <li>所有 simulate 都走 src 的 {@link Utils#simulateMove}（不再有"自家滑块"）；</li>
 *   <li>所有评估都走 src 的 {@link Utils#heuristic}；</li>
 *   <li>spawn 走 {@link AlgoCommon#spawnRandom}（与 ExperimentRunner 一致）；</li>
 *   <li>Grid 缓冲池减少对象分配（性能损失仅 1.3× vs int[][] 旧实现）。</li>
 *   <li>已通过 SimulateEquivalenceTest 与 GameLogicCorrectnessTest 各 20000 次比较验证零差异。</li>
 * </ul>
 */
public class MCTS {

    private static final int ROWS = 4, COLS = 4;
    private static final int ROLLOUT_DEPTH = 30;     // 每次 rollout 的最大步数
    private static int iterations = 50;              // 每个根方向的 rollout 次数

    // ---- Grid 缓冲池：复用 3 个 Grid[][] 减少分配 ----
    private static final Grid[][] gridBufA = AlgoCommon.newBoard();  // 备份原棋盘
    private static final Grid[][] gridBufB = AlgoCommon.newBoard();  // 工作棋盘
    private static final Grid[][] gridBufC = AlgoCommon.newBoard();  // rollout 起点

    public static void setIterations(int n) { iterations = Math.max(1, n); }
    public static int getIterations() { return iterations; }

    // ================================================================
    //  公开 API — 返回最优方向 0=UP 1=DOWN 2=LEFT 3=RIGHT
    // ================================================================

    public static int getBestDirection(Grid[][] grids) {
        // 备份原棋盘到 bufA，后续完全在 buf 上操作，不改入参
        AlgoCommon.copyValuesInto(grids, gridBufA);

        double[] avgGain = new double[4];
        boolean[] legal = new boolean[4];
        Arrays.fill(avgGain, Double.NEGATIVE_INFINITY);

        for (int dir = 0; dir < 4; dir++) {
            // 试一次：判合法 + 取首步得分 + spawn 后的初态
            AlgoCommon.copyValuesInto(gridBufA, gridBufB);
            int firstScore = Utils.simulateMove(gridBufB, dir);
            if (firstScore < 0) continue;       // 该方向非法
            legal[dir] = true;
            // 首步后必须 spawn 一次（环境随机出 2/4），构成 rollout 的"起点"
            AlgoCommon.spawnRandom(gridBufB);
            // 把"起点"备份到 bufC
            AlgoCommon.copyValuesInto(gridBufB, gridBufC);

            double sum = 0.0;
            for (int it = 0; it < iterations; it++) {
                AlgoCommon.copyValuesInto(gridBufC, gridBufB);  // 每次 rollout 重置到起点
                double gain = greedyRollout(gridBufB);
                sum += firstScore + gain;
            }
            avgGain[dir] = sum / iterations;
        }

        // 选平均最高的合法方向；全非法 fallback 到 0
        int best = -1;
        double bestVal = -Double.MAX_VALUE;
        for (int d = 0; d < 4; d++) {
            if (!legal[d]) continue;
            if (avgGain[d] > bestVal) { bestVal = avgGain[d]; best = d; }
        }
        return best < 0 ? 0 : best;
    }

    // ================================================================
    //  Rollout：用 src 的 simulateMove + heuristic 选动作；交替环境 spawn
    // ================================================================

    /**
     * 在 board 上做一次 rollout，返回累计合并得分 + 终态启发式评估。
     * board 会被原地修改。
     *
     * <p>使用 {@link AlgoCommon#scoreAllDirections} 选最佳方向，避免重复 save/restore 模板。
     */
    static double greedyRollout(Grid[][] board) {
        double cumulative = 0.0;
        for (int step = 0; step < ROLLOUT_DEPTH; step++) {
            // 用 AlgoCommon 一次性扫 4 方向，得分函数 = heuristic(state)（不计 immediate，
            // 因为 immediate 累计在外面，rollout 内部只关心"哪个方向带来更好的局面"）
            double[] scores = AlgoCommon.scoreAllDirections(board,
                    (state, dir, immediate) -> Utils.heuristic(state));

            int bestDir = -1;
            double bestEval = Double.NEGATIVE_INFINITY;
            for (int d = 0; d < 4; d++) {
                if (scores[d] > bestEval) {
                    bestEval = scores[d];
                    bestDir = d;
                }
            }
            if (bestDir < 0) break;        // 死局（4 方向全非法）

            // 真正落地最佳方向，把 immediate 计入累计得分
            int sc = Utils.simulateMove(board, bestDir);
            cumulative += sc;
            AlgoCommon.spawnRandom(board);            // ★ 关键：交替执行环境随机 spawn
        }
        return cumulative + Utils.heuristic(board);
    }

    // ================================================================
    //  保留向后兼容 API（被 MCTSTest 和 SimulateEquivalenceTest 使用）
    // ================================================================

    /** 把 Grid[][] 转 int[][]，仅用于测试与诊断。 */
    static int[][] fromGrid(Grid[][] g) {
        int[][] b = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                b[r][c] = g[r][c].value;
        return b;
    }

    /** int[][] 深拷贝。 */
    static int[][] copyBoard(int[][] src) {
        int[][] dst = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            System.arraycopy(src[r], 0, dst[r], 0, COLS);
        return dst;
    }

    /** 在 int[][] 上跑一次 simulateMove —— 内部转 Grid[][] 调用 src。 */
    static int applyMove(int[][] b, int dir) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                gridBufB[r][c].value = b[r][c];
                gridBufB[r][c].setMerge(false);
            }
        int score = Utils.simulateMove(gridBufB, dir);
        if (score >= 0) {
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    b[r][c] = gridBufB[r][c].value;
        }
        return score;
    }

    /** int[][] 版本的 spawnRandom（向后兼容）。 */
    static void spawnRandom(int[][] board) {
        // 借用 bufB 走 src
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                gridBufB[r][c].value = board[r][c];
        AlgoCommon.spawnRandom(gridBufB);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                board[r][c] = gridBufB[r][c].value;
    }

    /** int[][] 版本的合法方向（向后兼容）。 */
    static List<Integer> getValidMoves(int[][] board) {
        List<Integer> valid = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            int[][] test = copyBoard(board);
            if (applyMove(test, d) >= 0) valid.add(d);
        }
        return valid;
    }

    public static String getDirectionName(int dir) {
        switch (dir) {
            case 0: return "Up";
            case 1: return "Down";
            case 2: return "Left";
            case 3: return "Right";
            default: return "?";
        }
    }
}
