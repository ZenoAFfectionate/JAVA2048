import javax.swing.*;
import java.io.*;
import java.util.*;

/**
 * 工具类 — XML 读写、AI 智能提示
 *
 * AI 核心: 2-ply Expectimax + 启发式评估函数
 *
 * 搜索结构:
 *   当前棋盘 → 模拟四个方向的移动 → 在空格随机落子(2/4) →
 *   再模拟四个方向的移动 → 启发式评估终止状态
 *
 * 优化要点:
 *   - 使用原始数组 save/restore 替代深拷贝，消除内部循环的对象分配
 *   - 启发式函数综合考虑: 空位数、单调性、平滑度、角落最大值、合并潜力
 *   - 智能采样: 优先采样与大数字相邻的空格
 *   - 期望值归一化: 按采样数平均而非累加
 */
public class Utils {

    private static final int ROWS = 4;
    private static final int COLS = 4;

    // ---- 启发式权重 (量级对齐于合并得分 0~3000) ----
    private static final int W_EMPTY       = 240;   // 每个空格的价值
    private static final int W_MONOTONICITY = 35;   // 单调性每单位
    private static final int W_SMOOTHNESS   = -18;  // 平滑度惩罚 (负值)
    private static final int W_CORNER       = 60;   // 最大值在角落
    private static final int W_MERGE        = 22;   // 相邻相等 bonus (log2)

    // ---- 方向常量 ----
    public static final int DIR_UP    = 0;
    public static final int DIR_DOWN  = 1;
    public static final int DIR_LEFT  = 2;
    public static final int DIR_RIGHT = 3;

    static final String[] DIR_NAMES = {"Move Up", "Move Down", "Move Left", "Move Right"};

    // ---- XML 存档 (未改) ----

    public static void writeXML(List<String> keys, List<String> values, String fileName)
            throws FileNotFoundException {
        try (PrintWriter writer = new PrintWriter(fileName)) {
            for (int i = 0; i < keys.size(); i++) {
                writer.println("<" + keys.get(i) + ">" + values.get(i) + "</" + keys.get(i) + ">");
            }
        }
    }

    // ================================================================
    //  棋盘状态 save / restore (原始数组，极低成本)
    // ================================================================

    public static int[][] saveValues(Grid[][] g) {
        int[][] v = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                v[r][c] = g[r][c].value;
        return v;
    }

    public static boolean[][] saveMerges(Grid[][] g) {
        boolean[][] m = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                m[r][c] = g[r][c].isMerged();
        return m;
    }

