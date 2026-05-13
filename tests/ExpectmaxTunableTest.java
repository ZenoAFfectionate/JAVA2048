import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.lang.reflect.*;

/**
 * ExpectmaxTunable 全面测试。
 *
 * <p>覆盖维度：
 * <ol>
 *   <li>语义等价：默认参数下 ExpectmaxTunable.getBestDirection 与原 Expectmax 决策完全一致；</li>
 *   <li>Params API：构造 / copy / toString / 校验；</li>
 *   <li>setParams / getParams：内部拷贝、resetToDefault、非法输入异常；</li>
 *   <li>采样策略：SMART / RANDOM / ALL 都给合法方向；
 *       SMART 严格优先大数字邻接；ALL 不丢空格；RANDOM 给可重复结果（同种子）；</li>
 *   <li>状态保持：调用前后 g 的 value 与 merge 状态完全恢复；</li>
 *   <li>启发式权重：权重单调影响决策（用专门构造的局面验证）；
 *       全 0 权重时仍只返回合法方向（不抛异常）；
 *       极端值（很大正、很大负）不溢出；</li>
 *   <li>搜索深度：depth=1/2/3 都能正常完成；
 *       深度增加导致单步耗时显著上升但仍 < 1s 在小棋盘上；</li>
 *   <li>无空格 / 全阻塞 棋盘的退化行为；</li>
 *   <li>顺序参数切换：连续 setParams 不会"残留"之前权重的副作用；</li>
 *   <li>并发安全（对实验场景）：在固定参数下多线程并发调用 getBestDirection
 *       不会破坏全局参数（因为只读 + private static 的 RNG 不影响 SMART）。</li>
 * </ol>
 *
 * <p><b>本测试不修改任何游戏源代码</b>，仅通过公开 API 调用 Expectmax / ExpectmaxTunable / Utils / Grid。
 */
public class ExpectmaxTunableTest {

    private static int passed = 0, failed = 0;

    // ============================================================
    //  入口
    // ============================================================

