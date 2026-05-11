import java.util.*;

/**
 * Expectimax 搜索算法 — 2048 是随机博弈 (对手是随机环境而非敌对玩家)，
 * 因此使用 Expectimax 而非 Minimax。
 *
 * <h3>节点类型</h3>
 * <ul>
 *   <li>Max 节点 (玩家回合): 遍历 4 方向，选择最大化期望得分的动作</li>
 *   <li>Chance 节点 (环境回合): 枚举空格生成 2/4 的可能，计算期望值</li>
 * </ul>
 *
 * <h3>优化</h3>
 * <ul>
 *   <li>save/restore 原地操作，避免频繁深拷贝</li>
 *   <li>智能采样: 优先采样靠近大数字的空格</li>
 *   <li>固定 2-ply（实际深度 4）</li>
 * </ul>
 *
 * <h3>统一口径（2026-05-11 重构）</h3>
 * <ul>
 *   <li>顶层 4 方向选择：使用 {@link AlgoCommon#scoreAllDirections}</li>
 *   <li>递归 max 节点：使用 {@link AlgoCommon#scoreAllDirections}（仅在合法方向中取 max）</li>
 *   <li>所有 simulate 都走 src 的 {@link Utils#simulateMove}</li>
 *   <li>所有评估都走 src 的 {@link Utils#heuristic}</li>
 * </ul>
 */
public class Expectmax {

    private static final int ROWS = 4, COLS = 4;

    /** 搜索深度 (固定 2-ply，实际递归深度 4) */
    private static final int DEPTH = 2;

    /** 每层采样空格数上限 */
    private static final int MAX_SAMPLES = 4;

    /**
     * 返回当前棋盘的最优方向 (0=UP, 1=DOWN, 2=LEFT, 3=RIGHT)
     */
    public static int getBestDirection(Grid[][] grids) {
        // 顶层 max 节点：用 AlgoCommon 统一接口
        double[] scores = AlgoCommon.scoreAllDirections(grids,
                (state, dir, immediate) -> immediate + expectimax(state, DEPTH - 1));

        int best = -1;
        double bestVal = Double.NEGATIVE_INFINITY;
        for (int d = 0; d < 4; d++) {
            if (scores[d] > bestVal) {
                bestVal = scores[d];
                best = d;
            }
        }
        return best < 0 ? 0 : best;
    }

    /**
     * 递归 Expectimax (chance 节点 — 环境随机 spawn 2/4)。
     *
     * <p>depth==0 或无空格 → 启发式叶子兜底；
     * depth&gt;0：智能采样空格 → 落 2(0.9)/4(0.1) → 调用 max 节点 → 期望平均
     */
    private static double expectimax(Grid[][] g, int depth) {
        if (depth <= 0) return Utils.heuristic(g);

        List<int[]> empties = getEmptyCells(g);
        if (empties.isEmpty()) return Utils.heuristic(g);

        int sampleLimit = Math.min(MAX_SAMPLES, empties.size());
        List<int[]> samples = sampleSmart(g, empties, sampleLimit);

        // 注意：spawn 修改 g 后必须 restore；用 save/restore 而非 deepCopy 节省开销。
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

    /**
     * spawn 后的 max 节点 — 在 4 方向中取最优。
     *
     * <p>用 {@link AlgoCommon#scoreAllDirections} 保证：
     * <ul>
     *   <li>所有 4 方向都 save/restore（含 illegal）；</li>
     *   <li>仅在合法方向中比较；4 方向全非法 → 终态用 heuristic 兜底。</li>
     * </ul>
     */
    private static double bestAfterSpawn(Grid[][] g, int depth) {
        double[] scores = AlgoCommon.scoreAllDirections(g,
                (state, dir, immediate) -> immediate + expectimax(state, depth));

        double best = Double.NEGATIVE_INFINITY;
        for (int d = 0; d < 4; d++)
            if (scores[d] > best) best = scores[d];

        return best == Double.NEGATIVE_INFINITY ? Utils.heuristic(g) : best;
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

    /** 智能采样 — 优先选择与大数字相邻的空格 */
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
