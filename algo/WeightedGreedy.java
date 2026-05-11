/**
 * 加权贪心算法 — 蛇形权重矩阵强制实现单调性和角落锚定。
 *
 * <p>权重矩阵：最大数固定在右下角 (3,3)，权重从角落向外梯度递减（蛇形）：
 * <pre>
 *    [[ 3,  2,  1,  0],
 *     [ 4,  5,  6,  7],
 *     [11, 10,  9,  8],
 *     [12, 13, 14, 15]]
 * </pre>
 *
 * <p>核心：每一步选使 Σ(tileValue × weight) 最大的方向，本质是权重矩阵
 * 引导数字按蛇形排列。
 *
 * <h3>统一口径（2026-05-11 重构）</h3>
 * 使用 {@link AlgoCommon#pickBestDirection}，与其他算法走同一个 src 游戏底盘。
 */
public class WeightedGreedy {

    private static final int ROWS = 4, COLS = 4;

    /** 蛇形权重矩阵 — 右下角 (3,3) 权重最大=15，蛇形递减到左上 */
    static final int[][] WEIGHTS = {
        { 3,  2,  1,  0},
        { 4,  5,  6,  7},
        {11, 10,  9,  8},
        {12, 13, 14, 15}
    };

    /** 综合评分时各项权重 */
    private static final int W_IMMEDIATE = 8;     // 合并得分倍率
    private static final int W_EMPTY     = 800;   // 每个空格价值

    /**
     * 返回当前棋盘的最优方向 (0=UP, 1=DOWN, 2=LEFT, 3=RIGHT)
     */
    public static int getBestDirection(Grid[][] grids) {
        return AlgoCommon.pickBestDirection(grids, WeightedGreedy::scoreMove);
    }

    private static double scoreMove(Grid[][] state, int dir, int immediate) {
        double w = weightedSum(state);
        int empties = AlgoCommon.countEmpty(state);
        return immediate * W_IMMEDIATE + w + empties * W_EMPTY;
    }

    /** 计算加权和: Σ(tileValue × WEIGHTS[r][c]) */
    static double weightedSum(Grid[][] g) {
        double sum = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (!g[r][c].isEmpty())
                    sum += (double) g[r][c].value * WEIGHTS[r][c];
        return sum;
    }

    /** 数空格 — 保留旧 API 供测试使用 */
    static int countEmpty(Grid[][] g) {
        return AlgoCommon.countEmpty(g);
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
