import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Expectmax 参数探索实验。
 *
 * <p>对 {@link ExpectmaxTunable} 的每一个可调参数做"控制变量"扫描：
 * 在保持其它参数为默认值的前提下，仅改变一个参数，每个配置默认运行
 * <b>1000 局</b>独立游戏，记录均值/中位数/标准差/2048 达成率/最大 tile/最高分/平均决策耗时
 * 等多维度指标。
 *
 * <h3>扫描的参数</h3>
 * <ol>
 *   <li><b>DEPTH</b> 搜索深度：{1, 2, 3}（depth=4 默认跳过，可以通过命令行 --include-depth-4 启用）</li>
 *   <li><b>MAX_SAMPLES</b> 每层 chance 节点采样空格数上限：{1, 2, 4, 6, 8, 16}</li>
 *   <li><b>采样策略</b>：SMART / RANDOM / ALL</li>
 *   <li><b>W_EMPTY</b> 空格权重：{60, 120, 240, 480, 960}</li>
 *   <li><b>W_MONOTONICITY</b> 单调性权重：{0, 17, 35, 70, 140}</li>
 *   <li><b>W_SMOOTHNESS</b> 平滑度权重（绝对值递增，符号保持负）：{0, -9, -18, -36, -72}</li>
 *   <li><b>W_CORNER</b> 角落奖励权重：{0, 30, 60, 120, 240}</li>
 *   <li><b>W_MERGE</b> 合并潜力权重：{0, 11, 22, 44, 88}</li>
 * </ol>
 *
 * <h3>输出</h3>
 * 实验结果同时输出到：
 * <ul>
 *   <li>终端（实时进度）</li>
 *   <li>{@code result_expectmax_params.txt}（人类友好的盒框格式）</li>
 *   <li>{@code result_expectmax_params.md}（Markdown 表格，可直接渲染）</li>
 *   <li>{@code result_expectmax_params.csv}（机读格式，便于后续画图）</li>
 * </ul>
 *
 * <h3>命令行参数</h3>
 * <pre>
 *   java -cp out ExpectmaxParamExperiment [选项]
 *
 *   --games N               每个配置运行的局数（默认 1000）
 *   --include-depth-4       同时扫描 depth=4（开销极大，默认关闭）
 *   --quick                 快速模式：每配置 100 局（用于调试）
 *   --only NAME             仅运行指定参数扫描，NAME ∈
 *                           {depth, samples, strategy, empty, mono, smooth, corner, merge}
 *   --threads N             并行 worker 数（默认 = CPU 核心数）
 * </pre>
 *
 * <p>注意：每个 <i>配置</i> 内部使用并行多线程运行 N 局（每局独立棋盘），
 * 但同一时刻只扫描一个配置；这样既保证速度，又保证统计独立。
 *
 * <h3>可重复性</h3>
 * 每次实验内为每一局分配确定性的 RNG 种子（{@code seed = configIndex*100003 + gameIndex}），
 * 因此结果可重复。比较不同参数时同一 game index 的种子相同，但棋盘演化由算法决策驱动，
 * 不构成系统性偏差。
 */
public class ExpectmaxParamExperiment {

    // ============================================================
    //  常量
    // ============================================================

    private static final int ROWS = 4, COLS = 4;
    private static final int MAX_GAME_MOVES = 10_000;

    // ============================================================
    //  实验参数（可被命令行覆盖）
    // ============================================================

    private static int    GAMES_PER_CONFIG  = 1000;
    private static boolean INCLUDE_DEPTH_4  = false;
    private static String  ONLY_SCAN        = null;     // null = 全部
    private static int     THREADS          = Math.max(1, Runtime.getRuntime().availableProcessors());

    // ============================================================
    //  输出 sink — 同步写入终端 + 三种文件
    // ============================================================

    private static PrintWriter pwTxt;
    private static PrintWriter pwMd;
    private static PrintWriter pwCsv;

    // ============================================================
    //  入口
    // ============================================================