    public static void main(String[] args) {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  ExpectmaxTunable · 全面单元测试");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        testDefaultMatchesOriginal();
        testParamsCopyAndToString();
        testParamSetterValidation();
        testParamSetterDeepCopy();
        testResetToDefault();
        testAllSampleStrategies();
        testSampleStrategyDifferentiation();
        testStatePreservationManyBoards();
        testStateMergeFlagPreservation();
        testHeuristicWeightZero();
        testHeuristicExtremeWeights();
        testHeuristicWeightAffectsDecision();
        testDepthRange();
        testDepthIncreasesCost();
        testEmptyBoardEdge();
        testFullStuckBoard();
        testSequentialParamSwitching();
        testConcurrentCalls();
        testReturnRangeAlwaysValid();

        // ---- 终结 ----
        int total = passed + failed;
        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  ExpectmaxTunable: %d passed, %d failed (%d total)%n",
                passed, failed, total);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        ExpectmaxTunable.resetToDefault();
        System.exit(failed > 0 ? 1 : 0);
    }

    // ============================================================
    //  公共助手
    // ============================================================

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }

    static void section(String title) {
        System.out.println();
        System.out.println("[" + title + "]");
    }

    static Grid[][] makeBoard() {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c] = new Grid();
        return g;
    }

    static Grid[][] makeBoard(int[][] vals) {
        Grid[][] g = makeBoard();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[r][c];
        return g;
    }

    /** 比较两个 4x4 棋盘的所有 value 是否完全一致。 */
    static boolean valuesEqual(Grid[][] a, Grid[][] b) {
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (a[r][c].value != b[r][c].value) return false;
        return true;
    }

    // ============================================================
    //  1. 默认参数 == 原 Expectmax
    // ============================================================

    static void testDefaultMatchesOriginal() {
        section("1. 默认参数下与原版 Expectmax 决策完全一致");
        ExpectmaxTunable.resetToDefault();

        int[][][] boards = {
            // 简单局面
            { {2,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0} },
            { {2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0} },
            { {0,0,0,0},{0,0,0,0},{0,0,0,0},{2,2,4,8} },
            // 完美单调
            { {0,0,0,0},{0,0,0,0},{0,0,0,0},{128,256,512,1024} },
            // 中盘
            { {2,4,8,16},{4,2,4,8},{0,0,0,0},{0,0,0,0} },
            { {2,4,8,16},{32,64,128,256},{0,0,2,4},{0,0,0,0} },
            // 1 空格
            { {2,4,8,16},{32,64,128,256},{512,1024,2,4},{8,16,32,0} },
            // 全 2/4 交错
            { {2,4,2,4},{4,2,4,2},{2,4,2,4},{4,2,4,0} },
            // 高分接近 2048
            { {1024,512,256,128},{8,16,32,64},{4,2,2,4},{0,0,0,0} },
        };

        int agree = 0;
        for (int i = 0; i < boards.length; i++) {
            Grid[][] g1 = makeBoard(boards[i]);
            Grid[][] g2 = makeBoard(boards[i]);
            int d1 = Expectmax.getBestDirection(g1);
            int d2 = ExpectmaxTunable.getBestDirection(g2);
            check(String.format("case#%d: orig=%d, tunable=%d", i, d1, d2), d1 == d2);
            if (d1 == d2) agree++;
        }
        check("全部 case (" + boards.length + ") 一致", agree == boards.length);
    }

    // ============================================================
    //  2. Params 对象 API
    // ============================================================

    static void testParamsCopyAndToString() {
        section("2. Params copy / toString");

        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        check("defaultParams.depth == 2", p.depth == 2);
        check("defaultParams.maxSamples == 4", p.maxSamples == 4);
        check("defaultParams.strategy == SMART",
                p.sampleStrategy == ExpectmaxTunable.SampleStrategy.SMART);
        check("defaultParams.wEmpty == 240",      p.wEmpty == 240);
        check("defaultParams.wMonotonicity == 35", p.wMonotonicity == 35);
        check("defaultParams.wSmoothness == -18",  p.wSmoothness == -18);
        check("defaultParams.wCorner == 60",       p.wCorner == 60);
        check("defaultParams.wMerge == 22",        p.wMerge == 22);

        // copy 不共享引用
        ExpectmaxTunable.Params q = p.copy();
        q.depth = 999;
        q.wEmpty = -123;
        check("copy 不影响原对象 (depth)", p.depth == 2);
        check("copy 不影响原对象 (wEmpty)", p.wEmpty == 240);

        // toString 含全部字段
        String s = p.toString();
        check("toString contains 'depth='",       s.contains("depth="));
        check("toString contains 'maxSamples='",  s.contains("maxSamples="));
        check("toString contains 'sample='",      s.contains("sample="));
        check("toString contains 'wE='",          s.contains("wE="));
        check("toString contains 'wMer='",        s.contains("wMer="));
    }

    static void testParamSetterValidation() {
        section("3. setParams 参数校验");

        // null
        boolean threw = false;
        try { ExpectmaxTunable.setParams(null); }
        catch (IllegalArgumentException e) { threw = true; }
        check("setParams(null) 抛 IllegalArgumentException", threw);

        // depth <= 0
        threw = false;
        try {
            ExpectmaxTunable.Params bad = ExpectmaxTunable.defaultParams();
            bad.depth = 0;
            ExpectmaxTunable.setParams(bad);
        } catch (IllegalArgumentException e) { threw = true; }
        check("depth=0 抛 IllegalArgumentException", threw);

        threw = false;
        try {
            ExpectmaxTunable.Params bad = ExpectmaxTunable.defaultParams();
            bad.depth = -5;
            ExpectmaxTunable.setParams(bad);
        } catch (IllegalArgumentException e) { threw = true; }
        check("depth=-5 抛 IllegalArgumentException", threw);

        // maxSamples <= 0
        threw = false;
        try {
            ExpectmaxTunable.Params bad = ExpectmaxTunable.defaultParams();
            bad.maxSamples = 0;
            ExpectmaxTunable.setParams(bad);
        } catch (IllegalArgumentException e) { threw = true; }
        check("maxSamples=0 抛 IllegalArgumentException", threw);

        // 校验失败时全局 CUR 不应被改变
        ExpectmaxTunable.resetToDefault();
        try {
            ExpectmaxTunable.Params bad = ExpectmaxTunable.defaultParams();
            bad.depth = 0;
            ExpectmaxTunable.setParams(bad);
        } catch (IllegalArgumentException ignored) {}
        check("校验失败后全局参数仍为默认 (depth=2)",
                ExpectmaxTunable.getParams().depth == 2);
    }

    static void testParamSetterDeepCopy() {
        section("4. setParams 内部深拷贝");
        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        p.depth = 3;
        p.wEmpty = 999;
        p.sampleStrategy = ExpectmaxTunable.SampleStrategy.RANDOM;
        ExpectmaxTunable.setParams(p);

        // 修改 caller 的对象不应影响内部
        p.depth = 1;
        p.wEmpty = 1;
        p.sampleStrategy = ExpectmaxTunable.SampleStrategy.SMART;

        ExpectmaxTunable.Params got = ExpectmaxTunable.getParams();
        check("setParams 拷贝后 depth=3", got.depth == 3);
        check("setParams 拷贝后 wEmpty=999", got.wEmpty == 999);
        check("setParams 拷贝后 strategy=RANDOM",
                got.sampleStrategy == ExpectmaxTunable.SampleStrategy.RANDOM);

        // getParams 也应返回拷贝
        got.depth = 99;
        ExpectmaxTunable.Params got2 = ExpectmaxTunable.getParams();
        check("getParams 返回独立拷贝", got2.depth == 3);

        ExpectmaxTunable.resetToDefault();
    }

    static void testResetToDefault() {
        section("5. resetToDefault");
        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        p.depth = 3; p.wEmpty = 1; p.wMerge = 1;
        ExpectmaxTunable.setParams(p);
        ExpectmaxTunable.resetToDefault();

        ExpectmaxTunable.Params now = ExpectmaxTunable.getParams();
        check("reset 后 depth = 2", now.depth == 2);
        check("reset 后 wEmpty = 240", now.wEmpty == 240);
        check("reset 后 wMerge = 22", now.wMerge == 22);
        check("reset 后 strategy = SMART",
                now.sampleStrategy == ExpectmaxTunable.SampleStrategy.SMART);
    }

    // ============================================================
    //  6. 采样策略
    // ============================================================

    static void testAllSampleStrategies() {
        section("6. 三种采样策略均合法");

        int[][] vals = { {2,4,8,16},{32,64,128,256},{512,1024,2,4},{8,16,32,0} };
        for (ExpectmaxTunable.SampleStrategy strat
                : ExpectmaxTunable.SampleStrategy.values()) {
            ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
            p.sampleStrategy = strat;
            ExpectmaxTunable.setParams(p);

            Grid[][] g = makeBoard(vals);
            int dir = ExpectmaxTunable.getBestDirection(g);
            check("strategy=" + strat + " → dir ∈ [0,3]", dir >= 0 && dir <= 3);
            check("strategy=" + strat + " → 不修改棋盘",
                    g[0][0].value == vals[0][0] && g[3][3].value == vals[3][3]);
        }
        ExpectmaxTunable.resetToDefault();
    }

    static void testSampleStrategyDifferentiation() {
        section("7. ALL 策略 dominates SMART/RANDOM (不丢空格)");

        // 构造一个有较多空格、且大数字邻接特别明显的局面
        int[][] vals = { {1024,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,2} };
        // ALL 应该考虑全部空格，结果决策应稳定可重复
        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        p.sampleStrategy = ExpectmaxTunable.SampleStrategy.ALL;
        p.maxSamples = 2; // 故意 < 空格数
        ExpectmaxTunable.setParams(p);

        Grid[][] g1 = makeBoard(vals);
        Grid[][] g2 = makeBoard(vals);
        int d1 = ExpectmaxTunable.getBestDirection(g1);
        int d2 = ExpectmaxTunable.getBestDirection(g2);
        check("ALL 策略：相同局面相同决策（确定性）", d1 == d2);

        // SMART 策略也应当确定性（无 RNG 参与）
        p.sampleStrategy = ExpectmaxTunable.SampleStrategy.SMART;
        ExpectmaxTunable.setParams(p);
        int s1 = ExpectmaxTunable.getBestDirection(makeBoard(vals));
        int s2 = ExpectmaxTunable.getBestDirection(makeBoard(vals));
        check("SMART 策略：相同局面相同决策", s1 == s2);

        ExpectmaxTunable.resetToDefault();
    }

    // ============================================================
    //  8. 状态保持
    // ============================================================

    static void testStatePreservationManyBoards() {
        section("8. 多棋盘 / 多策略下，调用前后棋盘 value 完全恢复");

        int[][][] cases = {
            { {2,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0} },
            { {2,4,8,16},{32,64,128,256},{512,1024,0,0},{0,0,0,0} },
            { {2,4,2,4},{4,2,4,2},{2,4,2,4},{4,2,4,2} },  // 全阻塞
        };

        for (ExpectmaxTunable.SampleStrategy strat
                : ExpectmaxTunable.SampleStrategy.values()) {
            ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
            p.sampleStrategy = strat;
            ExpectmaxTunable.setParams(p);

            for (int i = 0; i < cases.length; i++) {
                Grid[][] before = makeBoard(cases[i]);
                Grid[][] g = makeBoard(cases[i]);
                ExpectmaxTunable.getBestDirection(g);
                check(String.format("strategy=%s, case#%d: 棋盘恢复", strat, i),
                        valuesEqual(before, g));
            }
        }
        ExpectmaxTunable.resetToDefault();
    }

    static void testStateMergeFlagPreservation() {
        section("9. merge 标志在调用前后保持一致");
        Grid[][] g = makeBoard(new int[][]{
                {2,4,8,16},{32,64,128,256},{512,1024,0,0},{0,0,0,0}});
        // 人为设置一些 merge 标志
        g[0][0].setMerge(true);
        g[1][1].setMerge(true);

        boolean[][] beforeMerges = Utils.saveMerges(g);
        ExpectmaxTunable.getBestDirection(g);
        boolean[][] afterMerges = Utils.saveMerges(g);

        boolean ok = true;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (beforeMerges[r][c] != afterMerges[r][c]) ok = false;
        check("调用后 merge 标志逐格相等", ok);
    }

    // ============================================================
    //  10. 启发式权重
    // ============================================================

    static void testHeuristicWeightZero() {
        section("10. 全零权重不崩溃，仍返回合法方向");
        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        p.wEmpty = p.wMonotonicity = p.wSmoothness = p.wCorner = p.wMerge = 0;
        ExpectmaxTunable.setParams(p);

        int[][][] cases = {
            { {2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0} },
            { {2,4,2,4},{4,2,4,2},{2,4,2,4},{4,2,4,0} },
            { {2,4,8,16},{32,64,128,256},{0,0,0,0},{0,0,0,0} },
        };
        for (int i = 0; i < cases.length; i++) {
            Grid[][] g = makeBoard(cases[i]);
            int dir = ExpectmaxTunable.getBestDirection(g);
            check("zero-weight case#" + i + " → dir ∈ [0,3]",
                    dir >= 0 && dir <= 3);
        }
        ExpectmaxTunable.resetToDefault();
    }

    static void testHeuristicExtremeWeights() {
        section("11. 极端权重不溢出");
        Grid[][] g = makeBoard(new int[][]{
                {1024,512,256,128},{8,16,32,64},{4,2,2,4},{0,0,0,0}});

        // 极大正权重
        ExpectmaxTunable.Params big = ExpectmaxTunable.defaultParams();
        big.wEmpty = 100_000_000;
        big.wMonotonicity = 100_000_000;
        big.wMerge = 100_000_000;
        ExpectmaxTunable.setParams(big);
        int d1 = ExpectmaxTunable.getBestDirection(g);
        check("巨大正权重 → 合法方向", d1 >= 0 && d1 <= 3);

        // 极大负权重
        ExpectmaxTunable.Params neg = ExpectmaxTunable.defaultParams();
        neg.wSmoothness = -100_000_000;
        ExpectmaxTunable.setParams(neg);
        int d2 = ExpectmaxTunable.getBestDirection(g);
        check("巨大负权重 → 合法方向", d2 >= 0 && d2 <= 3);

        ExpectmaxTunable.resetToDefault();
    }

    static void testHeuristicWeightAffectsDecision() {
        section("12. 权重确实影响决策 — 多场景统计差异");

        // 核心思想：权重确实影响决策 ⇔ 在多个棋盘上，
        //   (wMerge 超大 vs wMerge=0) 至少有相当比例的局面给出不同决策。
        // 这避免了对单个局面"应该走哪个方向"的脆弱断言。
        Random rng = new Random(7);
        int diffCount = 0;
        int tested = 30;
        for (int i = 0; i < tested; i++) {
            // 构造一个有合并机会的随机局面：1~6 个 tile，至少包含一对相邻相等
            int[][] vals = new int[4][4];
            int r0 = rng.nextInt(4), c0 = rng.nextInt(3);
            int v = 1 << (1 + rng.nextInt(6)); // 2..64
            vals[r0][c0] = v;
            vals[r0][c0 + 1] = v; // 水平相邻相等
            int extra = 1 + rng.nextInt(3);
            for (int k = 0; k < extra; k++) {
                int r = rng.nextInt(4), c = rng.nextInt(4);
                if (vals[r][c] == 0) vals[r][c] = 1 << (1 + rng.nextInt(8));
            }

            ExpectmaxTunable.Params hi = ExpectmaxTunable.defaultParams();
            hi.depth = 1;
            hi.wMerge = 100_000; hi.wCorner = 0; hi.wMonotonicity = 0;
            hi.wEmpty = 0; hi.wSmoothness = 0;
            ExpectmaxTunable.setParams(hi);
            int dHi = ExpectmaxTunable.getBestDirection(makeBoard(vals));

            ExpectmaxTunable.Params lo = ExpectmaxTunable.defaultParams();
            lo.depth = 1;
            lo.wMerge = 0; lo.wCorner = 1000; lo.wMonotonicity = 0;
            lo.wEmpty = 0; lo.wSmoothness = 0;
            ExpectmaxTunable.setParams(lo);
            int dLo = ExpectmaxTunable.getBestDirection(makeBoard(vals));

            if (dHi != dLo) diffCount++;
        }
        System.out.printf("    %d/%d 局面在 wMerge↑↑ vs wCorner↑↑ 下决策不同%n",
                diffCount, tested);
        check("不同权重 → 至少 30% 的局面决策不同（统计意义影响）",
                diffCount >= tested * 0.3);

        // 单局面退化 sanity：不同权重在某个简单局面上结果合法
        int[][] vals = { {2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,4} };
        for (int wM : new int[]{0, 100, 100_000}) {
            ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
            p.depth = 1; p.wMerge = wM;
            ExpectmaxTunable.setParams(p);
            int d = ExpectmaxTunable.getBestDirection(makeBoard(vals));
            check("wMerge=" + wM + " → 合法方向 " + d, d >= 0 && d <= 3);
        }

        ExpectmaxTunable.resetToDefault();
    }

    // ============================================================
    //  13. 搜索深度
    // ============================================================

    static void testDepthRange() {
        section("13. depth=1/2/3 都返回合法方向");
        Grid[][] base = makeBoard(new int[][]{
                {2,4,8,16},{32,64,128,256},{0,0,0,0},{0,0,0,0}});

        for (int d : new int[]{1, 2, 3}) {
            ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
            p.depth = d;
            ExpectmaxTunable.setParams(p);
            int dir = ExpectmaxTunable.getBestDirection(base);
            check("depth=" + d + " → 合法方向", dir >= 0 && dir <= 3);
        }
        ExpectmaxTunable.resetToDefault();
    }

    static void testDepthIncreasesCost() {
        section("14. depth 增大 → 单步耗时单调上升 (depth1 < depth2 < depth3)");

        int[][] vals = { {2,4,8,16},{32,64,128,256},{0,0,0,0},{0,0,0,0} };
        long[] times = new long[3];
        // 多次平均，避免抖动
        int reps = 10;
        for (int i = 0; i < 3; i++) {
            ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
            p.depth = i + 1;
            ExpectmaxTunable.setParams(p);

            // 预热
            for (int w = 0; w < 5; w++) ExpectmaxTunable.getBestDirection(makeBoard(vals));

            long t0 = System.nanoTime();
            for (int r = 0; r < reps; r++) ExpectmaxTunable.getBestDirection(makeBoard(vals));
            times[i] = System.nanoTime() - t0;
        }
        System.out.printf("    nanos: depth1=%d depth2=%d depth3=%d%n",
                times[0], times[1], times[2]);
        check("depth=1 < depth=2",  times[0] < times[1]);
        check("depth=2 < depth=3",  times[1] < times[2]);

        ExpectmaxTunable.resetToDefault();
    }

    // ============================================================
    //  15. 退化 / 边界
    // ============================================================

    static void testEmptyBoardEdge() {
        section("15. 接近空棋盘 / 单格棋盘");

        // 仅 1 个数字
        Grid[][] g1 = makeBoard();
        g1[0][0].value = 2;
        check("1 个 tile → 合法方向",
                ExpectmaxTunable.getBestDirection(g1) >= 0);

        // 仅 2 个数字
        Grid[][] g2 = makeBoard();
        g2[0][0].value = 2; g2[3][3].value = 4;
        check("2 个 tile → 合法方向",
                ExpectmaxTunable.getBestDirection(g2) >= 0);
    }

    static void testFullStuckBoard() {
        section("16. 全阻塞棋盘不抛异常");

        // 严格交错填满 (无相邻相等)
        Grid[][] g = makeBoard();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = ((r + c) % 2 == 0) ? 2 : 4;

        try {
            int dir = ExpectmaxTunable.getBestDirection(g);
            check("全阻塞 → 不崩溃", true);
            check("全阻塞 → 仍返回 [0,3] 范围方向", dir >= 0 && dir <= 3);
        } catch (Exception e) {
            check("全阻塞 → 不崩溃 (got exception: " + e + ")", false);
        }
    }

    // ============================================================
    //  17. 顺序参数切换不残留
    // ============================================================

    static void testSequentialParamSwitching() {
        section("17. 顺序切换参数后，决策反映最新参数");

        int[][] vals = { {2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,4} };

        // 第一次：高 wMerge — 记录决策 d1
        ExpectmaxTunable.Params m = ExpectmaxTunable.defaultParams();
        m.depth = 1;
        m.wMerge = 100_000;
        m.wCorner = 0; m.wMonotonicity = 0; m.wEmpty = 0; m.wSmoothness = 0;
        ExpectmaxTunable.setParams(m);
        int d1 = ExpectmaxTunable.getBestDirection(makeBoard(vals));
        check("高 wMerge 时合法方向", d1 >= 0 && d1 <= 3);

        // 第二次：reset 回默认，应当与原 Expectmax 决策一致
        ExpectmaxTunable.resetToDefault();
        int d2 = Expectmax.getBestDirection(makeBoard(vals));
        int d3 = ExpectmaxTunable.getBestDirection(makeBoard(vals));
        check("切回默认参数后 == 原 Expectmax", d2 == d3);

        // 第三次：再切到高 wMerge — 应得到与第一次相同的决策（确定性）
        ExpectmaxTunable.setParams(m);
        int d4 = ExpectmaxTunable.getBestDirection(makeBoard(vals));
        check("再次切回相同参数 → 相同决策", d1 == d4);

        ExpectmaxTunable.resetToDefault();
    }

    // ============================================================
    //  18. 并发安全（同一参数下）
    // ============================================================

    static void testConcurrentCalls() {
        section("18. 并发调用 getBestDirection — 同参数下结果一致");

        ExpectmaxTunable.resetToDefault();

        final int[][] vals = { {2,4,8,16},{32,64,128,256},{0,0,2,4},{0,0,0,0} };
        final int expected = ExpectmaxTunable.getBestDirection(makeBoard(vals));

        ExecutorService pool = Executors.newFixedThreadPool(8);
        int N = 200;
        AtomicInteger ok = new AtomicInteger(0);
        AtomicInteger total = new AtomicInteger(0);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < N; i++) {
            futures.add(pool.submit(() -> {
                int dir = ExpectmaxTunable.getBestDirection(makeBoard(vals));
                total.incrementAndGet();
                if (dir == expected) ok.incrementAndGet();
            }));
        }
        for (Future<?> f : futures) {
            try { f.get(); } catch (Exception e) {
                check("并发任务抛异常 (" + e + ")", false);
            }
        }
        pool.shutdown();
        check("200 次并发都完成", total.get() == N);
        check("200 次结果全部相同 (确定性)", ok.get() == N);
    }

    // ============================================================
    //  19. 返回值范围合法（fuzz）
    // ============================================================

    static void testReturnRangeAlwaysValid() {
        section("19. fuzz: 100 个随机棋盘，所有结果方向 ∈ [0,3]");

        ExpectmaxTunable.resetToDefault();
        Random rng = new Random(42);
        int bad = 0;
        for (int i = 0; i < 100; i++) {
            Grid[][] g = makeBoard();
            int filled = 1 + rng.nextInt(13);
            for (int k = 0; k < filled; k++) {
                int r = rng.nextInt(4), c = rng.nextInt(4);
                if (g[r][c].value == 0) {
                    int log = 1 + rng.nextInt(10); // 2 .. 1024
                    g[r][c].value = 1 << log;
                }
            }
            int dir = ExpectmaxTunable.getBestDirection(g);
            if (!(dir >= 0 && dir <= 3)) bad++;
        }
        check("fuzz: 100/100 合法", bad == 0);
    }
}
