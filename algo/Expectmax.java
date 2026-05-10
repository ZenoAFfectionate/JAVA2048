import java.util.*;

/**
 * Expectimax 搜索算法 — 2048 是随机博弈 (对手是随机环境而非敌对玩家)，
 * 因此使用 Expectimax 而非 Minimax。
 *
 * 节点类型:
 *   - Max 节点 (玩家回合): 遍历 4 方向，选择最大化期望得分的动作
 *   - Chance 节点 (环境回合): 枚举空格生成 2/4 的可能，计算期望值
 *
 * 优化:
 *   - save/restore 原地操作，避免频繁深拷贝
 *   - 智能采样: 优先采样靠近大数字的空格
 *   - 自适应深度: 空格越少搜索越深
 *
 * 使用方式: int dir = Expectmax.getBestDirection(grids);
 */
public class Expectmax {

    private static final int ROWS = 4, COLS = 4;

    /** 搜索深度 (固定 2-ply，平衡速度与质量) */
    private static final int DEPTH = 2;

    /** 每层采样空格数上限 */
    private static final int MAX_SAMPLES = 4;

    /**
     * 返回当前棋盘的最优方向 (0=UP, 1=DOWN, 2=LEFT, 3=RIGHT)
     */
    public static int getBestDirection(Grid[][] grids) {
        int depth = DEPTH;

        double[] scores = evaluateMoves(grids, depth);

        int best = 0;
        for (int i = 1; i < 4; i++) {
            if (scores[i] > scores[best]) best = i;
        }
        return scores[best] == Double.NEGATIVE_INFINITY ? 0 : best;
    }

    /** 对 4 个方向做 depth-ply Expectimax */
    private static double[] evaluateMoves(Grid[][] grids, int maxDepth) {
        double[] scores = new double[4];
        Arrays.fill(scores, Double.NEGATIVE_INFINITY);

        for (int dir = 0; dir < 4; dir++) {
            int[][] savedVals = Utils.saveValues(grids);
            boolean[][] savedMerges = Utils.saveMerges(grids);

            int immediate = Utils.simulateMove(grids, dir);
            if (immediate < 0) {
                Utils.restoreState(grids, savedVals, savedMerges);
                continue;
            }

            double future = expectimax(grids, maxDepth - 1);
            scores[dir] = immediate + future;

            Utils.restoreState(grids, savedVals, savedMerges);
        }
        return scores;
    }

    /**
     * 递归 Expectimax:
     *   depth==0 或 游戏结束 → heuristic()
     *   depth>0: 空格采样 → spawn 2/4 → 四方向取 max → 期望平均
     */
    private static double expectimax(Grid[][] g, int depth) {
        if (depth <= 0) return Utils.heuristic(g);

        List<int[]> empties = getEmptyCells(g);
        if (empties.isEmpty()) return Utils.heuristic(g);

        // 智能采样
        int sampleLimit = Math.min(MAX_SAMPLES, empties.size());
        List<int[]> samples = sampleSmart(g, empties, sampleLimit);

        int[][] baseVals = Utils.saveValues(g);
        boolean[][] baseMerges = Utils.saveMerges(g);

        double futureSum = 0;
        int count = samples.size();

        for (int[] cell : samples) {
            // 放 2 (p=0.9)
            g[cell[0]][cell[1]].value = 2;
            double val2 = bestAfterSpawn(g, depth - 1);
            Utils.restoreState(g, baseVals, baseMerges);

            // 放 4 (p=0.1)
            g[cell[0]][cell[1]].value = 4;
            double val4 = bestAfterSpawn(g, depth - 1);
            Utils.restoreState(g, baseVals, baseMerges);

            futureSum += 0.9 * val2 + 0.1 * val4;
        }

        return futureSum / count;
    }

    /** spawn 后做一次四方向 max */
    private static double bestAfterSpawn(Grid[][] g, int depth) {
        double best = Utils.heuristic(g);

        int[][] savedVals = Utils.saveValues(g);
        boolean[][] savedMerges = Utils.saveMerges(g);

        for (int dir = 0; dir < 4; dir++) {
            int score = Utils.simulateMove(g, dir);
            if (score >= 0) {
                double val = score + expectimax(g, depth);
                if (val > best) best = val;
                Utils.restoreState(g, savedVals, savedMerges);
            }
        }
        return best;
    }

    // ---- helpers ----

    private static List<int[]> getEmptyCells(Grid[][] g) {
        List<int[]> list = new ArrayList<>(16);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (g[r][c].isEmpty())
                    list.add(new int[]{r, c});
        return list;
    }

    /** 智能采样 — 优先选择靠近大数字的空格 */
    private static List<int[]> sampleSmart(Grid[][] g, List<int[]> empties, int limit) {
        if (empties.size() <= limit) return new ArrayList<>(empties);

        int n = empties.size();
        int[][] scored = new int[n][2];
        for (int i = 0; i < n; i++) {
            int[] cell = empties.get(i);
            int maxAdj = 0;
            int r = cell[0], c = cell[1];
            if (r > 0) maxAdj = Math.max(maxAdj, g[r - 1][c].value);
            if (r < 3) maxAdj = Math.max(maxAdj, g[r + 1][c].value);
            if (c > 0) maxAdj = Math.max(maxAdj, g[r][c - 1].value);
            if (c < 3) maxAdj = Math.max(maxAdj, g[r][c + 1].value);
            scored[i][0] = i;
            scored[i][1] = maxAdj;
        }
        Arrays.sort(scored, (a, b) -> Integer.compare(b[1], a[1]));

        List<int[]> result = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++)
            result.add(empties.get(scored[i][0]));
        return result;
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