    public static void main(String[] args) throws Exception {
        parseArgs(args);

        pwTxt = openWriter("result_expectmax_params.txt");
        pwMd  = openWriter("result_expectmax_params.md");
        pwCsv = openWriter("result_expectmax_params.csv");

        writeHeader();

        long t0 = System.currentTimeMillis();

        // 每个 scan 包含一组配置，依次运行
        List<Scan> scans = buildScans();

        for (Scan s : scans) {
            if (ONLY_SCAN != null && !s.key.equalsIgnoreCase(ONLY_SCAN)) continue;
            runScan(s);
        }

        long elapsed = System.currentTimeMillis() - t0;
        line("");
        line(String.format("All experiments finished in %.1f s (%.2f min).",
                elapsed / 1000.0, elapsed / 60000.0));
        line("Reports written to:");
        line("  - result_expectmax_params.txt");
        line("  - result_expectmax_params.md");
        line("  - result_expectmax_params.csv");

        pwTxt.close();
        pwMd.close();
        pwCsv.close();
    }

    // ============================================================
    //  扫描定义
    // ============================================================

    /** 一个扫描组：一个 key、一个 label，以及一系列待评估的 Params。 */
    static class Scan {
        final String key;            // CLI key
        final String title;          // 报告标题
        final String paramLabel;     // 表头列名
        final List<Variant> variants;

        Scan(String key, String title, String paramLabel, List<Variant> variants) {
            this.key = key;
            this.title = title;
            this.paramLabel = paramLabel;
            this.variants = variants;
        }
    }

    static class Variant {
        final String label;          // 用于显示（如 "depth=2"）
        final ExpectmaxTunable.Params params;
        Variant(String label, ExpectmaxTunable.Params p) { this.label = label; this.params = p; }
    }

    /** 在默认参数基础上修改一个字段并返回 Variant。 */
    private static Variant makeVar(String label, java.util.function.Consumer<ExpectmaxTunable.Params> patch) {
        ExpectmaxTunable.Params p = ExpectmaxTunable.defaultParams();
        patch.accept(p);
        return new Variant(label, p);
    }

    private static List<Scan> buildScans() {
        List<Scan> scans = new ArrayList<>();

        // 1. depth
        {
            List<Variant> vs = new ArrayList<>();
            int[] depths = INCLUDE_DEPTH_4 ? new int[]{1, 2, 3, 4} : new int[]{1, 2, 3};
            for (int d : depths) vs.add(makeVar("depth=" + d, p -> p.depth = d));
            scans.add(new Scan("depth", "Search Depth (DEPTH)", "depth", vs));
        }
        // 2. maxSamples
        {
            List<Variant> vs = new ArrayList<>();
            for (int s : new int[]{1, 2, 4, 6, 8, 16})
                vs.add(makeVar("maxSamples=" + s, p -> p.maxSamples = s));
            scans.add(new Scan("samples", "Chance-node Sample Limit (MAX_SAMPLES)", "maxSamples", vs));
        }
        // 3. strategy
        {
            List<Variant> vs = new ArrayList<>();
            for (ExpectmaxTunable.SampleStrategy s : ExpectmaxTunable.SampleStrategy.values())
                vs.add(makeVar("strategy=" + s, p -> p.sampleStrategy = s));
            scans.add(new Scan("strategy", "Sampling Strategy", "strategy", vs));
        }
        // 4. W_EMPTY
        {
            List<Variant> vs = new ArrayList<>();
            for (int v : new int[]{60, 120, 240, 480, 960})
                vs.add(makeVar("wEmpty=" + v, p -> p.wEmpty = v));
            scans.add(new Scan("empty", "Heuristic Weight: Empty Cells (W_EMPTY)", "wEmpty", vs));
        }
        // 5. W_MONOTONICITY
        {
            List<Variant> vs = new ArrayList<>();
            for (int v : new int[]{0, 17, 35, 70, 140})
                vs.add(makeVar("wMono=" + v, p -> p.wMonotonicity = v));
            scans.add(new Scan("mono", "Heuristic Weight: Monotonicity (W_MONOTONICITY)", "wMonotonicity", vs));
        }
        // 6. W_SMOOTHNESS  (note: should be ≤ 0 by design)
        {
            List<Variant> vs = new ArrayList<>();
            for (int v : new int[]{0, -9, -18, -36, -72})
                vs.add(makeVar("wSmooth=" + v, p -> p.wSmoothness = v));
            scans.add(new Scan("smooth", "Heuristic Weight: Smoothness (W_SMOOTHNESS)", "wSmoothness", vs));
        }
        // 7. W_CORNER
        {
            List<Variant> vs = new ArrayList<>();
            for (int v : new int[]{0, 30, 60, 120, 240})
                vs.add(makeVar("wCorner=" + v, p -> p.wCorner = v));
            scans.add(new Scan("corner", "Heuristic Weight: Corner Anchor (W_CORNER)", "wCorner", vs));
        }
        // 8. W_MERGE
        {
            List<Variant> vs = new ArrayList<>();
            for (int v : new int[]{0, 11, 22, 44, 88})
                vs.add(makeVar("wMerge=" + v, p -> p.wMerge = v));
            scans.add(new Scan("merge", "Heuristic Weight: Merge Bonus (W_MERGE)", "wMerge", vs));
        }

        // 9. 组合最优 — 把每个 OFAT 扫描中胜出的参数值组合起来，验证是否真能叠加增益。
        //    同时与原默认参数和 depth=3 的"暴力深搜"基线对比。
        {
            List<Variant> vs = new ArrayList<>();

            // baseline = 原默认
            vs.add(makeVar("baseline_default", p -> {}));

            // OFAT 最优组合（depth 保持 2，维持时间预算可控）
            vs.add(makeVar("OFAT_best_depth2", p -> {
                p.depth = 2;
                p.maxSamples = 8;
                p.sampleStrategy = ExpectmaxTunable.SampleStrategy.ALL;
                p.wEmpty = 60;
                p.wMonotonicity = 140;
                p.wSmoothness = -36;
                p.wCorner = 60;
                p.wMerge = 88;
            }));

            // 同样组合，但 depth=3 — 期望进一步显著提升
            vs.add(makeVar("OFAT_best_depth3", p -> {
                p.depth = 3;
                p.maxSamples = 8;
                p.sampleStrategy = ExpectmaxTunable.SampleStrategy.ALL;
                p.wEmpty = 60;
                p.wMonotonicity = 140;
                p.wSmoothness = -36;
                p.wCorner = 60;
                p.wMerge = 88;
            }));

            // 仅 depth=3、其余保持默认（用于隔离 depth 单一贡献）
            vs.add(makeVar("only_depth3", p -> p.depth = 3));

            scans.add(new Scan("combo",
                    "Combined Best Configuration (Confirmation Run)",
                    "config", vs));
        }
        return scans;
    }

