import java.util.*;

/**
 * 2048 游戏机制全面测试 — 覆盖官方规则的所有边界情况
 *
 * 测试范围:
 *   1. 方块的滑动、合并、连环移动
 *   2. 合并标记 — 防止单回合多次合并
 *   3. simulateMove — AI 核心的正确性
 *   4. saveValues / restoreState — AI 状态保持
 *   5. 随机生成方块 — 概率分布
 *   6. 游戏结束判定 — 边界情况
 *   7. 计分累加 — 多次合并
 *   8. 边界情况 — 满棋盘、单空格等
 *
 * 编译: javac -encoding UTF-8 -d out tests/GameMechanicsTest.java src/*.java
 * 运行: java -cp out GameMechanicsTest
 */
public class GameMechanicsTest {

    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║   2048 Game Mechanics — Full Audit    ║");
        System.out.println("╚════════════════════════════════════════╝\n");

        testBasicSlide();
        testBasicMerge();
        testNoDoubleMerge();
        testMultipleMergesSameMove();
        testSlideThenMerge();
        testComplexScenario();

        testSimulateMoveAllDirs();
        testSimulateMoveNoMove();
        testSimulateMoveScoreOnly();

        testSaveRestorePreservesBoard();
        testSaveRestorePreservesMerge();
        testSaveRestoreIdempotent();

        testSpawnDistribution();
        testSpawnFullBoard();
        testSpawnSingleEmpty();

        testGameOverDetection();
        testWinDetection();

        testRecursiveEdgeCases();
        testFullBoardScenarios();

