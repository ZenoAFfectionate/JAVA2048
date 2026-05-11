/**
 * 固定启发式算法 — 复刻人类高手的 2048 三大黄金规则，通过加权评分函数量化局面优劣。
 *
 * <h3>核心原理</h3>
 * <ol>
 *   <li>角落锚定 — 奖励最大值在主锚定角 (3,3)，惩罚 max 离开主角</li>
 *   <li>单调性   — 行/列按从大到小梯度排列，便于级联合并</li>
 *   <li>平滑性   — 相邻格子 log2 差异尽可能小</li>
 *   <li>空格奖励 — 空格越多选择越多</li>
 * </ol>
 *
 * <p>每一步：模拟 4 个方向 → 对结果棋盘打分 → 选择得分最高的方向。
 *
 * <h3>统一口径（2026-05-11 重构）</h3>
 * <ul>
 *   <li>使用 {@link AlgoCommon#pickBestDirection} 统一接口；
 *       4 算法走同一个 src 游戏底盘 + 同一个 illegal/restore 模板。</li>
 *   <li>相比手写循环：消除了"忘记 restore"类 BUG，简化代码 60%。</li>
 * </ul>
 */
public class FixHeuristic {

    // ---- 评估函数权重 ----
    private static final int W_CORNER  = 120;
    private static final int W_MONO    = 40;
    private static final int W_SMOOTH  = -22;
    private static final int W_EMPTY   = 280;
    private static final int W_MERGE   = 25;

    /** 主锚定角落（右下）— 与 WeightedGreedy 蛇形矩阵的 max 位置一致 */
    private static final int ANCHOR_R = 3, ANCHOR_C = 3;
    /** max 不在主锚定角的惩罚（按 log2(maxVal) 缩放，量级与 W_EMPTY*empty 匹配） */
    private static final int W_OFF_ANCHOR = 200;

    private static final int ROWS = 4, COLS = 4;

    /**
     * 返回当前棋盘的最优方向 (0=UP, 1=DOWN, 2=LEFT, 3=RIGHT)。
     */
    public static int getBestDirection(Grid[][] grids) {
        return AlgoCommon.pickBestDirection(grids, FixHeuristic::scoreMove);
    }

    /**
     * 评估一个 simulate 后的局面：
     *   immediate（合并得分）+ heuristic 评估 + 主锚定角偏好惩罚
     */
    private static double scoreMove(Grid[][] state, int dir, int immediate) {
        double score = immediate + evaluate(state);

        // max 离开主锚定角 (3,3) 时按 log2(max) 缩放惩罚；
        // 仅奖励单一主角，避免 max 在 4 角之间跳动破坏单调梯度。
        int[] mp = AlgoCommon.findMax(state);
        int maxR = mp[0], maxC = mp[1], maxVal = mp[2];
        if ((maxR != ANCHOR_R || maxC != ANCHOR_C) && maxVal > 0) {
            int logMax = 31 - Integer.numberOfLeadingZeros(maxVal);
            score -= (double) W_OFF_ANCHOR * logMax;
        }
        return score;
    }

    /**
     * 启发式评估函数 — 综合空格、单调性、平滑度、合并潜力、主角奖励。
     * 量级与 W_EMPTY × empty 在数千 ~ 上万。
     */
    static int evaluate(Grid[][] g) {
        int empty = 0;
        int monoRow = 0, monoCol = 0;
        int smoothPenalty = 0;
        int mergeBonus = 0;
        int maxVal = 0, maxR = 0, maxC = 0;

        // 单遍扫描：空格、最大值定位、平滑度、合并潜力
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

        // 单调性：行递减 + 列递减
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

        // 角落奖励：仅奖励"主锚定角"(3,3)；其他三角不给奖励 → 单调梯度稳定建立
        int cornerBonus = 0;
        if (maxR == ANCHOR_R && maxC == ANCHOR_C) {
            cornerBonus = 31 - Integer.numberOfLeadingZeros(maxVal);
        }

        return W_EMPTY * empty
             + W_MONO * (monoRow + monoCol)
             + W_SMOOTH * smoothPenalty
             + W_CORNER * cornerBonus
             + W_MERGE * mergeBonus;
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