    // ============================================================
    //  扫描执行
    // ============================================================

    private static void runScan(Scan scan) throws Exception {
        // ----- 标题块 -----
        line("");
        line("════════════════════════════════════════════════════════════════════════════════");
        line("  " + scan.title);
        line("    games per config = " + GAMES_PER_CONFIG + ",  threads = " + THREADS);
        line("════════════════════════════════════════════════════════════════════════════════");

        // ----- 在每个 variant 上跑实验 -----
        List<Stats> results = new ArrayList<>();
        for (int idx = 0; idx < scan.variants.size(); idx++) {
            Variant v = scan.variants.get(idx);
            ExpectmaxTunable.setParams(v.params);

            long t0 = System.currentTimeMillis();
            print(String.format("  [%d/%d] %-32s ... ", idx + 1, scan.variants.size(), v.label));

            Stats s = runConfig(v.label, idx);
            results.add(s);

            long ms = System.currentTimeMillis() - t0;
            line(String.format("done   mean=%,8.0f  med=%,8.0f  std=%,8.0f  2048=%5.1f%%  maxTile=%5d  (%.1fs)",
                    s.meanScore, s.medianScore, s.stdScore,
                    100.0 * s.win2048Count / s.n, s.maxTileOverall,
                    ms / 1000.0));
            flushAll();
        }

        // ----- 输出表格（终端 + txt + md + csv） -----
        printScanReport(scan, results);
        flushAll();
    }

    private static Stats runConfig(String label, int configIdx) throws Exception {
        Stats stats = new Stats(label);
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        List<Future<GameResult>> futures = new ArrayList<>(GAMES_PER_CONFIG);
        for (int g = 0; g < GAMES_PER_CONFIG; g++) {
            final long seed = (long) configIdx * 100_003L + g;
            futures.add(pool.submit(() -> playOneGame(seed)));
        }
        for (Future<GameResult> f : futures) stats.add(f.get());
        pool.shutdown();
        stats.finish();
        return stats;
    }

    // ============================================================
    //  单局游戏（线程安全：每个线程独立棋盘 + 独立 RNG）
    // ============================================================

    static class GameResult {
        int score, moves, maxTile;
        double decisionTimeMs;
        boolean reached2048;
    }

