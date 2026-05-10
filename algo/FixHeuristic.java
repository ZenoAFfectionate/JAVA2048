import java.util.*;

/**
 * 固定启发式算法 — 复刻人类高手的 2048 三大黄金规则，通过加权评分函数量化局面优劣。
 *
 * 核心原理:
 *   1. 角落锚定 — 奖励最大值在角落，禁止移动后最大值离开角落
 *   2. 单调性   — 行/列按从大到小梯度排列，便于级联合并
 *   3. 平滑性   — 相邻格子 log2 差异尽可能小
 *   4. 空格奖励 — 空格越多选择越多
 *
 * 每一步: 模拟 4 个方向 → 对结果棋盘打分 → 选择得分最高的方向
 *
 * 使用方式: int dir = FixHeuristic.getBestDirection(grids);
 *           String name = FixHeuristic.getDirectionName(dir);
 */
public class FixHeuristic {

    // ---- 权重 (调优至平衡) ----
    private static final int W_CORNER  = 120;   // 最大值在角落的每 log2 奖励
    private static final int W_MONO    = 40;    // 单调性每单位得分
    private static final int W_SMOOTH  = -22;   // 平滑度每单位惩罚
    private static final int W_EMPTY   = 280;   // 每个空格价值
    private static final int W_MERGE   = 25;    // 相邻相等 bonus (log2)

    private static final int ROWS = 4, COLS = 4;

    /**
     * 返回当前棋盘的最优方向 (0=UP, 1=DOWN, 2=LEFT, 3=RIGHT)
     */
    public static int getBestDirection(Grid[][] grids) {
        int bestDir = 0;
        double bestScore = Double.NEGATIVE_INFINITY;

        int[][] savedVals = Utils.saveValues(grids);
        boolean[][] savedMerges = Utils.saveMerges(grids);

        for (int dir = 0; dir < 4; dir++) {
            int immediate = Utils.simulateMove(grids, dir);
            if (immediate < 0) continue;

            double score = immediate + evaluate(grids);

            // 惩罚移动最大值离开角落
            int[] maxPos = findMax(grids);
            if (!isCorner(maxPos[0], maxPos[1])) {
                score -= 500;  // 强惩罚
            }

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }

            Utils.restoreState(grids, savedVals, savedMerges);
        }
        return bestDir;
    }

    /**
     * 启发式评估函数 — 综合单调性、平滑度、空格数、合并潜力、角落锚定
     */
    static int evaluate(Grid[][] g) {
        int empty = 0;
        int monoRow = 0, monoCol = 0;
        int smoothPenalty = 0;
        int mergeBonus = 0;
        int maxVal = 0, maxR = 0, maxC = 0;

        // 单遍扫描: 空格、最大值定位、平滑度、合并潜力
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int v = g[r][c].value;
                if (v == 0) { empty++; continue; }

                int logV = 31 - Integer.numberOfLeadingZeros(v);
                if (v > maxVal) { maxVal = v; maxR = r; maxC = c; }

                if (c + 1 < COLS && g[r][c + 1].value != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(g[r][c + 1].value);
                    smoothPenalty += Math.abs(logV - logN);
                }
                if (r + 1 < ROWS && g[r + 1][c].value != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(g[r + 1][c].value);
                    smoothPenalty += Math.abs(logV - logN);
                }

                if (c + 1 < COLS && g[r][c + 1].value == v) mergeBonus += logV;
                if (r + 1 < ROWS && g[r + 1][c].value == v) mergeBonus += logV;
            }
        }

        // 单调性: 行递减 + 列递减
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS - 1; c++) {
                if (g[r][c].value != 0 && g[r][c + 1].value != 0) {
                    monoRow += (g[r][c].value >= g[r][c + 1].value) ? 1 : -1;
                }
            }
        }
        for (int c = 0; c < COLS; c++) {
            for (int r = 0; r < ROWS - 1; r++) {
                if (g[r][c].value != 0 && g[r + 1][c].value != 0) {
                    monoCol += (g[r][c].value >= g[r + 1][c].value) ? 1 : -1;
                }
            }
        }

        int cornerBonus = 0;
        if (isCorner(maxR, maxC)) {
            cornerBonus = 31 - Integer.numberOfLeadingZeros(maxVal);
        }

        return W_EMPTY * empty
             + W_MONO * (monoRow + monoCol)
             + W_SMOOTH * smoothPenalty
             + W_CORNER * cornerBonus
             + W_MERGE * mergeBonus;
    }

    // ---- helpers ----

    private static boolean isCorner(int r, int c) {
        return (r == 0 || r == 3) && (c == 0 || c == 3);
    }

    private static int[] findMax(Grid[][] g) {
        int mv = 0, mr = 0, mc = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (g[r][c].value > mv) { mv = g[r][c].value; mr = r; mc = c; }
        return new int[]{mr, mc};
    }

    /** 方向常量 → 可读名称 */
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
