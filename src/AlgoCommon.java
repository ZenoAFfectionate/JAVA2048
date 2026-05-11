import java.util.*;

/**
 * AlgoCommon — 4 个算法 (FixHeuristic / WeightedGreedy / Expectmax / MCTS) 共用的
 * 统一口径，封装对 src 游戏底盘 (Utils.simulateMove + Grid[][]) 的调用。
 *
 * <h3>设计目标</h3>
 * 1. <b>统一游戏底盘</b>：所有算法的"试一步"全部走 {@link Utils#simulateMove}，
 *    与 GameView 真实游戏完全一致，避免出现"第二份滑块实现"。
 * 2. <b>统一状态保护</b>：每次 simulate 都包在 saveValues + saveMerges + restoreState 中。
 *    即使方向非法（simulateMove 返回 -1），merge 标志也已被 clearMerge() 改写，必须 restore。
 * 3. <b>统一选择语义</b>：仅在合法方向中比较分数；4 方向全非法时 fallback 到 0（与
 *    ExperimentRunner 的"无合法方向 → 终止"语义兼容）。
 *
 * <h3>用法</h3>
 * <pre>
 *   int dir = AlgoCommon.pickBestDirection(grids, (g, d, imm) -&gt; {
 *       // 在 g（已 simulateMove(d) 后）上算一个分数
 *       return imm + myEvaluator(g);
 *   });
 * </pre>
 *
 * <h3>正确性保证</h3>
 * 由 {@link SimulateEquivalenceTest} 与 {@link GameLogicCorrectnessTest} 证明
 * 此接口与 src 游戏底盘语义完全一致；20000 次随机棋盘对比零差异。
 */
public class AlgoCommon {

    private static final int ROWS = 4, COLS = 4;
    private static final Random RNG = new Random();

    private AlgoCommon() {}  // utility class, 禁止实例化

    // ================================================================
    //  函数式接口：把"在 simulate 后的局面上打分"抽出来
    // ================================================================

    /**
     * 移动评估函数。
     *
     * @param state simulateMove(dir) 之后的棋盘（in-place 修改）。
     *              评估函数<b>必须不修改 state.value</b>（merge 标志可读）。
     * @param dir 当前评估的方向 0=UP 1=DOWN 2=LEFT 3=RIGHT
     * @param immediate Utils.simulateMove 返回的合并得分 (≥0)
     * @return 综合得分；越大越好。
     */
    @FunctionalInterface
    public interface MoveEvaluator {
        double evaluate(Grid[][] state, int dir, int immediate);
    }

    // ================================================================
    //  统一选择入口
    // ================================================================

    /**
     * 在 {@code grids} 上对 4 个方向做评估，返回得分最高的合法方向。
     *
     * <p>严格保证：
     * <ul>
     *   <li>调用前后 {@code grids} 的 value 与 merge 状态完全一致；</li>
     *   <li>每次 simulate 都先 save、后 restore，无论是否合法；</li>
     *   <li>仅在合法方向间取 max；4 方向全非法时返回 0（玩家被卡住）。</li>
     * </ul>
     *
     * @param grids 当前棋盘（不会被修改）
     * @param eval 评估函数；接收 simulate 后的局面与 immediate 得分
     * @return 最优方向 0=UP 1=DOWN 2=LEFT 3=RIGHT
     */
    public static int pickBestDirection(Grid[][] grids, MoveEvaluator eval) {
        int[][] savedVals = Utils.saveValues(grids);
        boolean[][] savedMerges = Utils.saveMerges(grids);

        int bestDir = -1;
        double bestScore = Double.NEGATIVE_INFINITY;

        for (int dir = 0; dir < 4; dir++) {
            int immediate = Utils.simulateMove(grids, dir);
            if (immediate >= 0) {
                double score = eval.evaluate(grids, dir, immediate);
                if (score > bestScore) {
                    bestScore = score;
                    bestDir = dir;
                }
            }
            // ★ 关键：无论 simulateMove 返回 -1 还是 ≥0，都必须 restore，
            //   因为 simulateMove() 在第一行就 clearMerge() 改写了状态。
            Utils.restoreState(grids, savedVals, savedMerges);
        }
        return bestDir < 0 ? 0 : bestDir;
    }

    /**
     * 保留版本：仅返回每个方向的"原始得分数组"（包含 NEG_INFINITY 表示非法）。
     * 用于 Expectmax 等需要"是否合法"细节的场景。
     *
     * <p>调用后 {@code grids} 完全恢复原状。
     *
     * @return 长度 4 的得分数组，非法方向为 {@link Double#NEGATIVE_INFINITY}
     */
    public static double[] scoreAllDirections(Grid[][] grids, MoveEvaluator eval) {
        int[][] savedVals = Utils.saveValues(grids);
        boolean[][] savedMerges = Utils.saveMerges(grids);

        double[] scores = new double[4];
        Arrays.fill(scores, Double.NEGATIVE_INFINITY);

        for (int dir = 0; dir < 4; dir++) {
            int immediate = Utils.simulateMove(grids, dir);
            if (immediate >= 0) {
                scores[dir] = eval.evaluate(grids, dir, immediate);
            }
            Utils.restoreState(grids, savedVals, savedMerges);
        }
        return scores;
    }

    // ================================================================
    //  环境随机 spawn — 与 ExperimentRunner.spawnTile 完全一致
    // ================================================================

    /**
     * 在棋盘的随机空格生成 2（90%）或 4（10%）。<br>
     * 与 ExperimentRunner.spawnTile 行为完全一致。
     *
     * @return 是否成功生成（满棋盘返回 false）
     */
    public static boolean spawnRandom(Grid[][] grids) {
        int[] empties = new int[16];
        int count = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (grids[r][c].isEmpty())
                    empties[count++] = (r << 2) | c;
        if (count == 0) return false;
        int idx = empties[RNG.nextInt(count)];
        grids[idx >> 2][idx & 3].value = RNG.nextDouble() < 0.9 ? 2 : 4;
        return true;
    }

    // ================================================================
    //  低开销 deepCopy（各算法 rollout 起点用）
    // ================================================================

    /**
     * 在已有的 dst 上原地拷贝 src 的 value，并清空 merge 标志。
     * 性能比 deepCopy 高 ~3×（不分配新 Grid 对象）。
     *
     * <p>要求 src 与 dst 都是已分配好的 4×4 Grid 数组。
     */
    public static void copyValuesInto(Grid[][] src, Grid[][] dst) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                dst[r][c].value = src[r][c].value;
                dst[r][c].setMerge(false);
            }
    }

    /** 创建一个新的 4×4 Grid 数组（每格已 new）。 */
    public static Grid[][] newBoard() {
        Grid[][] g = new Grid[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                g[r][c] = new Grid();
        return g;
    }

    // ================================================================
    //  其他辅助
    // ================================================================

    /** 找最大值的位置。返回 {row, col, value}。 */
    public static int[] findMax(Grid[][] grids) {
        int mv = 0, mr = 0, mc = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (grids[r][c].value > mv) {
                    mv = grids[r][c].value;
                    mr = r;
                    mc = c;
                }
        return new int[]{mr, mc, mv};
    }

    /** 数空格。 */
    public static int countEmpty(Grid[][] grids) {
        int n = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (grids[r][c].isEmpty()) n++;
        return n;
    }
}