    /**
     * <b>注意</b>：{@link ExpectmaxTunable#getBestDirection} 内部读取全局 CUR 参数；
     * 我们的实验执行顺序是「设置参数 → 启动这一组 N 局并行 → 等待完成 → 设置下一组」，
     * 因此并行子任务期间参数稳定不变，符合假设。
     */
    static GameResult playOneGame(long seed) {
        Random rng = new Random(seed);
        Grid[][] grids = new Grid[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                grids[r][c] = new Grid();

        spawnTile(grids, rng);
        spawnTile(grids, rng);

        int score = 0, moves = 0;
        long totalNs = 0;

        while (moves < MAX_GAME_MOVES) {
            if (isGameOver(grids)) break;

            long ns0 = System.nanoTime();
            int dir = ExpectmaxTunable.getBestDirection(grids);
            long ns1 = System.nanoTime();
            totalNs += (ns1 - ns0);

            int gain = Utils.simulateMove(grids, dir);
            if (gain < 0) break;

            score += gain;
            moves++;
            spawnTile(grids, rng);
        }

        GameResult r = new GameResult();
        r.score = score;
        r.moves = moves;
        r.maxTile = findMax(grids);
        r.decisionTimeMs = totalNs / 1_000_000.0;
        r.reached2048 = r.maxTile >= 2048;
        return r;
    }

    static void spawnTile(Grid[][] g, Random rng) {
        int[] empties = new int[16];
        int count = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (g[r][c].isEmpty())
                    empties[count++] = (r << 2) | c;
        if (count == 0) return;
        int idx = empties[rng.nextInt(count)];
        g[idx >> 2][idx & 3].value = rng.nextDouble() < 0.9 ? 2 : 4;
    }

    static boolean isGameOver(Grid[][] g) {
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++) {
                if (g[r][c].isEmpty()) return false;
                int v = g[r][c].value;
                if (c + 1 < COLS && g[r][c + 1].value == v) return false;
                if (r + 1 < ROWS && g[r + 1][c].value == v) return false;
            }
        return true;
    }

