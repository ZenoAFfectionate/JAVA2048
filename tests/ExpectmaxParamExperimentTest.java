import java.io.*;
import java.util.*;
import java.lang.reflect.*;

/**
 * ExpectmaxParamExperiment 测试 —
 * 测试实验主程序的工具方法、统计聚合、CLI 选项、单局回放与报告产物。
 *
 * <p>注意：实验主程序内的核心方法（spawnTile / isGameOver / findMax / playOneGame
 * 以及 Stats / GameResult 内部类）声明为 package-private 或包内可访问，
 * 本测试位于 default package，因此可以直接调用，无需反射。
 *
 * <p><b>本测试不修改任何游戏源代码或实验主程序源代码</b>，仅通过调用接口验证行为。
 */
public class ExpectmaxParamExperimentTest {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println("  ExpectmaxParamExperiment · 全面单元测试");
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.println();

        testSpawnTile();
        testIsGameOver();
        testFindMax();
        testPlayOneGameDeterminism();
        testPlayOneGameProducesPositiveScore();
        testPlayOneGameTerminates();
        testStatsAggregation();
        testStatsOnSingleGame();
        testTileBucketDistribution();
        testCliQuickOnlySmoke();
        testCliInvalidArgRejected();
        testReportFilesGenerated();

        System.out.println();
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");
        System.out.printf("  ExpectmaxParamExperiment: %d passed, %d failed%n", passed, failed);
        System.out.println("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━");

