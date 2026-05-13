import java.util.*;

/**
 * ExpectmaxTunable — 参数化版本的 Expectimax，用于参数扫描实验。
 *
 * <p>语义与 {@link Expectmax} 完全一致；区别仅在于：
 * <ul>
 *   <li>所有原本的 {@code static final} 参数全部改为可由实验运行器动态设置；</li>
 *   <li>启发式评估权重内置在本类中（不依赖 {@link Utils#heuristic}），
 *       便于扫描启发式相关权重；</li>
 *   <li>采样策略可选：SMART（默认，靠近大数字优先）、RANDOM（均匀随机）、ALL（全部空格）；</li>
 *   <li>对外暴露线程不安全的全局 setter — 实验单线程顺序运行，无并发问题。</li>
 * </ul>
 *
 * <p>所有底盘动作仍然走 src 提供的统一接口
 * （{@link Utils#simulateMove}、{@link Utils#saveValues}、{@link Utils#restoreState}），
 * 与 {@link AlgoCommon} 的语义保持一致；此类<b>仅参数化算法层，不重新实现游戏底盘</b>。
 *
 * <p>使用：
 * <pre>
 *   ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
 *   p.depth = 3;
 *   p.maxSamples = 8;
 *   ExpectmaxTunable.setParams(p);
 *   int dir = ExpectmaxTunable.getBestDirection(grids);
 * </pre>
 */
public class ExpectmaxTunable {

    private static final int ROWS = 4, COLS = 4;

    // ============================================================
    //  参数对象
    // ============================================================

    /** 采样策略 */
    public enum SampleStrategy {
        /** 智能采样 — 优先选择与大数字相邻的空格（与原版一致） */
        SMART,
        /** 均匀随机采样 */
        RANDOM,
        /** 全部空格（不采样） */
        ALL
    }

    /** 算法参数集合（可拷贝、可序列化为字符串）。 */
    public static class Params {
        // ---- 搜索结构 ----
        public int depth = 2;
        public int maxSamples = 4;
        public SampleStrategy sampleStrategy = SampleStrategy.SMART;

        // ---- 启发式权重（默认与 Utils.heuristic 完全一致） ----
        public int wEmpty       = 240;
        public int wMonotonicity = 35;
        public int wSmoothness   = -18;
        public int wCorner       = 60;
        public int wMerge        = 22;

        public Params copy() {
            Params p = new Params();
            p.depth          = this.depth;
            p.maxSamples     = this.maxSamples;
            p.sampleStrategy = this.sampleStrategy;
            p.wEmpty         = this.wEmpty;
            p.wMonotonicity  = this.wMonotonicity;
            p.wSmoothness    = this.wSmoothness;
            p.wCorner        = this.wCorner;
            p.wMerge         = this.wMerge;
            return p;
        }

        @Override
        public String toString() {
            return String.format(
                "Params{depth=%d, maxSamples=%d, sample=%s, wE=%d, wMono=%d, wSmo=%d, wCor=%d, wMer=%d}",
                depth, maxSamples, sampleStrategy,
                wEmpty, wMonotonicity, wSmoothness, wCorner, wMerge);
        }
    }

    public static Params defaultParams() {
        return new Params();
    }

    // ============================================================
    //  全局当前参数（实验为单线程，无需线程安全）
    // ============================================================

    private static volatile Params CUR = defaultParams();
    private static final Random RNG = new Random(20260513L);

    public static void setParams(Params p) {
        if (p == null) throw new IllegalArgumentException("params must not be null");
        if (p.depth < 1) throw new IllegalArgumentException("depth must be >= 1");
        if (p.maxSamples < 1) throw new IllegalArgumentException("maxSamples must be >= 1");
        CUR = p.copy();
    }

    public static Params getParams() {
        return CUR.copy();
    }

    /** 重置到默认参数（=原版 Expectmax + Utils.heuristic）。 */
    public static void resetToDefault() {
        CUR = defaultParams();
    }

    // ============================================================
    //  公开 API：返回当前棋盘最优方向
    // ============================================================

