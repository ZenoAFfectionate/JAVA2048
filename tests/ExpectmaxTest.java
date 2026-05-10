/**
 * Expectmax 算法全面测试
 */
public class ExpectmaxTest {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("━━━ Expectmax 全面测试 ━━━\n");
        testBasicMove();
        testMergePreference();
        testCornerStrategy();
        testEmptyBoard();
        testNearFullBoard();
        testDeterministic();
        testStatePreservation();
        testAllDirectionsInvalid();
        testPerformance();

        System.out.printf("%nExpectmax: %d passed, %d failed%n", passed, failed);
        System.exit(failed > 0 ? 1 : 0);
    }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }

    static Grid[][] makeBoard() {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c] = new Grid();
        return g;
    }

    static void testBasicMove() {
        System.out.println("[基本移动]");
        Grid[][] g = makeBoard(); g[0][0].value = 2;
        int dir = Expectmax.getBestDirection(g);
        check("非空棋盘返回合法方向", dir >= 0 && dir <= 3);
    }

    static void testMergePreference() {
        System.out.println("[合并偏好]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[0][1].value = 2;
        int dir = Expectmax.getBestDirection(g);
        check("2+2 相邻时返回合法方向", dir >= 0 && dir <= 3);
        check("合并方向不是 Up", dir != 0);
    }

    static void testCornerStrategy() {
        System.out.println("[角落策略]");
        Grid[][] g = makeBoard();
        g[3][3].value = 1024; g[3][2].value = 512;
        g[3][1].value = 256; g[3][0].value = 128;
        g[2][3].value = 64;
        int dir = Expectmax.getBestDirection(g);
        check("完美单调布局返回合法方向", dir >= 0 && dir <= 3);
    }

    static void testEmptyBoard() {
        System.out.println("[空棋盘]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[3][3].value = 4;
        long start = System.currentTimeMillis();
        int dir = Expectmax.getBestDirection(g);
        long ms = System.currentTimeMillis() - start;
        check("初始棋盘不崩溃", dir >= 0 && dir <= 3);
        check("初始棋盘响应 < 5s", ms < 5000);
    }

    static void testNearFullBoard() {
        System.out.println("[近满棋盘]");
        Grid[][] g = makeBoard();
        int[] vals = {2,4,8,16, 32,64,128,256, 512,1024,2,4, 8,16,32,0};
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[k++];
        long start = System.currentTimeMillis();
        int dir = Expectmax.getBestDirection(g);
        long ms = System.currentTimeMillis() - start;
        check("1空格棋盘不崩溃", dir >= 0 && dir <= 3);
        check("1空格搜索 < 10s", ms < 10000);
    }

    static void testDeterministic() {
        System.out.println("[确定性]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[1][0].value = 4;
        int d1 = Expectmax.getBestDirection(g);
        int d2 = Expectmax.getBestDirection(g);
        check("相同输入 → 相同输出", d1 == d2);
    }

    static void testStatePreservation() {
        System.out.println("[状态保持]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[1][0].value = 8; g[3][3].value = 32;
        int[] vals = {g[0][0].value, g[0][1].value, g[1][0].value, g[3][3].value};
        Expectmax.getBestDirection(g);
        check("调用后 [0][0] 不变", g[0][0].value == vals[0]);
        check("调用后 [0][1] 不变", g[0][1].value == vals[1]);
        check("调用后 [1][0] 不变", g[1][0].value == vals[2]);
        check("调用后 [3][3] 不变", g[3][3].value == vals[3]);
    }

    static void testAllDirectionsInvalid() {
        System.out.println("[全阻塞棋盘]");
        Grid[][] g = makeBoard();
        // 交错填满使所有方向都无法移动/合并
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = ((r + c) % 2 == 0) ? 2 : 4;
        try {
            Expectmax.getBestDirection(g);
            check("全阻塞棋盘不崩溃", true);
        } catch (Exception e) {
            check("全阻塞棋盘不崩溃", false);
        }
    }

    static void testPerformance() {
        System.out.println("[性能]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[1][0].value = 8;
        long t0 = System.nanoTime();
        for (int i = 0; i < 50; i++) {
            Grid[][] g2 = makeBoard();
            g2[0][0].value = 2; g2[0][1].value = 4; g2[1][0].value = 8;
            Expectmax.getBestDirection(g2);
        }
        long ns = System.nanoTime() - t0;
        double avgMs = ns / 50.0 / 1_000_000;
        check("平均每次决策 < 100ms", avgMs < 100);
    }
}
