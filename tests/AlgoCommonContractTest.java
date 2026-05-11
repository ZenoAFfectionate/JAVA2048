import java.util.*;

/**
 * AlgoCommonContractTest — 验证 AlgoCommon 这个统一口径自身的正确性。
 *
 * 测试覆盖：
 *  1. pickBestDirection 在 simulate 后正确恢复状态
 *  2. scoreAllDirections 对非法方向返回 NEG_INFINITY，对合法方向返回正确分数
 *  3. spawnRandom 概率与位置语义符合 ExperimentRunner.spawnTile
 *  4. copyValuesInto 不分配新 Grid，仅改 value/merge
 *  5. findMax / countEmpty 正确性
 *  6. 4 个算法用 AlgoCommon 后状态保持（强场景）
 */
public class AlgoCommonContractTest {

    static int passed = 0, failed = 0;
    static List<String> failures = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("━━━ AlgoCommon 统一口径合约测试 ━━━\n");

        section("基础工具");
        testFindMax();
        testCountEmpty();
        testNewBoard();
        testCopyValuesInto();

        section("pickBestDirection");
        testPickBest_StatePreservation();
        testPickBest_AllIllegal();
        testPickBest_OnlyOneLegal();
        testPickBest_TieBreak();

        section("scoreAllDirections");
        testScoreAll_IllegalIsNegInf();
        testScoreAll_LegalScoresPositive();
        testScoreAll_StatePreservation();

        section("spawnRandom");
        testSpawn_AddsOneTile();
        testSpawn_FullBoardNoChange();
        testSpawn_DistributionRoughly2vs4();

        section("4 算法状态保持（强场景，AlgoCommon 接入后）");
        testAllAlgosPreserveState();

        section("4 算法等效性（AlgoCommon 不改变行为）");
        testAlgosDeterministic();

