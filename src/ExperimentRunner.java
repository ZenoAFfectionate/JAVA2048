import java.util.*;
import java.io.*;

/**
 * 2048 算法对比实验 — 对四种 AI 算法进行 10/100/1000/10000 局游戏测试，
 * 记录并对比平均得分、达成2048次数、用时、步数等多维度指标。
 * 结果输出到项目根目录的 result.txt 文件中。
 *
 * 编译: javac -encoding UTF-8 -d out src/*.java algo/*.java
 * 运行: java -cp out ExperimentRunner
 */
public class ExperimentRunner {

    private static final int ROWS = 4, COLS = 4;
    private static final int MAX_GAME_MOVES = 10000;  // 安全上限
    private static final Random RNG = new Random();
    private static PrintWriter out;  // 同时写入文件和终端

    // 实验规模
    private static final int[] GAME_COUNTS = {10, 100, 1000, 10000};

    // 算法注册
    interface Algo {
        String name();
        int getBestDirection(Grid[][] grids);
    }

    private static final Algo[] ALGOS = {
        new Algo() {
            public String name() { return "FixHeuristic"; }
            public int getBestDirection(Grid[][] g) { return FixHeuristic.getBestDirection(g); }
        },
        new Algo() {
            public String name() { return "WeightedGreedy"; }
            public int getBestDirection(Grid[][] g) { return WeightedGreedy.getBestDirection(g); }
        },
        new Algo() {
            public String name() { return "Expectmax"; }
            public int getBestDirection(Grid[][] g) { return Expectmax.getBestDirection(g); }
        },
        new Algo() {
            public String name() { return "MCTS"; }
            public int getBestDirection(Grid[][] g) { return MCTS.getBestDirection(g); }
        },
    };

    // ---- 主入口 ----

    public static void main(String[] args) throws Exception {
        out = new PrintWriter(new OutputStreamWriter(
                new java.io.FileOutputStream("result.txt"), "UTF-8"), true) {
            public void write(String s) {
                super.write(s);
                System.out.print(s);  // 同步输出到终端
            }
        };

        out.println("╔══════════════════════════════════════════════════════════╗");
        out.println("║       2048 AI Algorithm Comparison Experiment            ║");
        out.println("╚══════════════════════════════════════════════════════════╝\n");
        out.flush();

        for (int gameCount : GAME_COUNTS) {
            runExperiment(gameCount);
        }

        out.println("\nExperiment complete.");
        out.close();
    }

    // ---- 单组实验 ----

    static void runExperiment(int totalGames) {
        out.println("═══════════════════════════════════════════════");
        out.println("  Experiment: " + totalGames + " games per algorithm");
        out.println("═══════════════════════════════════════════════\n");

        // 自适应 MCTS 迭代次数: 大规模实验时减少模拟次数
        if (totalGames <= 100)      MCTS.setIterations(20);
        else if (totalGames <= 1000) MCTS.setIterations(8);
        else                        MCTS.setIterations(3);
        out.println("  MCTS iterations: " + MCTS.getIterations() + "\n");

        // 每个算法的统计
        List<Stats> allStats = new ArrayList<>();

        for (Algo algo : ALGOS) {
            out.print("Running " + algo.name() + " ... ");
            long t0 = System.currentTimeMillis();

            Stats stats = new Stats(algo.name());
            for (int game = 0; game < totalGames; game++) {
                GameResult r = playOneGame(algo);
                stats.add(r);

                // 进度指示
                if (totalGames >= 1000 && (game + 1) % (totalGames / 10) == 0) {
                    out.print((game + 1) / (totalGames / 10) * 10 + "% ");
                }
            }

            stats.finish();
            allStats.add(stats);

            long elapsed = System.currentTimeMillis() - t0;
            out.printf("done (%.1fs)%n", elapsed / 1000.0);
            out.flush();
        }

        // 打印对比表
        printComparison(allStats, totalGames);
        out.flush();
    }

    // ---- 单局游戏 ----

