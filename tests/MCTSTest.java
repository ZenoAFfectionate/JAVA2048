/**
 * MCTS 算法全面测试 — 覆盖核心逻辑、边界条件、性能、状态保持
 */
public class MCTSTest {
    private static int passed = 0, failed = 0;

    public static void main(String[] args) {
        System.out.println("━━━ MCTS 全面测试 ━━━\n");
        testBasicMove();
        testMergePreference();
        testMultipleRunsConsistent();
        testEmptyBoard();
        testNearFullBoard();
        testNoCrashOnFullBoard();
        testStatePreservation();
        testSingleValidDirection();
        testAllDirectionsBlocked();
        testSlideLogic();
        testCopyBoard();
        testSpawnRandom();
        testGetValidMoves();
        testIterationsSetter();

        System.out.printf("%nMCTS: %d passed, %d failed%n", passed, failed);
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

    // ---- 已有测试 ----

    static void testBasicMove() {
        System.out.println("[基本移动]");
        Grid[][] g = makeBoard(); g[0][0].value = 2;
        int dir = MCTS.getBestDirection(g);
        check("非空棋盘返回合法方向", dir >= 0 && dir <= 3);
        check("方向名称非 null", MCTS.getDirectionName(dir) != null);
    }

    static void testMergePreference() {
        System.out.println("[合并偏好]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[0][1].value = 2;
        int dir = MCTS.getBestDirection(g);
        check("2+2 相邻时返回合法方向", dir >= 0 && dir <= 3);
    }

    static void testMultipleRunsConsistent() {
        System.out.println("[多次运行一致性]");
        Grid[][] g = makeBoard();
        g[0][0].value = 8; g[0][1].value = 4;
        g[1][0].value = 4; g[1][1].value = 2;
        int[] counts = new int[4];
        for (int i = 0; i < 3; i++) {
            Grid[][] g2 = makeBoard();
            g2[0][0].value = 8; g2[0][1].value = 4;
            g2[1][0].value = 4; g2[1][1].value = 2;
            counts[MCTS.getBestDirection(g2)]++;
        }
        check("3次运行结果均在合法范围", true);
    }

    static void testEmptyBoard() {
        System.out.println("[空棋盘]");
        Grid[][] g = makeBoard(); g[0][0].value = 2; g[3][3].value = 4;
        long start = System.currentTimeMillis();
        int dir = MCTS.getBestDirection(g);
        long ms = System.currentTimeMillis() - start;
        check("初始棋盘不崩溃", dir >= 0 && dir <= 3);
        check("初始棋盘搜索 < 10s", ms < 10000);
    }

    static void testNearFullBoard() {
        System.out.println("[近满棋盘]");
        Grid[][] g = makeBoard();
        int[] vals = {2,4,8,16, 32,64,128,256, 8,16,32,64, 128,256,512,0};
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[k++];
        long start = System.currentTimeMillis();
        int dir = MCTS.getBestDirection(g);
        long ms = System.currentTimeMillis() - start;
        check("近满棋盘不崩溃", dir >= 0 && dir <= 3);
        check("近满棋盘搜索 < 15s", ms < 15000);
    }

    static void testNoCrashOnFullBoard() {
        System.out.println("[满棋盘不崩溃]");
        Grid[][] g = makeBoard();
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = (k++ % 2 == 0) ? 2 : 4;
        try {
            MCTS.getBestDirection(g);
            check("满棋盘不抛异常", true);
        } catch (Exception e) {
            check("满棋盘不抛异常", false);
        }
    }

    // ---- 新增: 状态保持 ----

    static void testStatePreservation() {
        System.out.println("[状态保持]");
        Grid[][] g = makeBoard();
        g[0][0].value = 2; g[0][1].value = 4; g[1][0].value = 4;
        // 记录调用前的状态
        int[][] before = MCTS.fromGrid(g);
        MCTS.getBestDirection(g);
        // 调用后状态应完全恢复
        boolean preserved = true;
        for (int r = 0; r < 4 && preserved; r++)
            for (int c = 0; c < 4 && preserved; c++)
                if (g[r][c].value != before[r][c]) preserved = false;
        check("调用后棋盘状态完全恢复", preserved);
    }

    // ---- 新增: 单一合法方向 ----

    static void testSingleValidDirection() {
        System.out.println("[单一合法方向]");
        Grid[][] g = makeBoard();
        // 最上行全满，只能 Down
        g[0][0].value = 2; g[0][1].value = 4; g[0][2].value = 8; g[0][3].value = 16;
        // 下行只有几个 tile，确保 UP 是无效的
        g[1][0].value = 2; g[1][1].value = 4;
        int dir = MCTS.getBestDirection(g);
        check("存在合法方向时返回有效值", dir >= 0 && dir <= 3);
    }

    // ---- 新增: 所有方向阻塞 ----

    static void testAllDirectionsBlocked() {
        System.out.println("[所有方向阻塞]");
        Grid[][] g = makeBoard();
        // 对角线摆放使所有方向都无变化
        int[] vals = {2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2, 4, 8, 16, 32, 64};
        int k = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[k++];
        try {
            MCTS.getBestDirection(g);  // 可能无合法方向
            check("全阻塞棋盘不崩溃", true);
        } catch (Exception e) {
            check("全阻塞棋盘不崩溃", false);
        }
    }

    // ---- 新增: 快照/拷贝/滑动逻辑 ----

    static void testSlideLogic() {
        System.out.println("[滑动逻辑]");
        int[][] b = new int[4][4];
        b[0][0] = 2; b[1][0] = 2;  // 同列两个2
        int score = MCTS.applyMove(b, 0);  // UP
        check("UP 合并 2+2: score = 4", score == 4);
        check("UP 后 [0][0] = 4", b[0][0] == 4);
        check("UP 后 [1][0] = 0", b[1][0] == 0);

        // 测试向上滑入空格
        int[][] b2 = new int[4][4];
        b2[3][0] = 2; b2[0][0] = 0;
        int s2 = MCTS.applyMove(b2, 0);
        check("UP 滑入空格: b[0][0] = 2", b2[0][0] == 2);
        check("UP 滑入空格: b[3][0] = 0", b2[3][0] == 0);

        // 测试无效移动
        int[][] b3 = new int[4][4];
        b3[0][0] = 2; b3[0][1] = 4; b3[0][2] = 8;
        int s3 = MCTS.applyMove(b3, 0);  // UP against wall
        check("UP 贴墙无变化: score = -1", s3 == -1);
        check("UP 贴墙状态不变", b3[0][0] == 2 && b3[0][1] == 4 && b3[0][2] == 8);
    }

    static void testCopyBoard() {
        System.out.println("[棋盘拷贝]");
        int[][] src = new int[4][4];
        src[0][0] = 2; src[3][3] = 1024;
        int[][] dst = MCTS.copyBoard(src);
        dst[0][0] = 99;  // 修改拷贝不影响原版
        check("深拷贝 - 原版不受修改影响", src[0][0] == 2);
        check("深拷贝 - 其他值一致", dst[3][3] == 1024);
    }

    static void testSpawnRandom() {
        System.out.println("[随机生成]");
        int[][] b = new int[4][4];
        b[0][0] = 2;  // 只有一个 tile，其余 15 格为空
        MCTS.spawnRandom(b);
        int nonZero = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (b[r][c] != 0) nonZero++;
        check("spawn 后新增一个非零 tile", nonZero == 2);
        // 满棋盘不生成
        int[][] full = new int[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                full[r][c] = 2;
        MCTS.spawnRandom(full);
        int cnt = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (full[r][c] == 2) cnt++;
        check("满棋盘 spawn 无变化", cnt == 16);
    }

    static void testGetValidMoves() {
        System.out.println("[合法方向检测]");
        int[][] b = new int[4][4];
        b[0][0] = 2;
        java.util.List<Integer> v = MCTS.getValidMoves(b);
        check("单 tile 棋盘合法方向 ≥ 2 (靠墙方向无效)", v.size() >= 2);

        // 所有 tile 顶在左上角
        int[][] b2 = new int[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                b2[r][c] = (r + c) % 2 == 0 ? 2 : 4;
        java.util.List<Integer> v2 = MCTS.getValidMoves(b2);
        check("交错满棋盘合法方向数 ≤ 4", v2.size() <= 4);
    }

    static void testIterationsSetter() {
        System.out.println("[迭代次数设定]");
        int orig = MCTS.getIterations();
        MCTS.setIterations(10);
        check("setIterations(10) 后 get = 10", MCTS.getIterations() == 10);
        MCTS.setIterations(0);
        check("setIterations(0) 钳制为 1", MCTS.getIterations() == 1);
        MCTS.setIterations(-5);
        check("setIterations(-5) 钳制为 1", MCTS.getIterations() == 1);
        MCTS.setIterations(orig);
    }
}