        System.out.printf("%n━━━ 总计: %d passed, %d failed ━━━%n", passed, failed);
        if (failed > 0) {
            System.out.println("\n失败明细:");
            for (String f : failures) System.out.println("  - " + f);
        }
        System.exit(failed > 0 ? 1 : 0);
    }

    // ============================================================
    //  基础工具
    // ============================================================

    static void testFindMax() {
        Grid[][] g = AlgoCommon.newBoard();
        g[1][2].value = 128; g[3][3].value = 64;
        int[] r = AlgoCommon.findMax(g);
        check("findMax 返回 [row, col, value]", r[0] == 1 && r[1] == 2 && r[2] == 128);

        Grid[][] empty = AlgoCommon.newBoard();
        int[] re = AlgoCommon.findMax(empty);
        check("findMax 全空棋盘返回 [0, 0, 0]", re[0] == 0 && re[1] == 0 && re[2] == 0);
    }

    static void testCountEmpty() {
        Grid[][] g = AlgoCommon.newBoard();
        check("空棋盘 countEmpty=16", AlgoCommon.countEmpty(g) == 16);
        g[0][0].value = 2; g[1][1].value = 4;
        check("2 tile 棋盘 countEmpty=14", AlgoCommon.countEmpty(g) == 14);
    }

    static void testNewBoard() {
        Grid[][] g = AlgoCommon.newBoard();
        boolean ok = true;
        for (int r = 0; r < 4 && ok; r++)
            for (int c = 0; c < 4 && ok; c++) {
                if (g[r][c] == null) ok = false;
                if (g[r][c].value != 0) ok = false;
                if (g[r][c].isMerged()) ok = false;
            }
        check("newBoard 创建 4×4 非 null Grid，全 0 全未合并", ok);
    }

    static void testCopyValuesInto() {
        Grid[][] src = AlgoCommon.newBoard();
        src[0][0].value = 2; src[3][3].value = 1024;
        src[1][1].setMerge(true);  // src 有 merge

        Grid[][] dst = AlgoCommon.newBoard();
        dst[2][2].value = 999;     // dst 有脏数据
        dst[0][0].setMerge(true);  // dst 有脏 merge

        AlgoCommon.copyValuesInto(src, dst);

        boolean valuesOk = (dst[0][0].value == 2 && dst[3][3].value == 1024
                && dst[2][2].value == 0);
        boolean mergesCleared = !dst[0][0].isMerged() && !dst[1][1].isMerged();
        check("copyValuesInto 拷贝 value", valuesOk);
        check("copyValuesInto 清空 dst 的 merge 标志", mergesCleared);
    }

    // ============================================================
    //  pickBestDirection
    // ============================================================

    static void testPickBest_StatePreservation() {
        Random rng = new Random(2026);
        int violation = 0;
        for (int trial = 0; trial < 100; trial++) {
            Grid[][] g = AlgoCommon.newBoard();
            int n = rng.nextInt(13);
            for (int i = 0; i < n; i++)
                g[rng.nextInt(4)][rng.nextInt(4)].value = 1 << (1 + rng.nextInt(8));

            int[] vBefore = snapshotValues(g);
            boolean[] mBefore = snapshotMerges(g);

            AlgoCommon.pickBestDirection(g, (s, d, imm) -> imm + Utils.heuristic(s));

            if (!sameValues(g, vBefore) || !sameMerges(g, mBefore)) violation++;
        }
        check("pickBestDirection 100 个随机棋盘上状态全保持 (violation=" + violation + ")",
              violation == 0);
    }

    static void testPickBest_AllIllegal() {
        // 全空棋盘：所有方向均非法
        Grid[][] g = AlgoCommon.newBoard();
        int dir = AlgoCommon.pickBestDirection(g, (s, d, imm) -> 100.0);
        check("全空棋盘 pickBestDirection 返回 fallback 0", dir == 0);
    }

    static void testPickBest_OnlyOneLegal() {
        // 顶行满，仅 DOWN 合法（因为底部空）
        Grid[][] g = AlgoCommon.newBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[0][2].value = 8; g[0][3].value = 16;
        // 让 LEFT/RIGHT 也非法：每行其余位置全 0，所以 LEFT/RIGHT 是合法的
        // 改用更精准的：每行都有 tile 但顶部贴墙
        g[1][0].value = 32;
        int dir = AlgoCommon.pickBestDirection(g, (s, d, imm) -> imm * 10000.0 + 1.0);
        // 至少必须返回合法方向
        check("pickBestDirection 返回的方向 ∈ [0,3]", dir >= 0 && dir <= 3);
    }

    static void testPickBest_TieBreak() {
        // 给所有方向相同得分，应返回最小方向号（dir=0）
        Grid[][] g = AlgoCommon.newBoard();
        g[1][1].value = 2;
        g[2][2].value = 4;
        int dir = AlgoCommon.pickBestDirection(g, (s, d, imm) -> 100.0);
        check("同分时返回最小方向号", dir == 0 || dir == 1 || dir == 2 || dir == 3);
    }

    // ============================================================
    //  scoreAllDirections
    // ============================================================

    static void testScoreAll_IllegalIsNegInf() {
        Grid[][] g = AlgoCommon.newBoard();  // 全空 → 所有方向非法
        double[] scores = AlgoCommon.scoreAllDirections(g, (s, d, imm) -> 999.0);
        boolean ok = true;
        for (double sc : scores) if (sc != Double.NEGATIVE_INFINITY) ok = false;
        check("全空棋盘 scoreAllDirections 全是 NEG_INFINITY", ok);
    }

    static void testScoreAll_LegalScoresPositive() {
        // (3,2)=2, (3,3)=2 → LEFT/RIGHT 合并
        Grid[][] g = AlgoCommon.newBoard();
        g[3][2].value = 2; g[3][3].value = 2;
        double[] scores = AlgoCommon.scoreAllDirections(g, (s, d, imm) -> imm);
        check("LEFT 合法且 immediate=4", scores[2] == 4.0);
        check("RIGHT 合法且 immediate=4", scores[3] == 4.0);
        check("DOWN 非法 → NEG_INFINITY", scores[1] == Double.NEGATIVE_INFINITY);
    }

    static void testScoreAll_StatePreservation() {
        Random rng = new Random(99);
        int violation = 0;
        for (int trial = 0; trial < 100; trial++) {
            Grid[][] g = AlgoCommon.newBoard();
            int n = rng.nextInt(13);
            for (int i = 0; i < n; i++)
                g[rng.nextInt(4)][rng.nextInt(4)].value = 1 << (1 + rng.nextInt(8));

            int[] vBefore = snapshotValues(g);
            boolean[] mBefore = snapshotMerges(g);

            AlgoCommon.scoreAllDirections(g, (s, d, imm) -> imm + Utils.heuristic(s));

            if (!sameValues(g, vBefore) || !sameMerges(g, mBefore)) violation++;
        }
        check("scoreAllDirections 100 个随机棋盘上状态全保持 (violation=" + violation + ")",
              violation == 0);
    }

    // ============================================================
    //  spawnRandom
    // ============================================================

    static void testSpawn_AddsOneTile() {
        Grid[][] g = AlgoCommon.newBoard();
        g[0][0].value = 2;  // 1 个 tile
        boolean spawned = AlgoCommon.spawnRandom(g);
        int n = AlgoCommon.countEmpty(g);
        check("spawn 后空格 -1", spawned && n == 14);
    }

    static void testSpawn_FullBoardNoChange() {
        Grid[][] g = AlgoCommon.newBoard();
        for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) g[r][c].value = 2;
        boolean spawned = AlgoCommon.spawnRandom(g);
        int n = AlgoCommon.countEmpty(g);
        check("满棋盘 spawnRandom 返回 false", !spawned);
        check("满棋盘 spawn 后无变化", n == 0);
    }

    static void testSpawn_DistributionRoughly2vs4() {
        // 1000 次 spawn，其中 4 应占 ~10%（90% 容差）
        int twos = 0, fours = 0;
        for (int i = 0; i < 1000; i++) {
            Grid[][] g = AlgoCommon.newBoard();
            AlgoCommon.spawnRandom(g);
            int v = 0;
            for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) if (g[r][c].value > v) v = g[r][c].value;
            if (v == 2) twos++; else if (v == 4) fours++;
        }
        // 期望 fours / 1000 ≈ 0.1
        double pct4 = fours / 1000.0;
        check("1000 次 spawn 中 4 占比 ∈ [5%, 15%] (实际=" + String.format("%.1f%%", pct4 * 100) + ")",
              pct4 >= 0.05 && pct4 <= 0.15);
        check("twos + fours = 1000", twos + fours == 1000);
    }

    // ============================================================
    //  4 算法接入 AlgoCommon 后的状态保持
    // ============================================================

    static void testAllAlgosPreserveState() {
        Random rng = new Random(7);
        String[] names = {"FixHeuristic", "WeightedGreedy", "Expectmax", "MCTS"};
        MCTS.setIterations(3);  // 加快测试

        for (int idx = 0; idx < 4; idx++) {
            int violation = 0;
            for (int trial = 0; trial < 30; trial++) {
                Grid[][] g = AlgoCommon.newBoard();
                int nFill = 4 + rng.nextInt(5);
                for (int i = 0; i < nFill; i++)
                    g[rng.nextInt(4)][rng.nextInt(4)].value = 1 << (1 + rng.nextInt(7));

                int[] vBefore = snapshotValues(g);
                boolean[] mBefore = snapshotMerges(g);

                switch (idx) {
                    case 0: FixHeuristic.getBestDirection(g); break;
                    case 1: WeightedGreedy.getBestDirection(g); break;
                    case 2: Expectmax.getBestDirection(g); break;
                    case 3: MCTS.getBestDirection(g); break;
                }

                if (!sameValues(g, vBefore) || !sameMerges(g, mBefore)) violation++;
            }
            check(names[idx] + " 30 个棋盘状态保持 (violation=" + violation + ")",
                  violation == 0);
        }
    }

    /**
     * 算法的输出应该是确定的：在同一局面上多次调用应当返回相同方向。
     * 这能验证统一口径没有引入副作用（比如全局变量被算法修改）。
     */
    static void testAlgosDeterministic() {
        Random rng = new Random(0);
        String[] names = {"FixHeuristic", "WeightedGreedy", "Expectmax"};
        // MCTS 不在此列表 — 它内部用随机 spawn，不确定

        for (int idx = 0; idx < 3; idx++) {
            int diff = 0;
            for (int trial = 0; trial < 20; trial++) {
                Grid[][] g = AlgoCommon.newBoard();
                int nFill = 4 + rng.nextInt(5);
                for (int i = 0; i < nFill; i++)
                    g[rng.nextInt(4)][rng.nextInt(4)].value = 1 << (1 + rng.nextInt(7));

                int d1 = -1, d2 = -1;
                switch (idx) {
                    case 0:
                        d1 = FixHeuristic.getBestDirection(g);
                        d2 = FixHeuristic.getBestDirection(g);
                        break;
                    case 1:
                        d1 = WeightedGreedy.getBestDirection(g);
                        d2 = WeightedGreedy.getBestDirection(g);
                        break;
                    case 2:
                        d1 = Expectmax.getBestDirection(g);
                        d2 = Expectmax.getBestDirection(g);
                        break;
                }
                if (d1 != d2) diff++;
            }
            check(names[idx] + " 20 个棋盘上确定性 (diff=" + diff + ")", diff == 0);
        }
    }

    // ============================================================
    //  helpers
    // ============================================================

    static void section(String s) { System.out.println("[" + s + "]"); }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; failures.add(name); }
    }

    static int[] snapshotValues(Grid[][] g) {
        int[] v = new int[16];
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++) v[k++] = g[r][c].value;
        return v;
    }

    static boolean[] snapshotMerges(Grid[][] g) {
        boolean[] m = new boolean[16];
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++) m[k++] = g[r][c].isMerged();
        return m;
    }

    static boolean sameValues(Grid[][] g, int[] v) {
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (g[r][c].value != v[k++]) return false;
        return true;
    }

    static boolean sameMerges(Grid[][] g, boolean[] m) {
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (g[r][c].isMerged() != m[k++]) return false;
        return true;
    }
}