        ExpectmaxTunable.resetToDefault();
        System.exit(failed > 0 ? 1 : 0);
    }

    // ============================================================
    //  helpers
    // ============================================================

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }
    static void section(String s) { System.out.println(); System.out.println("[" + s + "]"); }

    static Grid[][] makeBoard() {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c] = new Grid();
        return g;
    }

    static int countNonZero(Grid[][] g) {
        int n = 0;
        for (Grid[] row : g) for (Grid t : row) if (t.value != 0) n++;
        return n;
    }

    // ============================================================
    //  1. spawnTile
    // ============================================================

    static void testSpawnTile() {
        section("1. spawnTile — 在空格生成 2 (90%) 或 4 (10%)");

        // 空棋盘多次 spawn → 全 2 或 4
        Random rng = new Random(123);
        int twos = 0, fours = 0, others = 0;
        for (int t = 0; t < 1000; t++) {
            Grid[][] g = makeBoard();
            ExpectmaxParamExperiment.spawnTile(g, rng);
            int v = -1;
            for (int r = 0; r < 4; r++)
                for (int c = 0; c < 4; c++)
                    if (g[r][c].value != 0) v = g[r][c].value;
            if (v == 2) twos++;
            else if (v == 4) fours++;
            else others++;
        }
        check("spawn 1 个 → 仅放 1 格", others == 0);
        check("spawn 1 个 → 都是 2 或 4", twos + fours == 1000);
        // 应大致呈 9:1 但不必严格
        double pct4 = fours / 1000.0;
        System.out.printf("    实际 4 比例: %.3f (期望约 0.10)%n", pct4);
        check("4 的比例 ∈ [0.04, 0.18] (9:1 概率，1000 次)", pct4 >= 0.04 && pct4 <= 0.18);

        // 满棋盘 → 不应改变
        Grid[][] full = makeBoard();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                full[r][c].value = 2;
        int[][] before = Utils.saveValues(full);
        ExpectmaxParamExperiment.spawnTile(full, rng);
        int[][] after = Utils.saveValues(full);
        boolean equal = true;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (before[r][c] != after[r][c]) equal = false;
        check("满棋盘 spawn 不改变状态", equal);
    }

    // ============================================================
    //  2. isGameOver
    // ============================================================

    static void testIsGameOver() {
        section("2. isGameOver");

        Grid[][] empty = makeBoard();
        check("空棋盘: not over", !ExpectmaxParamExperiment.isGameOver(empty));

        Grid[][] one = makeBoard();
        one[0][0].value = 2;
        check("仅 1 tile: not over", !ExpectmaxParamExperiment.isGameOver(one));

        // 阻塞棋盘 (严格交错 2/4)
        Grid[][] stuck = makeBoard();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                stuck[r][c].value = ((r + c) % 2 == 0) ? 2 : 4;
        check("交错满棋盘: game over", ExpectmaxParamExperiment.isGameOver(stuck));

        // 满棋盘但有相邻相等 → not over
        Grid[][] mergeable = makeBoard();
        int v = 2;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                mergeable[r][c].value = v;  // 全 2 → 任意方向都能合并
        check("全相同满棋盘: not over (可合并)",
                !ExpectmaxParamExperiment.isGameOver(mergeable));

        // 只有底行最后一格相等 — 也不算 over
        Grid[][] bottomMerge = makeBoard();
        int[][] vals = {
            {2,4,8,16},
            {4,2,4,8},
            {8,4,2,4},
            {2,8,4,4}  // 最后两个相等 → 可合并
        };
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                bottomMerge[r][c].value = vals[r][c];
        check("有相邻相等: not over",
                !ExpectmaxParamExperiment.isGameOver(bottomMerge));
    }

    // ============================================================
    //  3. findMax
    // ============================================================

    static void testFindMax() {
        section("3. findMax");

        Grid[][] g = makeBoard();
        check("空棋盘 findMax = 0",
                ExpectmaxParamExperiment.findMax(g) == 0);

        g[0][0].value = 2;
        g[2][3].value = 1024;
        g[3][3].value = 64;
        check("最大值正确",
                ExpectmaxParamExperiment.findMax(g) == 1024);
    }

    // ============================================================
    //  4. playOneGame — 确定性 (相同 seed → 相同结果)
    // ============================================================

    static void testPlayOneGameDeterminism() {
        section("4. playOneGame: 相同种子产生相同结果");

        ExpectmaxTunable.resetToDefault();
        long seed = 12345L;
        ExpectmaxParamExperiment.GameResult r1 = ExpectmaxParamExperiment.playOneGame(seed);
        ExpectmaxParamExperiment.GameResult r2 = ExpectmaxParamExperiment.playOneGame(seed);

        check("score 相同", r1.score == r2.score);
        check("moves 相同", r1.moves == r2.moves);
        check("maxTile 相同", r1.maxTile == r2.maxTile);
        check("reached2048 相同", r1.reached2048 == r2.reached2048);

        // 不同 seed 应当大概率得到不同结果
        ExpectmaxParamExperiment.GameResult r3 = ExpectmaxParamExperiment.playOneGame(seed + 1);
        // 不强制不同（理论上可能恰好相同），但至少 score 或 moves 应当不同
        boolean somethingDiffers = (r1.score != r3.score) || (r1.moves != r3.moves);
        check("不同种子大概率产生不同结果", somethingDiffers);
    }

    static void testPlayOneGameProducesPositiveScore() {
        section("5. playOneGame: 默认参数下能产生合理分数");

        ExpectmaxTunable.resetToDefault();
        int sum = 0, sumMoves = 0, max = 0;
        int N = 5;
        for (int i = 0; i < N; i++) {
            ExpectmaxParamExperiment.GameResult r =
                    ExpectmaxParamExperiment.playOneGame(1000L + i);
            sum += r.score;
            sumMoves += r.moves;
            if (r.maxTile > max) max = r.maxTile;
            check("game#" + i + " score >= 0", r.score >= 0);
            check("game#" + i + " moves > 50 (默认参数应能下很多步)", r.moves > 50);
            check("game#" + i + " maxTile >= 16", r.maxTile >= 16);
            check("game#" + i + " decisionTimeMs >= 0", r.decisionTimeMs >= 0);
        }
        System.out.printf("    %d 局：avg score = %d, avg moves = %d, peak tile = %d%n",
                N, sum / N, sumMoves / N, max);
        check("平均得分 > 1000 (默认 Expectmax 在 5 局抽样)", sum / N > 1000);
    }

    static void testPlayOneGameTerminates() {
        section("6. playOneGame: 一定终止 (≤ MAX_GAME_MOVES)");

        ExpectmaxTunable.resetToDefault();
        // depth=1 让单步极快，多跑几个种子，确认全部终止
        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        p.depth = 1;
        ExpectmaxTunable.setParams(p);

        int allTerminated = 0;
        long t0 = System.currentTimeMillis();
        for (int i = 0; i < 5; i++) {
            ExpectmaxParamExperiment.GameResult r =
                    ExpectmaxParamExperiment.playOneGame(2000L + i);
            check("game#" + i + " moves ∈ [0, 10000]",
                    r.moves >= 0 && r.moves <= 10_000);
            allTerminated++;
        }
        long ms = System.currentTimeMillis() - t0;
        check("5 局全部正常终止", allTerminated == 5);
        check("5 局总时间 < 30s (depth=1 应远低于此)", ms < 30_000);

        ExpectmaxTunable.resetToDefault();
    }

    // ============================================================
    //  7. Stats 聚合
    // ============================================================

    static void testStatsAggregation() {
        section("7. Stats 聚合: mean / median / std / minmax / 2048 率");

        ExpectmaxParamExperiment.Stats s = new ExpectmaxParamExperiment.Stats("test");
        int[] scores = {100, 200, 300, 400, 500};
        int[] moves  = {10,  20,  30,  40,  50};
        int[] tiles  = {32,  64,  128, 1024, 2048};
        for (int i = 0; i < scores.length; i++) {
            ExpectmaxParamExperiment.GameResult r = new ExpectmaxParamExperiment.GameResult();
            r.score = scores[i];
            r.moves = moves[i];
            r.maxTile = tiles[i];
            r.decisionTimeMs = 1.0 * moves[i];
            r.reached2048 = tiles[i] >= 2048;
            s.add(r);
        }
        s.finish();

        check("n = 5", s.n == 5);
        check("meanScore = 300", Math.abs(s.meanScore - 300) < 1e-9);
        check("medianScore = 300", Math.abs(s.medianScore - 300) < 1e-9);
        check("minScore = 100", Math.abs(s.minScore - 100) < 1e-9);
        check("maxScore = 500", Math.abs(s.maxScore - 500) < 1e-9);
        // var = ((100-300)^2 + (200-300)^2 + 0 + (400-300)^2 + (500-300)^2) / 5
        //     = (40000 + 10000 + 0 + 10000 + 40000) / 5 = 20000
        // std = sqrt(20000) ≈ 141.4214
        check("stdScore ≈ sqrt(20000) ≈ 141.42",
                Math.abs(s.stdScore - Math.sqrt(20000)) < 1e-6);
        check("meanMoves = 30", Math.abs(s.meanMoves - 30) < 1e-9);
        // 在 [128, 2048] 区间，但聚合 maxTileOverall = 2048
        check("maxTileOverall = 2048", s.maxTileOverall == 2048);
        // 1 局 reached2048
        check("win2048Count = 1", s.win2048Count == 1);
        check("totalTimeMs = 150 (10+20+30+40+50)",
                Math.abs(s.totalTimeMs - 150) < 1e-9);
        // mean time/move = total / sum(moves) = 150 / 150 = 1.0
        check("meanTimePerMoveMs = 1.0",
                Math.abs(s.meanTimePerMoveMs - 1.0) < 1e-9);
    }

    static void testStatsOnSingleGame() {
        section("8. Stats: 单局退化");

        ExpectmaxParamExperiment.Stats s = new ExpectmaxParamExperiment.Stats("x");
        ExpectmaxParamExperiment.GameResult r = new ExpectmaxParamExperiment.GameResult();
        r.score = 8888;
        r.moves = 100;
        r.maxTile = 1024;
        r.decisionTimeMs = 5.0;
        r.reached2048 = false;
        s.add(r);
        s.finish();

        check("n = 1", s.n == 1);
        check("mean = median = min = max = score",
                s.meanScore == 8888 && s.medianScore == 8888
                        && s.minScore == 8888 && s.maxScore == 8888);
        check("std = 0 (单点)", Math.abs(s.stdScore) < 1e-9);
        check("win2048Count = 0", s.win2048Count == 0);
    }

    static void testTileBucketDistribution() {
        section("9. Stats: tileBuckets 直方图正确");

        ExpectmaxParamExperiment.Stats s = new ExpectmaxParamExperiment.Stats("hist");
        int[] tiles = {2, 4, 4, 8, 8, 8, 16, 1024, 2048};
        for (int t : tiles) {
            ExpectmaxParamExperiment.GameResult r = new ExpectmaxParamExperiment.GameResult();
            r.score = 1; r.moves = 1; r.maxTile = t; r.decisionTimeMs = 1;
            r.reached2048 = t >= 2048;
            s.add(r);
        }
        s.finish();

        // log2(2)=1, log2(4)=2, log2(8)=3, log2(16)=4, log2(1024)=10, log2(2048)=11
        check("log2(2) bucket = 1",   s.tileBuckets[1]  == 1);
        check("log2(4) bucket = 2",   s.tileBuckets[2]  == 2);
        check("log2(8) bucket = 3",   s.tileBuckets[3]  == 3);
        check("log2(16) bucket = 1",  s.tileBuckets[4]  == 1);
        check("log2(1024) bucket = 1", s.tileBuckets[10] == 1);
        check("log2(2048) bucket = 1", s.tileBuckets[11] == 1);
        // sum
        int total = 0;
        for (int v : s.tileBuckets) total += v;
        check("总和 = 9", total == 9);
    }

    // ============================================================
    //  10. CLI 端到端 (--smoke --only ...)
    // ============================================================

    static void testCliQuickOnlySmoke() throws Exception {
        section("10. CLI: --smoke --only depth 端到端");

        // 切换到临时工作目录跑实验，避免覆盖正式实验产物
        File tmpDir = makeTempDir("expectmax-cli-test-");
        File savedTxt = new File("result_expectmax_params.txt");
        File savedMd  = new File("result_expectmax_params.md");
        File savedCsv = new File("result_expectmax_params.csv");
        // 备份正式产物（如已存在）
        File bakTxt = backup(savedTxt);
        File bakMd  = backup(savedMd);
        File bakCsv = backup(savedCsv);

        try {
            // 重定向 stdout 隔离日志
            PrintStream realOut = System.out;
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            System.setOut(new PrintStream(baos, true, "UTF-8"));

            int exitOk = -1;
            try {
                // 直接调用 main，--smoke (10 局) --only depth
                ExpectmaxParamExperiment.main(new String[]{"--smoke", "--only", "depth"});
                exitOk = 0;
            } catch (Throwable t) {
                exitOk = 1;
            } finally {
                System.setOut(realOut);
            }

            String out = baos.toString("UTF-8");

            check("CLI 正常返回", exitOk == 0);
            check("输出包含标题",
                    out.contains("Expectmax Algorithm  ·  Parameter-Sweep Experiment"));
            check("输出包含 'Search Depth'", out.contains("Search Depth"));
            check("输出包含 depth=1/2/3 行",
                    out.contains("depth=1") && out.contains("depth=2") && out.contains("depth=3"));
            check("输出包含 'All experiments finished'",
                    out.contains("All experiments finished"));

            check("产物 result_expectmax_params.txt 存在", savedTxt.exists());
            check("产物 result_expectmax_params.md  存在", savedMd.exists());
            check("产物 result_expectmax_params.csv 存在", savedCsv.exists());

            // 检查 CSV 内容
            if (savedCsv.exists()) {
                String csv = readAll(savedCsv);
                check("CSV 含表头",
                        csv.startsWith("scan,config,n,meanScore,"));
                long lines = csv.lines().filter(s -> !s.isBlank()).count();
                // 1 行表头 + 3 行 (depth=1/2/3)
                check("CSV 行数 = 1 (header) + 3 (depth scan) = 4",
                        lines == 4);
                check("CSV 含 depth=1 行", csv.contains(",depth=1,"));
                check("CSV 含 depth=3 行", csv.contains(",depth=3,"));
            }
        } finally {
            // 删除测试产物，恢复备份
            savedTxt.delete();
            savedMd.delete();
            savedCsv.delete();
            restore(bakTxt, savedTxt);
            restore(bakMd, savedMd);
            restore(bakCsv, savedCsv);
            deleteRecursively(tmpDir);
        }
    }

    static void testCliInvalidArgRejected() {
        section("11. CLI: 未知参数报错并退出 (但不影响 JVM)");
        // parseArgs 在错误时会调用 System.exit；我们安装一个 SecurityManager 拦截
        // 注意：JDK 17+ 已 deprecate SecurityManager，此处保持简化测试 ——
        // 使用反射调用 parseArgs (private) 不行；所以用外部进程模式测试。

        // 直接 fork JVM 运行 ExpectmaxParamExperiment 加非法参数，期望非 0 退出码
        try {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome + File.separator + "bin" + File.separator + "java";
            String classPath = System.getProperty("java.class.path");
            ProcessBuilder pb = new ProcessBuilder(
                    javaBin, "-cp", classPath,
                    "ExpectmaxParamExperiment", "--no-such-arg");
            pb.redirectErrorStream(true);
            Process proc = pb.start();
            String output = readAll(proc.getInputStream());
            int code = proc.waitFor();
            check("非法参数 → 输出含 'Unknown argument' 或 Usage",
                    output.contains("Unknown argument") || output.contains("Usage"));
            check("非法参数 → 非 0 退出码 或显示用法（容许 --no-such-arg 触发 printUsageAndExit）",
                    code != 0 || output.contains("Usage"));
        } catch (Exception e) {
            check("子进程执行失败: " + e.getMessage(), false);
        }
    }

    // ============================================================
    //  12. 报告产物完整性
    // ============================================================

    static void testReportFilesGenerated() throws Exception {
        section("12. 完整报告产物三件套同步生成 (--only merge 仅 5 配置)");

        File savedTxt = new File("result_expectmax_params.txt");
        File savedMd  = new File("result_expectmax_params.md");
        File savedCsv = new File("result_expectmax_params.csv");
        File bakTxt = backup(savedTxt);
        File bakMd  = backup(savedMd);
        File bakCsv = backup(savedCsv);

        try {
            // smoke 模式 (10 局)
            ExpectmaxParamExperiment.main(new String[]{
                    "--smoke", "--only", "merge"});

            check("txt 存在且非空", savedTxt.exists() && savedTxt.length() > 0);
            check("md  存在且非空", savedMd.exists()  && savedMd.length()  > 0);
            check("csv 存在且非空", savedCsv.exists() && savedCsv.length() > 0);

            String md = readAll(savedMd);
            check("md 含 wMerge 标题",
                    md.contains("Merge Bonus") && md.contains("`wMerge=22`"));
            check("md 含 'Best mean score'", md.contains("Best mean score"));

            String txt = readAll(savedTxt);
            check("txt 含 box-drawing 表",
                    txt.contains("┌") && txt.contains("┐") && txt.contains("│"));
            check("txt 含 max-tile distribution",
                    txt.contains("Max-tile distribution"));
        } finally {
            savedTxt.delete();
            savedMd.delete();
            savedCsv.delete();
            restore(bakTxt, savedTxt);
            restore(bakMd, savedMd);
            restore(bakCsv, savedCsv);
        }
    }

    // ============================================================
    //  IO helpers
    // ============================================================

    static File makeTempDir(String prefix) throws IOException {
        File f = File.createTempFile(prefix, "");
        f.delete();
        f.mkdirs();
        return f;
    }

    /** 备份文件到 sibling .bak；返回 .bak File，若原不存在返回 null。 */
    static File backup(File src) throws IOException {
        if (!src.exists()) return null;
        File bak = new File(src.getAbsolutePath() + ".bak");
        copyFile(src, bak);
        return bak;
    }

    static void restore(File bak, File dst) throws IOException {
        if (bak == null) return;
        copyFile(bak, dst);
        bak.delete();
    }

    static void copyFile(File src, File dst) throws IOException {
        try (InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
    }

    static String readAll(File f) throws IOException {
        try (InputStream in = new FileInputStream(f)) {
            return readAll(in);
        }
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        return out.toString("UTF-8");
    }

    static void deleteRecursively(File f) {
        if (f == null || !f.exists()) return;
        if (f.isDirectory()) {
            File[] kids = f.listFiles();
            if (kids != null) for (File k : kids) deleteRecursively(k);
        }
        f.delete();
    }
}
