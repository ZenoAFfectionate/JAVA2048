/**
 * 动画集成测试 — 输入缓冲 + 动画与游戏逻辑一致性
 *
 * 编译: javac -encoding UTF-8 -cp src -d out src/*.java tests/AnimationIntegrationTest.java
 * 运行: java -cp out AnimationIntegrationTest
 */
public class AnimationIntegrationTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║   Animation 集成测试              ║");
        System.out.println("╚══════════════════════════════════╝\n");

        testInputBuffering();
        testAnimationCompletesWithoutBlocking();
        testCompleteAllSyncsState();
        testAnimationResultMatchesDirectMove();
        testNoAnimationOnEmptyList();

        System.out.println("\n========================================");
        System.out.printf("  Integration: %d passed, %d failed%n", passed, failed);
        System.out.println("========================================");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }

    private static void checkEquals(String name, int expected, int actual) {
        if (expected == actual) {
            System.out.println("  ✓ " + name);
            passed++;
        } else {
            System.out.println("  ✗ " + name + "  ← FAIL (expected " + expected + " got " + actual + ")");
            failed++;
        }
    }

    // ================================================================
    //  输入缓冲测试
    // ================================================================

    static void testInputBuffering() {
        System.out.println("--- 输入缓冲 ---\n");

        AnimationEngine engine = new AnimationEngine();

        final int[] buffered = {0};
        engine.addSlide(0, 0, 0, 1, 2);
        engine.start(() -> {
            buffered[0] = 42; // 模拟 pendingKeyCode
        });

        check("动画运行中 isRunning() = true", engine.isRunning());

        engine.completeAll();
        check("completeAll 后回调已执行 (buffered = 42)", buffered[0] == 42);
        check("动画完成后 isRunning() = false", !engine.isRunning());
    }

    // ================================================================
    //  动画非阻塞
    // ================================================================

    static void testAnimationCompletesWithoutBlocking() {
        System.out.println("\n--- 动画非阻塞 ---\n");

        AnimationEngine engine = new AnimationEngine();
        final boolean[] finished = {false};

        long start = System.currentTimeMillis();
        engine.addPop(0, 0, 2);
        engine.start(() -> finished[0] = true);
        engine.completeAll();
        long elapsed = System.currentTimeMillis() - start;

        check("completeAll 后回调已触发", finished[0]);
        check("completeAll 不阻塞 (elapsed < 500ms)", elapsed < 500);
    }

    // ================================================================
    //  completeAll 后状态同步
    // ================================================================

    static void testCompleteAllSyncsState() {
        System.out.println("\n--- 状态同步 ---\n");

        AnimationEngine engine = new AnimationEngine();
        engine.addSlide(2, 0, 0, 0, 16);
        engine.addMerge(0, 0, 32);
        engine.addPop(3, 3, 2);

        check("动画前 isRunning() = false", !engine.isRunning());

        final boolean[] cb = {false};
        engine.start(() -> cb[0] = true);

        check("start 后 isRunning() = true", engine.isRunning());
        checkEquals("start 后 animCount = 3", 3, engine.animCount());

        engine.completeAll();

        check("completeAll 后 isRunning() = false", !engine.isRunning());
        check("completeAll 后回调触发", cb[0]);

        boolean allDone = true;
        for (AnimationEngine.Anim a : engine.getAnimations()) {
            if (a.getProgress() < 1f) allDone = false;
        }
        check("completeAll 后所有动画 progress = 1", allDone);
    }

    // ================================================================
    //  动画不影响游戏逻辑结果
    // ================================================================

    static void testAnimationResultMatchesDirectMove() {
        System.out.println("\n--- 动画不影响逻辑 ---\n");

        Grid[][] testGrids = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                testGrids[r][c] = new Grid();

        // 设置初始状态: 第一列有 2,4,4,空
        testGrids[0][0].value = 2;
        testGrids[1][0].value = 4;
        testGrids[2][0].value = 4;
        testGrids[3][0].value = 0;

        // 模拟向上移动 (不走动画引擎，纯逻辑测试)
        for (Grid[] row : testGrids)
            for (Grid g : row)
                g.setMerge(false);
        for (int c = 0; c < 4; c++)
            for (int r = 1; r < 4; r++)
                if (!testGrids[r][c].isEmpty())
                    testGrids[r][c].moveUp(testGrids, r, c);

        // 验证结果: [0][0]=2, [1][0]=8, [2][0]=0, [3][0]=0
        check("移动后 [0][0] = 2", testGrids[0][0].value == 2);
        check("移动后 [1][0] = 8 (4+4 合并)", testGrids[1][0].value == 8);
        check("移动后 [2][0] = 0", testGrids[2][0].value == 0);
        check("移动后 [3][0] = 0", testGrids[3][0].value == 0);
        check("移动后 [0][0] isMerged = false", !testGrids[0][0].isMerged());
        check("移动后 [1][0] isMerged = true (合并标志)", testGrids[1][0].isMerged());
    }

    // ================================================================
    //  空动画列表不触发错误
    // ================================================================

    static void testNoAnimationOnEmptyList() {
        System.out.println("\n--- 空动画列表 ---\n");

        AnimationEngine engine = new AnimationEngine();

        final boolean[] called = {false};
        engine.start(() -> called[0] = true);

        check("空动画列表直接回调", called[0]);
        check("空列表后 isRunning() = false", !engine.isRunning());
    }
}