    static GameResult playOneGame(Algo algo) {
        Grid[][] grids = new Grid[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                grids[r][c] = new Grid();

        // 初始两个 tile
        spawnTile(grids);
        spawnTile(grids);

        int score = 0, moves = 0;
        long totalDecisionNs = 0;

        while (moves < MAX_GAME_MOVES) {
            // 检查是否结束
            if (isGameOver(grids)) break;

            // AI 决策
            long ns0 = System.nanoTime();
            int dir = algo.getBestDirection(grids);
            long ns1 = System.nanoTime();
            totalDecisionNs += (ns1 - ns0);

            // 执行移动
            int gain = Utils.simulateMove(grids, dir);
            if (gain < 0) break;  // 无合法移动 (理论上不会)

            score += gain;
            moves++;

            // 生成新方块
            spawnTile(grids);
        }

        GameResult r = new GameResult();
        r.score = score;
        r.moves = moves;
        r.maxTile = findMax(grids);
        r.decisionTimeMs = totalDecisionNs / 1_000_000.0;
        r.reached2048 = r.maxTile >= 2048;
        return r;
    }

    // ---- 游戏逻辑 ----

    static void spawnTile(Grid[][] g) {
        List<int[]> empties = new ArrayList<>(16);
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (g[r][c].isEmpty())
                    empties.add(new int[]{r, c});
        if (empties.isEmpty()) return;
        int[] cell = empties.get(RNG.nextInt(empties.size()));
        g[cell[0]][cell[1]].value = RNG.nextDouble() < 0.9 ? 2 : 4;
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

    // ================================================================
    //  统计与输出
    // ================================================================

    static class GameResult {
        int score, moves, maxTile;
        double decisionTimeMs;
        boolean reached2048;
    }

    static class Stats {
        String algoName;
        List<Integer> scores = new ArrayList<>();
        List<Double> times = new ArrayList<>();
        List<Integer> moveCounts = new ArrayList<>();
        int win2048Count;
        int maxTileOverall;
        double maxScore, minScore;
        double meanScore, medianScore;
        double meanMoves;
        double meanTimePerMoveMs;
        double totalTimeMs;

        Stats(String name) { this.algoName = name; }

        void add(GameResult r) {
            scores.add(r.score);
            times.add(r.decisionTimeMs);
            moveCounts.add(r.moves);
            if (r.reached2048) win2048Count++;
            if (r.maxTile > maxTileOverall) maxTileOverall = r.maxTile;
            totalTimeMs += r.decisionTimeMs;
        }

        void finish() {
            int n = scores.size();
            Collections.sort(scores);

            minScore = scores.get(0);
            maxScore = scores.get(n - 1);
            medianScore = n % 2 == 0
                    ? (scores.get(n / 2 - 1) + scores.get(n / 2)) / 2.0
                    : scores.get(n / 2);

            double sumScore = 0, sumMoves = 0, sumTime = 0;
            for (int i = 0; i < n; i++) {
                sumScore += scores.get(i);
                sumMoves += moveCounts.get(i);
                sumTime += times.get(i);
            }
            meanScore = sumScore / n;
            meanMoves = sumMoves / n;
            meanTimePerMoveMs = meanMoves > 0 ? sumTime / sumMoves : 0;
        }
    }

    static void printComparison(List<Stats> allStats, int gameCount) {
        out.println();
        out.println("┌────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐");
        out.println("│      Metric        │ FixHeuristic │WeightedGreedy│  Expectmax   │     MCTS     │");
        out.println("├────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤");

        String[] labels = {
            "Mean Score", "Max Score", "Min Score", "Median Score",
            "2048 Rate (%)", "Mean Moves", "Max Tile",
            "Mean Time/Move(ms)", "Total Time(s)"
        };

        for (int i = 0; i < labels.length; i++) {
            String label = labels[i];
            String[] values = new String[4];
            for (int j = 0; j < 4; j++) {
                Stats s = allStats.get(j);
                switch (i) {
                    case 0: values[j] = fmt(  s.meanScore); break;
                    case 1: values[j] = fmt(  s.maxScore); break;
                    case 2: values[j] = fmt(  s.minScore); break;
                    case 3: values[j] = fmt(  s.medianScore); break;
                    case 4: values[j] = fmt1(100.0 * s.win2048Count / gameCount) + "%"; break;
                    case 5: values[j] = fmt(  s.meanMoves); break;
                    case 6: values[j] = String.valueOf(s.maxTileOverall); break;
                    case 7: values[j] = fmt3( s.meanTimePerMoveMs); break;
                    case 8: values[j] = fmt1( s.totalTimeMs / 1000.0); break;
                }
            }
            out.printf("│ %-18s │ %12s │ %12s │ %12s │ %12s │%n",
                    label, values[0], values[1], values[2], values[3]);
        }

        out.println("└────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘");
        out.println();
    }

    static String fmt(double d)  { return String.format("%,.0f", d); }
    static String fmt1(double d) { return String.format("%,.1f", d); }
    static String fmt3(double d) { return String.format("%,.3f", d); }
}