    public static int getBestDirection(Grid[][] grids) {
        final Params p = CUR;
        // 顶层 max 节点
        double[] scores = AlgoCommon.scoreAllDirections(grids,
                (state, dir, immediate) -> immediate + expectimax(state, p.depth - 1, p));

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

    // ============================================================
    //  Expectimax 递归
    // ============================================================

    /** chance 节点 — 环境随机 spawn 2/4 */
    private static double expectimax(Grid[][] g, int depth, Params p) {
        if (depth <= 0) return heuristic(g, p);

        List<int[]> empties = getEmptyCells(g);
        if (empties.isEmpty()) return heuristic(g, p);

        int sampleLimit = Math.min(p.maxSamples, empties.size());
        List<int[]> samples = sample(g, empties, sampleLimit, p.sampleStrategy);

        int[][] baseVals = Utils.saveValues(g);
        boolean[][] baseMerges = Utils.saveMerges(g);

        double futureSum = 0;
        int count = samples.size();

        for (int[] cell : samples) {
            // 放 2 (p=0.9)
            g[cell[0]][cell[1]].value = 2;
            double val2 = bestAfterSpawn(g, depth - 1, p);
            Utils.restoreState(g, baseVals, baseMerges);

            // 放 4 (p=0.1)
            g[cell[0]][cell[1]].value = 4;
            double val4 = bestAfterSpawn(g, depth - 1, p);
            Utils.restoreState(g, baseVals, baseMerges);

            futureSum += 0.9 * val2 + 0.1 * val4;
        }

        return futureSum / count;
    }

    /** spawn 后的 max 节点 */
    private static double bestAfterSpawn(Grid[][] g, int depth, Params p) {
        double[] scores = AlgoCommon.scoreAllDirections(g,
                (state, dir, immediate) -> immediate + expectimax(state, depth, p));

        double best = Double.NEGATIVE_INFINITY;
        for (int d = 0; d < 4; d++)
            if (scores[d] > best) best = scores[d];

        return best == Double.NEGATIVE_INFINITY ? heuristic(g, p) : best;
    }

    // ============================================================
    //  采样策略
    // ============================================================

    private static List<int[]> getEmptyCells(Grid[][] g) {
        List<int[]> list = new ArrayList<>(16);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (g[r][c].isEmpty())
                    list.add(new int[]{r, c});
        return list;
    }

    private static List<int[]> sample(Grid[][] g, List<int[]> empties,
                                      int limit, SampleStrategy strategy) {
        if (empties.size() <= limit || strategy == SampleStrategy.ALL) {
            return new ArrayList<>(empties);
        }
        switch (strategy) {
            case SMART:  return sampleSmart(g, empties, limit);
            case RANDOM: return sampleRandom(empties, limit);
            default:     return new ArrayList<>(empties);
        }
    }

    private static List<int[]> sampleSmart(Grid[][] g, List<int[]> empties, int limit) {
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

    private static List<int[]> sampleRandom(List<int[]> empties, int limit) {
        // Fisher–Yates 取前 limit 个
        List<int[]> shuffled = new ArrayList<>(empties);
        int n = shuffled.size();
        for (int i = 0; i < limit; i++) {
            int j = i + RNG.nextInt(n - i);
            int[] tmp = shuffled.get(i);
            shuffled.set(i, shuffled.get(j));
            shuffled.set(j, tmp);
        }
        return shuffled.subList(0, limit);
    }

    // ============================================================
    //  启发式评估（参数化，不依赖 Utils.heuristic 内置常量）
    //  与 Utils.heuristic 的逻辑保持完全一致，仅权重可调
    // ============================================================

    private static int heuristic(Grid[][] g, Params p) {
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

                if (c < COLS - 1 && g[r][c + 1].value != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(g[r][c + 1].value);
                    smoothPenalty += Math.abs(logV - logN);
                }
                if (r < ROWS - 1 && g[r + 1][c].value != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(g[r + 1][c].value);
                    smoothPenalty += Math.abs(logV - logN);
                }

                if (c < COLS - 1 && g[r][c + 1].value == v) mergeBonus += logV;
                if (r < ROWS - 1 && g[r + 1][c].value == v) mergeBonus += logV;
            }
        }

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

        int cornerBonus = 0;
        if ((maxR == 0 || maxR == 3) && (maxC == 0 || maxC == 3)) {
            cornerBonus = 31 - Integer.numberOfLeadingZeros(maxVal);
        }

        return p.wEmpty * empty
                + p.wMonotonicity * (monoRow + monoCol)
                + p.wSmoothness * smoothPenalty
                + p.wCorner * cornerBonus
                + p.wMerge * mergeBonus;
    }
}
