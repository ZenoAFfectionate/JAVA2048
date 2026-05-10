/**
 * 加权贪心算法 — 蛇形权重矩阵强制实现单调性和角落锚定。
 *
 * 权重矩阵: 最大数固定在右下角 (3,3)，权重从角落向外梯度递减。
 *    [[15, 14, 13, 12],
 *     [8,  9,  10, 11],
 *     [7,  6,  5,  4],
 *     [0,  1,  2,  3]]
 *
 * 核心: 每一步选使 Σ(tileValue × weight) 最大的方向，本质是权重矩阵引导数字按蛇形排列。
 *
 * 使用方式: int dir = WeightedGreedy.getBestDirection(grids);
 */
public class WeightedGreedy {

    private static final int ROWS = 4, COLS = 4;

    /** 蛇形权重矩阵 — 右下角 (3,3) 权重最大=15，蛇形递减到左上 */
    static final int[][] WEIGHTS = {
        {3,  2,  1,  0},
        {4,  5,  6,  7},
        {11, 10, 9,  8},
        {12, 13, 14, 15}
    };

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

            double w = weightedSum(grids);
            int empties = countEmpty(grids);
            // 综合: 合并得分(放大) + 位置权重 + 空格奖励
            double score = immediate * 8 + w + empties * 800;

            if (score > bestScore) {
                bestScore = score;
                bestDir = dir;
            }

            Utils.restoreState(grids, savedVals, savedMerges);
        }
        return bestDir;
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

    static int countEmpty(Grid[][] g) {
        int n = 0;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.isEmpty()) n++;
        return n;
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
