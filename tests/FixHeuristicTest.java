/**
 * FixHeuristic 算法全面测试
 */
public class FixHeuristicTest {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("━━━ FixHeuristic 全面测试 ━━━\n");
        testBasicMove();
        testCornerAnchor();
        testEmptyBoard();
        testMergeableBoard();
        testFullBoard();
        testDeterministic();
        testStatePreservation();
        testSingleDirection();
        testEvalNoCrash();

        System.out.printf("%nFixHeuristic: %d passed, %d failed%n", passed, failed);
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
        int dir = FixHeuristic.getBestDirection(g);
        check("非空棋盘返回合法方向", dir >= 0 && dir <= 3);
        check("方向名称非 null", FixHeuristic.getDirectionName(dir) != null);
    }

    static void testCornerAnchor() {
        System.out.println("[角落锚定]");
        Grid[][] g = makeBoard();
        g[3][3].value = 128; g[0][0].value = 2; g[0][1].value = 4;
        int dir = FixHeuristic.getBestDirection(g);
        check("有最大值在角落时返回有效方向", dir >= 0 && dir <= 3);
        int score = FixHeuristic.evaluate(g);
        check("有最大值在角落时评估值 > 0", score > 0);
    }

    static void testEmptyBoard() {
        System.out.println("[空棋盘]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[3][3].value = 2;
        int dir = FixHeuristic.getBestDirection(g);
        check("初始棋盘返回合法方向", dir >= 0 && dir <= 3);
    }

    static void testMergeableBoard() {
        System.out.println("[可合并棋盘]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[0][1].value = 2;
        int dir = FixHeuristic.getBestDirection(g);
        check("2+2 相邻时返回的方向不是 Up", dir != 0);
    }

    static void testFullBoard() {
        System.out.println("[满棋盘]");
        Grid[][] g = makeBoard();
        int[] vals = {2,4,8,16, 32,64,128,256, 512,1024,2,4, 8,16,32,64};
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[k++];
        try { FixHeuristic.getBestDirection(g); check("满棋盘不崩溃", true); }
        catch (Exception e) { check("满棋盘不崩溃", false); }
    }

    static void testDeterministic() {
        System.out.println("[确定性]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[0][2].value = 2;
        g[0][3].value = 4; g[1][0].value = 4; g[1][1].value = 2;
        check("相同输入 → 相同输出",
                FixHeuristic.getBestDirection(g) == FixHeuristic.getBestDirection(g));
    }

    static void testStatePreservation() {
        System.out.println("[状态保持]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[1][0].value = 8;
        int v00 = g[0][0].value, v01 = g[0][1].value, v10 = g[1][0].value;
        FixHeuristic.getBestDirection(g);
        check("调用后 [0][0] 不变", g[0][0].value == v00);
        check("调用后 [0][1] 不变", g[0][1].value == v01);
        check("调用后 [1][0] 不变", g[1][0].value == v10);
    }

    static void testSingleDirection() {
        System.out.println("[单一方向]");
        Grid[][] g = makeBoard();
        // 顶行满，只有下方有空间
        g[0][0].value = 2; g[0][1].value = 4; g[0][2].value = 8; g[0][3].value = 16;
        g[1][0].value = 32;
        int dir = FixHeuristic.getBestDirection(g);
        check("存在合法方向", dir >= 0 && dir <= 3);
    }

    static void testEvalNoCrash() {
        System.out.println("[评估不崩溃]");
        Grid[][] g = makeBoard();
        FixHeuristic.evaluate(g);  // 全空
        g[0][0].value = 2; g[3][3].value = 2048;
        FixHeuristic.evaluate(g);
        g[0][0].value = 65536;  // 超大值
        try { FixHeuristic.evaluate(g); check("超大值评估不崩溃", true); }
        catch (Exception e) { check("超大值评估不崩溃", false); }
    }
}