    static int findMax(Grid[][] g) {
        int mx = 0;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.value > mx) mx = t.value;
        return mx;
    }

    // ============================================================
    //  统计聚合
    // ============================================================

    static class Stats {
        final String label;
        int n;
        List<Integer> scores = new ArrayList<>();
        List<Integer> moveCounts = new ArrayList<>();
        List<Integer> maxTiles = new ArrayList<>();
        double totalTimeMs;
        int win2048Count;
        int maxTileOverall;

        double meanScore, medianScore, stdScore, minScore, maxScore;
        double meanMoves;
        double meanTimePerMoveMs;
        double tileDistAvgMaxTile;

        // tile 分布：log2 -> count
        int[] tileBuckets = new int[16];

        Stats(String label) { this.label = label; }

        synchronized void add(GameResult r) {
            scores.add(r.score);
            moveCounts.add(r.moves);
            maxTiles.add(r.maxTile);
            totalTimeMs += r.decisionTimeMs;
            if (r.reached2048) win2048Count++;
            if (r.maxTile > maxTileOverall) maxTileOverall = r.maxTile;
            if (r.maxTile > 0) {
                int log = 31 - Integer.numberOfLeadingZeros(r.maxTile);
                if (log >= 0 && log < tileBuckets.length) tileBuckets[log]++;
            }
        }

        void finish() {
            n = scores.size();
            if (n == 0) return;
            List<Integer> sorted = new ArrayList<>(scores);
            Collections.sort(sorted);
            minScore = sorted.get(0);
            maxScore = sorted.get(n - 1);
            medianScore = (n % 2 == 0)
                    ? (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0
                    : sorted.get(n / 2);

            double sumScore = 0, sumMoves = 0, sumTile = 0;
            for (int i = 0; i < n; i++) {
                sumScore += scores.get(i);
                sumMoves += moveCounts.get(i);
                sumTile  += maxTiles.get(i);
            }
            meanScore = sumScore / n;
            meanMoves = sumMoves / n;
            tileDistAvgMaxTile = sumTile / n;
            meanTimePerMoveMs = sumMoves > 0 ? totalTimeMs / sumMoves : 0;

            double var = 0;
            for (int s : scores) var += (s - meanScore) * (s - meanScore);
            stdScore = Math.sqrt(var / n);
        }
    }

    // ============================================================
    //  报表输出
    // ============================================================

    private static void printScanReport(Scan scan, List<Stats> results) {
        line("");
        // ---- 终端 / TXT 表 ----
        line("┌──────────────────────────┬─────────────┬─────────────┬─────────────┬─────────────┬───────────┬──────────┬──────────────────┐");
        line("│ Configuration            │ Mean Score  │  Median     │  Std-dev    │ Max Score   │ 2048 Rate │ Max Tile │ Mean Time/Move ms│");
        line("├──────────────────────────┼─────────────┼─────────────┼─────────────┼─────────────┼───────────┼──────────┼──────────────────┤");
        for (Stats s : results) {
            line(String.format("│ %-24s │ %11s │ %11s │ %11s │ %11s │ %8s%% │ %8d │ %16s │",
                    truncate(s.label, 24),
                    fmt(s.meanScore), fmt(s.medianScore), fmt(s.stdScore), fmt(s.maxScore),
                    fmt1(100.0 * s.win2048Count / s.n),
                    s.maxTileOverall,
                    fmt3(s.meanTimePerMoveMs)));
        }
        line("└──────────────────────────┴─────────────┴─────────────┴─────────────┴─────────────┴───────────┴──────────┴──────────────────┘");

        // ---- Tile 分布表 ----
        // 找出本组中出现过的所有 tile
        Set<Integer> seen = new TreeSet<>();
        for (Stats s : results)
            for (int log = 0; log < s.tileBuckets.length; log++)
                if (s.tileBuckets[log] > 0) seen.add(log);
        if (!seen.isEmpty()) {
            line("");
            line("Max-tile distribution (count of games):");
            StringBuilder hdr = new StringBuilder();
            hdr.append(String.format("  %-26s", "Configuration"));
            for (int log : seen) hdr.append(String.format(" %7d", 1 << log));
            line(hdr.toString());
            for (Stats s : results) {
                StringBuilder row = new StringBuilder();
                row.append(String.format("  %-26s", truncate(s.label, 26)));
                for (int log : seen) row.append(String.format(" %7d", s.tileBuckets[log]));
                line(row.toString());
            }
        }

        // ---- Markdown 表（写入 .md） ----
        pwMd.println();
        pwMd.println("## " + scan.title);
        pwMd.println();
        pwMd.println("| Configuration | Mean Score | Median | Std-dev | Max Score | 2048 Rate | Max Tile | Mean Time/Move (ms) |");
        pwMd.println("|---|---:|---:|---:|---:|---:|---:|---:|");
        for (Stats s : results) {
            pwMd.printf("| `%s` | %s | %s | %s | %s | %s%% | %d | %s |%n",
                    s.label,
                    fmt(s.meanScore), fmt(s.medianScore), fmt(s.stdScore), fmt(s.maxScore),
                    fmt1(100.0 * s.win2048Count / s.n),
                    s.maxTileOverall,
                    fmt3(s.meanTimePerMoveMs));
        }
        // best 行
        Stats best = results.stream()
                .max(Comparator.comparingDouble(x -> x.meanScore))
                .orElseThrow(NoSuchElementException::new);
        pwMd.printf("%n**Best mean score**: `%s` → **%s**%n", best.label, fmt(best.meanScore));

        // ---- CSV 行（写入 .csv） ----
        for (Stats s : results) {
            pwCsv.printf("%s,%s,%d,%.2f,%.2f,%.2f,%.2f,%.2f,%.4f,%d,%d%n",
                    scan.key, s.label, s.n,
                    s.meanScore, s.medianScore, s.stdScore, s.minScore, s.maxScore,
                    100.0 * s.win2048Count / s.n,
                    s.maxTileOverall,
                    Math.round(s.meanTimePerMoveMs * 1000)); // µs/move
        }

        // ---- 结论 ----
        line("");
        line(String.format("→ Best configuration of this scan: %s  (mean = %s)",
                best.label, fmt(best.meanScore)));
    }

    // ============================================================
    //  报头
    // ============================================================

    private static void writeHeader() {
        String now = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date());
        line("╔════════════════════════════════════════════════════════════════════════════════╗");
        line("║         2048  ·  Expectmax Algorithm  ·  Parameter-Sweep Experiment            ║");
        line("╚════════════════════════════════════════════════════════════════════════════════╝");
        line("");
        line("  Generated:        " + now);
        line("  Games per config: " + GAMES_PER_CONFIG);
        line("  Threads:          " + THREADS);
        line("  Include depth=4:  " + INCLUDE_DEPTH_4);
        line("  Only scan:        " + (ONLY_SCAN == null ? "(all)" : ONLY_SCAN));
        line("");
        line("  Methodology");
        line("  ─────────────────────────────────────────────────────────────────────────────");
        line("  - One-factor-at-a-time (OFAT) sweep on top of the default configuration.");
        line("  - Default config = original Expectmax + Utils.heuristic weights:");
        line("        depth=2, maxSamples=4, strategy=SMART,");
        line("        wEmpty=240, wMonotonicity=35, wSmoothness=-18, wCorner=60, wMerge=22");
        line("  - Each game uses an independent deterministic seed; scoring depends only on");
        line("    the algorithm's choice plus environment randomness, ensuring reproducibility.");
        line("  - All games share the same 4×4 board, simulateMove logic and termination rule.");
        line("");

        // Markdown 头
        pwMd.println("# 2048 · Expectmax · Parameter-Sweep Experiment");
        pwMd.println();
        pwMd.println("- **Generated**: " + now);
        pwMd.println("- **Games per config**: " + GAMES_PER_CONFIG);
        pwMd.println("- **Threads**: " + THREADS);
        pwMd.println("- **Include depth=4**: " + INCLUDE_DEPTH_4);
        pwMd.println("- **Only scan**: " + (ONLY_SCAN == null ? "(all)" : ONLY_SCAN));
        pwMd.println();
        pwMd.println("## Methodology");
        pwMd.println();
        pwMd.println("One-factor-at-a-time (OFAT) parameter sweep on top of the default");
        pwMd.println("Expectmax configuration:");
        pwMd.println();
        pwMd.println("```");
        pwMd.println("depth=2, maxSamples=4, strategy=SMART,");
        pwMd.println("wEmpty=240, wMonotonicity=35, wSmoothness=-18, wCorner=60, wMerge=22");
        pwMd.println("```");
        pwMd.println();
        pwMd.println("Each individual game uses a deterministic per-game seed for reproducibility.");

        // CSV 头
        pwCsv.println("scan,config,n,meanScore,medianScore,stdScore,minScore,maxScore,winRate2048Pct,maxTile,timePerMoveUs");
    }

    // ============================================================
    //  CLI 解析
    // ============================================================

    private static void parseArgs(String[] args) {
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            switch (a) {
                case "--games":
                    GAMES_PER_CONFIG = Integer.parseInt(args[++i]); break;
                case "--include-depth-4":
                    INCLUDE_DEPTH_4 = true; break;
                case "--quick":
                    GAMES_PER_CONFIG = 100; break;
                case "--smoke":
                    GAMES_PER_CONFIG = 10; break;
                case "--only":
                    ONLY_SCAN = args[++i]; break;
                case "--threads":
                    THREADS = Integer.parseInt(args[++i]); break;
                case "-h": case "--help":
                    printUsageAndExit(); break;
                default:
                    System.err.println("Unknown argument: " + a);
                    printUsageAndExit();
            }
        }
    }

    private static void printUsageAndExit() {
        System.out.println(
            "Usage: java -cp out ExpectmaxParamExperiment [options]\n" +
            "  --games N            Games per configuration (default 1000)\n" +
            "  --quick              Shortcut for --games 100\n" +
            "  --smoke              Shortcut for --games 10 (sanity check)\n" +
            "  --include-depth-4    Also scan depth=4 (very slow)\n" +
            "  --only NAME          Run only one scan: depth|samples|strategy|empty|mono|smooth|corner|merge\n" +
            "  --threads N          Worker threads (default = CPU cores)\n");
        System.exit(0);
    }

    // ============================================================
    //  IO 辅助
    // ============================================================

    private static PrintWriter openWriter(String path) throws IOException {
        return new PrintWriter(new OutputStreamWriter(
                new FileOutputStream(path), "UTF-8"), false);
    }

    private static void line(String s) {
        System.out.println(s);
        pwTxt.println(s);
    }
    private static void print(String s) {
        System.out.print(s);
        pwTxt.print(s);
    }
    private static void flushAll() {
        System.out.flush();
        pwTxt.flush();
        pwMd.flush();
        pwCsv.flush();
    }

    // ============================================================
    //  格式化
    // ============================================================

    private static String fmt(double v)  { return String.format("%,.0f", v); }
    private static String fmt1(double v) { return String.format("%,.1f", v); }
    private static String fmt3(double v) { return String.format("%,.3f", v); }
    private static String truncate(String s, int n) {
        return s.length() <= n ? s : s.substring(0, n - 1) + "…";
    }
}