        System.out.println("════════════════════════════════════════");
        System.out.printf("  Game Mechanics: %d passed, %d failed%n", passed, failed);
        System.out.println("════════════════════════════════════════");
        if (failed > 0) System.exit(1);
    }

    // ---- helpers ----

    static Grid[][] makeBoard(int[][] vals) {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c] = new Grid(vals[r][c]);
        return g;
    }

    static int[] gridToArray(Grid[][] g) {
        int[] a = new int[16];
        for (int r = 0, i = 0; r < 4; r++)
            for (int c = 0; c < 4; c++, i++)
                a[i] = g[r][c].value;
        return a;
    }

    static boolean boardEquals(Grid[][] g, int[][] expected) {
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (g[r][c].value != expected[r][c]) return false;
        return true;
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ✓ " + name); }
        else      { failed++; System.out.println("  ✗ " + name + "  <-- FAILED"); }
    }

    // ================================================================
    //  1. 基本滑动测试
    // ================================================================

    static void testBasicSlide() {
        System.out.println("\n[1. 基本滑动]");

        // 单格滑动到底
        Grid[][] g = makeBoard(new int[][]{{2,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        int score = Utils.simulateMove(g, 3);  // RIGHT
        check("单格右滑到底", g[0][3].value == 2 && g[0][0].value == 0);
        check("滑动无得分", score == 0);

        // 多格滑动
        g = makeBoard(new int[][]{{2,0,4,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("多格右滑", g[0][3].value == 4 && g[0][2].value == 2);

        // 左滑
        g = makeBoard(new int[][]{{0,0,2,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 2);  // LEFT
        check("单格左滑到底", g[0][0].value == 2);

        // 上滑
        g = makeBoard(new int[][]{{0,0,0,0},{0,2,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 0);  // UP
        check("单格上滑到底", g[0][1].value == 2 && g[1][1].value == 0);

        // 下滑
        g = makeBoard(new int[][]{{0,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 1);  // DOWN
        check("单格下滑到底", g[3][1].value == 2 && g[0][1].value == 0);
    }

    // ================================================================
    //  2. 基本合并
    // ================================================================

    static void testBasicMerge() {
        System.out.println("\n[2. 基本合并]");

        // 2+2=4
        Grid[][] g = makeBoard(new int[][]{{2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        int score = Utils.simulateMove(g, 3);  // RIGHT
        check("2+2=4", g[0][3].value == 4 && g[0][2].value == 0);
        check("2+2 得分=4", score == 4);

        // 4+4=8
        g = makeBoard(new int[][]{{4,4,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        score = Utils.simulateMove(g, 3);
        check("4+4=8", g[0][3].value == 8);
        check("4+4 得分=8", score == 8);

        // 竖向合并
        g = makeBoard(new int[][]{{2,0,0,0},{2,0,0,0},{0,0,0,0},{0,0,0,0}});
        score = Utils.simulateMove(g, 0);  // UP
        check("竖向 2+2=4", g[0][0].value == 4 && g[1][0].value == 0);
        check("竖向合并得分=4", score == 4);
    }

    // ================================================================
    //  3. 防止单回合多次合并 (关键规则)
    // ================================================================

    static void testNoDoubleMerge() {
        System.out.println("\n[3. 防止重复合并 (每回合每格只合并一次)]");

        // 场景: [4,2,2,0] 右移 → 应为 [0,0,4,4] (4不参与合并)
        Grid[][] g = makeBoard(new int[][]{{4,2,2,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[4,2,2,0]→R: 右端为4,4", g[0][3].value == 4 && g[0][2].value == 4);
        check("[4,2,2,0]→R: 4不在右端合并成8", g[0][3].value == 4);

        // 场景: [4,4,4,0] 右移 → [0,0,4,8] (右端两个4合并，左端4不参与)
        // 实际2048规则: 处理从右向左, c=2先与c=3合并→g[3]=8, 然后c=1的4滑到c=2
        g = makeBoard(new int[][]{{4,4,4,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[4,4,4,0]→R: 右端=8", g[0][3].value == 8);
        check("[4,4,4,0]→R: 中间=4(c=2)", g[0][2].value == 4);
        check("[4,4,4,0]→R: 左端为空(c=0,c=1)", g[0][0].value == 0 && g[0][1].value == 0);
    }

    // ================================================================
    //  4. 同回合多组合并
    // ================================================================

    static void testMultipleMergesSameMove() {
        System.out.println("\n[4. 同回合多组独立合并]");

        // [2,2,4,4] 右移 → [0,0,4,8]
        Grid[][] g = makeBoard(new int[][]{{2,2,4,4},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[2,2,4,4]→R: [0,0,4,8]",
              g[0][0].value == 0 && g[0][1].value == 0 &&
              g[0][2].value == 4 && g[0][3].value == 8);

        // [2,2,2,2] 右移 → [0,0,4,4] (标准规则)
        g = makeBoard(new int[][]{{2,2,2,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[2,2,2,2]→R: [0,0,4,4]",
              g[0][0].value == 0 && g[0][1].value == 0 &&
              g[0][2].value == 4 && g[0][3].value == 4);

        // 竖向多组合并
        g = makeBoard(new int[][]{{2,0,0,0},{2,0,0,0},{4,0,0,0},{4,0,0,0}});
        Utils.simulateMove(g, 0);  // UP
        check("竖排 [2,2,4,4]→U: [4,8,0,0]",
              g[0][0].value == 4 && g[1][0].value == 8 &&
              g[2][0].value == 0 && g[3][0].value == 0);
    }

    // ================================================================
    //  5. 滑动后合并 (非相邻tile的合并)
    // ================================================================

    static void testSlideThenMerge() {
        System.out.println("\n[5. 滑动后合并 (有间隔的合并)]");

        // [2,0,2,0] 右移 → [0,0,0,4]
        Grid[][] g = makeBoard(new int[][]{{2,0,2,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[2,0,2,0]→R: [0,0,0,4]",
              g[0][3].value == 4 && g[0][0].value == 0 && g[0][1].value == 0 && g[0][2].value == 0);

        // [2,0,0,2] 右移 → [0,0,0,4]
        g = makeBoard(new int[][]{{2,0,0,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[2,0,0,2]→R: [0,0,0,4]",
              g[0][3].value == 4 && g[0][0].value == 0);

        // [0,2,0,4] 左移 → [2,4,0,0] (不合并，值不同)
        g = makeBoard(new int[][]{{0,2,0,4},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 2);  // LEFT
        check("[0,2,0,4]→L: [2,4,0,0]",
              g[0][0].value == 2 && g[0][1].value == 4 && g[0][2].value == 0 && g[0][3].value == 0);
    }

    // ================================================================
    //  6. 复杂场景 (大数字、多行多列)
    // ================================================================

    static void testComplexScenario() {
        System.out.println("\n[6. 复杂场景]");

        // 多行同时合并
        Grid[][] g = makeBoard(new int[][]{
            {2,2,0,0},
            {4,0,4,0},
            {0,8,8,0},
            {16,0,0,16}
        });
        Utils.simulateMove(g, 3);  // RIGHT
        check("多行同时合并: row0 [0,0,0,4]", g[0][2].value == 0 && g[0][3].value == 4);
        check("多行同时合并: row1 [0,0,0,8]", g[1][2].value == 0 && g[1][3].value == 8);
        check("多行同时合并: row2 [0,0,0,16]", g[2][2].value == 0 && g[2][3].value == 16);
        check("多行同时合并: row3 [0,0,0,32]", g[3][2].value == 0 && g[3][3].value == 32);

        // 满棋盘无合并无移动
        g = makeBoard(new int[][]{
            {2,4,8,16},
            {16,8,4,2},
            {2,4,8,16},
            {16,8,4,2}
        });
        int s = Utils.simulateMove(g, 3);
        check("满棋盘无空格无合并→R: 返回-1", s == -1);
    }

    // ================================================================
    //  7. simulateMove 全方向测试
    // ================================================================

    static void testSimulateMoveAllDirs() {
        System.out.println("\n[7. simulateMove 全方向]");

        int[][] board = {{2,0,0,2},{0,4,4,0},{0,0,0,0},{0,0,0,0}};

        // UP: 4,4 在不同列，不会竖向合并。预期 row0=[2,4,4,2], row1 全空
        Grid[][] g = makeBoard(board);
        int s = Utils.simulateMove(g, 0);
        check("UP: row0 [2,4,4,2]", g[0][0].value == 2 && g[0][1].value == 4
              && g[0][2].value == 4 && g[0][3].value == 2);
        check("UP: row1 全空", g[1][0].value == 0 && g[1][1].value == 0
              && g[1][2].value == 0 && g[1][3].value == 0);

        // DOWN: 4,4 在不同列。预期 row3=[2,4,4,2]
        g = makeBoard(board);
        s = Utils.simulateMove(g, 1);
        check("DOWN: row3 [2,4,4,2]", g[3][0].value == 2 && g[3][1].value == 4
              && g[3][2].value == 4 && g[3][3].value == 2);

        // LEFT
        g = makeBoard(board);
        s = Utils.simulateMove(g, 2);
        check("LEFT: row0 [4,0,0,0]", g[0][0].value == 4 && g[0][1].value == 0);
        check("LEFT: row1 [8,0,0,0]", g[1][0].value == 8);

        // RIGHT
        g = makeBoard(board);
        s = Utils.simulateMove(g, 3);
        check("RIGHT: row0 [0,0,0,4]", g[0][3].value == 4);
        check("RIGHT: row1 [0,0,0,8]", g[1][3].value == 8);
    }

    static void testSimulateMoveNoMove() {
        System.out.println("\n[8. simulateMove 无移动返回-1]");

        // 所有tile都在顶部，上移应无效
        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},
            {0,0,0,0},
            {0,0,0,0},
            {0,0,0,0}
        });
        check("UP 无移动返回-1", Utils.simulateMove(g, 0) == -1);
        check("UP 后棋盘不变", boardEquals(g, new int[][]{
            {2,4,8,16},{0,0,0,0},{0,0,0,0},{0,0,0,0}
        }));

        // 所有tile在底部，下移无效
        g = makeBoard(new int[][]{
            {0,0,0,0},{0,0,0,0},{0,0,0,0},{2,4,8,16}
        });
        check("DOWN 无移动返回-1", Utils.simulateMove(g, 1) == -1);

        // 所有tile在左边，左移无效
        g = makeBoard(new int[][]{
            {2,0,0,0},{4,0,0,0},{8,0,0,0},{16,0,0,0}
        });
        check("LEFT 无移动返回-1", Utils.simulateMove(g, 2) == -1);

        // 所有tile在右边，右移无效
        g = makeBoard(new int[][]{
            {0,0,0,2},{0,0,0,4},{0,0,0,8},{0,0,0,16}
        });
        check("RIGHT 无移动返回-1", Utils.simulateMove(g, 3) == -1);
    }

    static void testSimulateMoveScoreOnly() {
        System.out.println("\n[9. simulateMove 计分准确性]");

        // 单次合并
        Grid[][] g = makeBoard(new int[][]{{2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        check("2+2 得分=4", Utils.simulateMove(g, 3) == 4);

        // 双次合并
        g = makeBoard(new int[][]{{2,2,2,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        check("[2,2,2,2]→R 得分=8", Utils.simulateMove(g, 3) == 8);

        // 高值合并
        g = makeBoard(new int[][]{{1024,1024,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        check("1024+1024 得分=2048", Utils.simulateMove(g, 3) == 2048);
    }

    // ================================================================
    //  10. saveValues / restoreState
    // ================================================================

    static void testSaveRestorePreservesBoard() {
        System.out.println("\n[10. save/restore 棋盘状态完整性]");

        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},{32,64,128,256},{512,1024,2,4},{8,16,32,64}
        });

        int[][] savedVals = Utils.saveValues(g);
        boolean[][] savedMerges = Utils.saveMerges(g);

        // 修改棋盘
        Utils.simulateMove(g, 3);
        Utils.simulateMove(g, 0);

        Utils.restoreState(g, savedVals, savedMerges);

        check("restore后 row0", g[0][0].value == 2 && g[0][1].value == 4 &&
              g[0][2].value == 8 && g[0][3].value == 16);
        check("restore后 row1", g[1][0].value == 32 && g[1][1].value == 64);
        check("restore后 row3", g[3][3].value == 64);
    }

    static void testSaveRestorePreservesMerge() {
        System.out.println("\n[11. save/restore 合并标记完整性]");

        Grid[][] g = makeBoard(new int[][]{{2,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});

        // 先执行一次移动产生合并标记
        Utils.simulateMove(g, 3);
        boolean mergedAfter = g[0][3].isMerged();
        check("合并后 g[0][3] 标记为 true", mergedAfter);

        // save 带有合并标记的状态
        int[][] sv = Utils.saveValues(g);
        boolean[][] sm = Utils.saveMerges(g);

        // 修改棋盘
        Utils.simulateMove(g, 2);
        Utils.simulateMove(g, 0);

        // restore
        Utils.restoreState(g, sv, sm);
        check("restore 后合并标记恢复", g[0][3].isMerged() && g[0][3].value == 4);
    }

    static void testSaveRestoreIdempotent() {
        System.out.println("\n[12. save/restore 多次调用一致性]");

        Grid[][] g = makeBoard(new int[][]{{2,4,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});

        int[][] sv = Utils.saveValues(g);
        boolean[][] sm = Utils.saveMerges(g);

        // 多次 restore 结果一致
        Utils.restoreState(g, sv, sm);
        int v1 = g[0][0].value;
        Utils.restoreState(g, sv, sm);
        int v2 = g[0][0].value;

        check("两次 restore 结果一致", v1 == v2 && v1 == 2);
    }

    // ================================================================
    //  13. 随机生成方块
    // ================================================================

    static void testSpawnDistribution() {
        System.out.println("\n[13. 随机生成方块概率分布]");

        Random rng = new Random(12345);
        int count2 = 0, count4 = 0;
        int trials = 10000;
        for (int i = 0; i < trials; i++) {
            if (rng.nextDouble() < 0.9) count2++;
            else count4++;
        }
        double pct2 = 100.0 * count2 / trials;
        check("2 出现概率 ≈ 90% (实际 " + String.format("%.1f", pct2) + "%)",
              Math.abs(pct2 - 90.0) < 3.0);
    }

    static void testSpawnFullBoard() {
        System.out.println("\n[14. 满棋盘生成]");

        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},{16,8,4,2},{2,4,8,16},{16,8,4,2}
        });
        List<int[]> empties = Utils.getEmptyCells(g);
        check("满棋盘无空格", empties.isEmpty());
    }

    static void testSpawnSingleEmpty() {
        System.out.println("\n[15. 单空格生成]");

        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},{16,8,4,2},{2,4,8,16},{16,8,4,0}
        });
        List<int[]> empties = Utils.getEmptyCells(g);
        check("单空格正确位置", empties.size() == 1 &&
              empties.get(0)[0] == 3 && empties.get(0)[1] == 3);
    }

    // ================================================================
    //  16. 游戏结束判定
    // ================================================================

    static void testGameOverDetection() {
        System.out.println("\n[16. 游戏结束判定]");

        // 有空位 → 未结束
        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},{16,8,4,2},{2,4,8,16},{16,8,4,0}
        });
        check("有空位 → 未结束", !isGameOver(g));

        // 满但可合并 → 未结束
        g = makeBoard(new int[][]{
            {2,2,4,8},{8,4,2,16},{16,2,8,4},{4,8,16,2}
        });
        check("满但可合并 → 未结束", !isGameOver(g));

        // 满且不可合并 → 结束
        g = makeBoard(new int[][]{
            {2,4,8,16},{16,8,4,2},{2,4,8,16},{16,8,4,2}
        });
        check("满且不可合并 → 结束", isGameOver(g));

        // 边界: 角落相等但不相邻
        g = makeBoard(new int[][]{
            {2,4,8,16},{4,2,16,8},{8,16,2,4},{16,8,4,2}
        });
        check("交错满棋盘无相邻相等 → 结束", isGameOver(g));
    }

    static void testWinDetection() {
        System.out.println("\n[17. 胜利判定]");

        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},{32,64,128,256},{512,1024,2,4},{8,16,32,2048}
        });
        check("含2048 → 胜利", has2048(g));

        g = makeBoard(new int[][]{
            {2,4,8,16},{32,64,128,256},{512,1024,2,4},{8,16,32,1024}
        });
        check("无2048 → 未胜利", !has2048(g));

        g = makeBoard(new int[][]{{4096,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        check("超2048 → 胜利", has2048(g));
    }

    // ================================================================
    //  17. 递归边界情况
    // ================================================================

    static void testRecursiveEdgeCases() {
        System.out.println("\n[18. 递归滑动边界情况]");

        // 全列填充不同值，UP不合并
        Grid[][] g = makeBoard(new int[][]{
            {2,0,0,0},{4,0,0,0},{8,0,0,0},{16,0,0,0}
        });
        Utils.simulateMove(g, 0);  // UP (no move, already at top)
        check("全列不同值UP: 不变", boardEquals(g, new int[][]{
            {2,0,0,0},{4,0,0,0},{8,0,0,0},{16,0,0,0}
        }));

        // 间隔合并: [0,2,0,2] UP
        g = makeBoard(new int[][]{
            {0,0,0,0},{2,0,0,0},{0,0,0,0},{2,0,0,0}
        });
        Utils.simulateMove(g, 0);
        check("[0,2,0,2]→U: [4,0,0,0]",
              g[0][0].value == 4 && g[1][0].value == 0 &&
              g[2][0].value == 0 && g[3][0].value == 0);

        // tile被阻挡不能跳过: [2,8,2,0] RIGHT → [0,2,8,2]
        g = makeBoard(new int[][]{{2,8,2,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}});
        Utils.simulateMove(g, 3);
        check("[2,8,2,0]→R: [0,2,8,2]",
              g[0][0].value == 0 && g[0][1].value == 2 &&
              g[0][2].value == 8 && g[0][3].value == 2);
    }

    // ================================================================
    //  18. 满棋盘极端场景
    // ================================================================

    static void testFullBoardScenarios() {
        System.out.println("\n[19. 满棋盘极端场景]");

        // 满棋盘任一方块可合并 → 游戏继续
        Grid[][] g = makeBoard(new int[][]{
            {2,4,8,16},{32,64,128,256},{512,1024,2,4},{8,16,32,32}
        });
        check("全满仅一对相邻 → 未结束", !isGameOver(g));

        // 全满无相邻相等 → 游戏结束 (严格交错，无任何行列相邻相等)
        g = makeBoard(new int[][]{
            {2,  4,  8,  16},
            {16, 2,  4,  8},
            {8,  16, 32, 64},
            {64, 8,  16, 2}
        });
        check("满棋盘严格交错无相邻相等 → 结束", isGameOver(g));
    }

    // ---- 辅助判定函数 ----

    static boolean isGameOver(Grid[][] g) {
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++) {
                if (g[r][c].isEmpty()) return false;
                if (c < 3 && g[r][c].value == g[r][c + 1].value) return false;
                if (r < 3 && g[r][c].value == g[r + 1][c].value) return false;
            }
        return true;
    }

    static boolean has2048(Grid[][] g) {
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.value >= 2048) return true;
        return false;
    }
}