    public static void restoreState(Grid[][] g, int[][] vals, boolean[][] merges) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                g[r][c].value = vals[r][c];
                g[r][c].setMerge(merges[r][c]);
            }
    }

    // ================================================================
    //  深拷贝 (仅在评估入口调用一次)
    // ================================================================

    public static Grid[][] deepCopy(Grid[][] src) {
        Grid[][] dst = new Grid[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                dst[r][c] = src[r][c].copy();
        return dst;
    }

    // ================================================================
    //  启发式评估函数
    // ================================================================

    /**
     * 综合评估棋盘局势，返回值与合并得分在同一量级。
     *
     * 评估维度:
     *   1. 空位数 — 越多选择空间越大
     *   2. 单调性 — 数字是否沿某方向有序排列 (便于级联合并)
     *   3. 平滑度 — 相邻格子值差异越小越容易合并 (惩罚大差异)
     *   4. 角落奖励 — 最大值在角落更稳定
     *   5. 合并潜力 — 相邻相等值的 bonus
     */
    public static int heuristic(Grid[][] g) {
        int empty = 0;
        int monoRow = 0, monoCol = 0;
        int smoothPenalty = 0;
        int maxVal = 0, maxR = 0, maxC = 0;
        int mergeBonus = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int v = g[r][c].value;
                if (v == 0) { empty++; continue; }

                int logV = 31 - Integer.numberOfLeadingZeros(v);
                if (v > maxVal) { maxVal = v; maxR = r; maxC = c; }

                // 平滑度: 与右方、下方邻居的 log2 差异
                if (c < COLS - 1 && g[r][c + 1].value != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(g[r][c + 1].value);
                    smoothPenalty += Math.abs(logV - logN);
                }
                if (r < ROWS - 1 && g[r + 1][c].value != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(g[r + 1][c].value);
                    smoothPenalty += Math.abs(logV - logN);
                }

                // 合并潜力: 相邻相等
                if (c < COLS - 1 && g[r][c + 1].value == v) mergeBonus += logV;
                if (r < ROWS - 1 && g[r + 1][c].value == v) mergeBonus += logV;
            }
        }

        // 单调性: 偏向从左到右、从上到下递减 (经典 2048 策略)
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS - 1; c++) {
                if (g[r][c].value != 0 && g[r][c + 1].value != 0) {
                    if (g[r][c].value >= g[r][c + 1].value) monoRow++;
                    else monoRow--;
                }
            }
        }
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS - 1; r++) {
                if (g[r][c].value != 0 && g[r + 1][c].value != 0) {
                    if (g[r][c].value >= g[r + 1][c].value) monoCol++;
                    else monoCol--;
                }
            }
        }

        // 角落奖励: max tile 在角落时给 bonus (按 log2 比例)
        int cornerBonus = 0;
        if ((maxR == 0 || maxR == 3) && (maxC == 0 || maxC == 3)) {
            cornerBonus = 31 - Integer.numberOfLeadingZeros(maxVal);
        }

        return W_EMPTY * empty
                + W_MONOTONICITY * (monoRow + monoCol)
                + W_SMOOTHNESS * smoothPenalty
                + W_CORNER * cornerBonus
                + W_MERGE * mergeBonus;
    }

    // ================================================================
    //  移动模拟 (in-place)
    // ================================================================

    public static void clearMerge(Grid[][] g) {
        for (Grid[] row : g)
            for (Grid t : row)
                t.setMerge(false);
    }

    /** 在 g 上原地模拟一个方向移动，返回合并得分；不可行返回 -1 */
    public static int simulateMove(Grid[][] g, int dir) {
        clearMerge(g);
        int score = 0;
        boolean moved = false;

        switch (dir) {
            case DIR_UP:
                for (int c = 0; c < COLS; c++)
                    for (int r = 1; r < ROWS; r++)
                        if (!g[r][c].isEmpty()) {
                            int s = g[r][c].moveUp(g, r, c);
                            if (s >= 0) { score += s; moved = true; }
                        }
                break;
            case DIR_DOWN:
                for (int c = 0; c < COLS; c++)
                    for (int r = ROWS - 2; r >= 0; r--)
                        if (!g[r][c].isEmpty()) {
                            int s = g[r][c].moveDown(g, r, c);
                            if (s >= 0) { score += s; moved = true; }
                        }
                break;
            case DIR_LEFT:
                for (int r = 0; r < ROWS; r++)
                    for (int c = 1; c < COLS; c++)
                        if (!g[r][c].isEmpty()) {
                            int s = g[r][c].moveLeft(g, r, c);
                            if (s >= 0) { score += s; moved = true; }
                        }
                break;
            case DIR_RIGHT:
                for (int r = 0; r < ROWS; r++)
                    for (int c = COLS - 2; c >= 0; c--)
                        if (!g[r][c].isEmpty()) {
                            int s = g[r][c].moveRight(g, r, c);
                            if (s >= 0) { score += s; moved = true; }
                        }
                break;
        }
        return moved ? score : -1;
    }

    // ================================================================
    //  bestMoveValue — 一步最优移动的 (得分 + 启发值)
    //  使用 save/restore 而非深拷贝
    // ================================================================

    /**
     * 在 g 上尝试四个方向，返回最优的 (immediate_score + heuristic(result))。
     * 调用后 g 恢复原状。
     *
     * BUG 修复（2026-05-11）：
     *   - 旧实现 best = heuristic(g)，把"不动"当合法选项 → 小幅合并被基线淹没。
     *   - 旧实现 illegal 方向不 restore；但 simulateMove() 即使返回 -1 也已 clearMerge()。
     */
    private static int bestMoveValue(Grid[][] g) {
        int[][] savedVals = saveValues(g);
        boolean[][] savedMerges = saveMerges(g);

        int best = Integer.MIN_VALUE;

        for (int dir = 0; dir < 4; dir++) {
            int score = simulateMove(g, dir);
            if (score >= 0) {
                int value = score + heuristic(g);
                if (value > best) best = value;
            }
            // 不论合法与否都要 restore（simulateMove 内部已 clearMerge）
            restoreState(g, savedVals, savedMerges);
        }
        return best == Integer.MIN_VALUE ? heuristic(g) : best;
    }

    // ================================================================
    //  空格工具
    // ================================================================

    public static List<int[]> getEmptyCells(Grid[][] g) {
        List<int[]> list = new ArrayList<>(16);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (g[r][c].isEmpty())
                    list.add(new int[]{r, c});
        return list;
    }

    /**
     * 智能采样 — 优先选择与大数字相邻的空格。
     * 大数字周围的空格对局势影响更大。
     */
    public static List<int[]> smartSample(Grid[][] g, List<int[]> empties, int limit) {
        if (empties.size() <= limit) return new ArrayList<>(empties);

        // Score each empty cell by max adjacent value
        int n = empties.size();
        int[][] scored = new int[n][2];
        for (int i = 0; i < n; i++) {
            int[] cell = empties.get(i);
            int maxAdj = 0;
            if (cell[0] > 0) maxAdj = Math.max(maxAdj, g[cell[0] - 1][cell[1]].value);
            if (cell[0] < 3) maxAdj = Math.max(maxAdj, g[cell[0] + 1][cell[1]].value);
            if (cell[1] > 0) maxAdj = Math.max(maxAdj, g[cell[0]][cell[1] - 1].value);
            if (cell[1] < 3) maxAdj = Math.max(maxAdj, g[cell[0]][cell[1] + 1].value);
            scored[i][0] = i;
            scored[i][1] = maxAdj;
        }

        Arrays.sort(scored, (a, b) -> Integer.compare(b[1], a[1]));

        List<int[]> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            result.add(empties.get(scored[i][0]));
        }
        return result;
    }

    // ================================================================
    //  evaluateMoves — 对四个方向做 2-ply Expectimax
    // ================================================================

    /**
     * 对每个方向:
     *   1. 深拷贝一次 → sim
     *   2. simulateMove(sim, dir) → immediate_score
     *   3. 在 sim 上智能采样空格
     *   4. 对每个采样空格分别落 2 (p=0.9) 和 4 (p=0.1)
     *   5. 用 save/restore 调用 bestMoveValue 求后续价值
     *   6. 期望值 = immediate_score + (Σ weight × future) / sampleCount
     */
    private static double[] evaluateMoves(Grid[][] grids) {
        double[] scores = new double[4];
        Arrays.fill(scores, Double.NEGATIVE_INFINITY);

        for (int dir = 0; dir < 4; dir++) {
            Grid[][] sim = deepCopy(grids);
            int immediate = simulateMove(sim, dir);
            if (immediate < 0) continue;  // 非法方向

            List<int[]> empties = getEmptyCells(sim);
            if (empties.isEmpty()) {
                scores[dir] = heuristic(sim);
                continue;
            }

            List<int[]> samples = smartSample(sim, empties, 6);

            // save sim state for restore during sampling
            int[][] baseVals = saveValues(sim);
            boolean[][] baseMerges = saveMerges(sim);

            double futureSum = 0;
            int sampleCount = samples.size();

            for (int[] cell : samples) {
                // 放 2 (权重 0.9)
                sim[cell[0]][cell[1]].value = 2;
                int best2 = bestMoveValue(sim);
                restoreState(sim, baseVals, baseMerges);

                // 放 4 (权重 0.1)
                sim[cell[0]][cell[1]].value = 4;
                int best4 = bestMoveValue(sim);
                restoreState(sim, baseVals, baseMerges);

                futureSum += 0.9 * best2 + 0.1 * best4;
            }

            // 归一化: futureSum / sampleCount (而非累加)
            scores[dir] = immediate + futureSum / sampleCount;
        }
        return scores;
    }

    // ================================================================
    //  公开 API
    // ================================================================

    /** 返回最优方向名称 */
    public static String getBestMove(Grid[][] grids) {
        double[] scores = evaluateMoves(grids);

        int best = 0;
        for (int i = 1; i < 4; i++) {
            if (scores[i] > scores[best]) best = i;
        }

        // 所有方向都不可行 (理论上不会出现在正常游戏中)
        if (scores[best] == Double.NEGATIVE_INFINITY) {
            return "Move Up";  // fallback
        }

        return DIR_NAMES[best];
    }

    /** 弹出最优解提示框 */
    public static void tips(Grid[][] grids) {
        String best = getBestMove(grids);
        JOptionPane.showMessageDialog(null, best,
                "Optimal Solution", JOptionPane.INFORMATION_MESSAGE);
    }
}
