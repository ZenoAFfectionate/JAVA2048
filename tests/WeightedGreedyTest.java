/**
 * WeightedGreedy 算法全面测试
 */
public class WeightedGreedyTest {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("━━━ WeightedGreedy 全面测试 ━━━\n");
        testWeightMatrix();
        testBasicMove();
        testCornerBias();
        testMergeableBoard();
        testDeterministic();
        testStatePreservation();
        testWeightedSum();
        testFullBoard();

        System.out.printf("%nWeightedGreedy: %d passed, %d failed%n", passed, failed);
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

    static void testWeightMatrix() {
        System.out.println("[权重矩阵]");
        check("右下角权重 = 15 (最大值)", WeightedGreedy.WEIGHTS[3][3] == 15);
        check("左上角权重 = 3", WeightedGreedy.WEIGHTS[0][0] == 3);
        check("第一行递减: [0][0] > [0][3]",
                WeightedGreedy.WEIGHTS[0][0] > WeightedGreedy.WEIGHTS[0][3]);
        // 蛇形验证
        check("蛇形: [1][0] < [1][1]", WeightedGreedy.WEIGHTS[1][0] < WeightedGreedy.WEIGHTS[1][1]);
        check("蛇形: [2][0] > [2][1]", WeightedGreedy.WEIGHTS[2][0] > WeightedGreedy.WEIGHTS[2][1]);
    }

    static void testBasicMove() {
        System.out.println("[基本移动]");
        Grid[][] g = makeBoard(); g[0][0].value = 2;
        int dir = WeightedGreedy.getBestDirection(g);
        check("非空棋盘返回合法方向", dir >= 0 && dir <= 3);
    }

    static void testCornerBias() {
        System.out.println("[角落偏向]");
        Grid[][] g = makeBoard();
        g[3][3].value = 256; g[0][0].value = 2;
        double score = WeightedGreedy.weightedSum(g);
        check("256 在 (3,3) 权重 = 3840 + 2×(0,0)=6 → 3846", Math.abs(score - 3846) < 1);
    }

    static void testMergeableBoard() {
        System.out.println("[可合并]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[0][1].value = 2;
        int dir = WeightedGreedy.getBestDirection(g);
        check("2+2 相邻返回合法方向", dir >= 0 && dir <= 3);
    }

    static void testDeterministic() {
        System.out.println("[确定性]");
        Grid[][] g = makeBoard();
        g[0][0].value = 4; g[0][1].value = 2; g[0][2].value = 4; g[1][0].value = 2;
        int d1 = WeightedGreedy.getBestDirection(g);
        int d2 = WeightedGreedy.getBestDirection(g);
        check("相同输入 → 相同输出", d1 == d2);
    }

    static void testStatePreservation() {
        System.out.println("[状态保持]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 8; g[3][3].value = 128;
        int v00 = g[0][0].value, v01 = g[0][1].value, v33 = g[3][3].value;
        WeightedGreedy.getBestDirection(g);
        check("调用后 [0][0] 不变", g[0][0].value == v00);
        check("调用后 [0][1] 不变", g[0][1].value == v01);
        check("调用后 [3][3] 不变", g[3][3].value == v33);
    }

    static void testWeightedSum() {
        System.out.println("[加权和计算]");
        Grid[][] g = makeBoard();
        double empty = WeightedGreedy.weightedSum(g);
        check("空棋盘加权和为 0", empty == 0);
        // 满棋盘
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = 2;
        double full = WeightedGreedy.weightedSum(g);
        int expectedWeight = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                expectedWeight += WeightedGreedy.WEIGHTS[r][c];
        check("满值2棋盘加权和 = 2×总权重", Math.abs(full - 2.0 * expectedWeight) < 1);
    }

    static void testFullBoard() {
        System.out.println("[满棋盘不崩溃]");
        Grid[][] g = makeBoard();
        int[] vals = {2,4,8,16, 32,64,128,256, 512,1024,2,4, 8,16,32,64};
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[k++];
        try { WeightedGreedy.getBestDirection(g); check("满棋盘不崩溃", true); }
        catch (Exception e) { check("满棋盘不崩溃", false); }
    }
}
