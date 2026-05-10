/**
 * AnimationEngine 单元测试 — 缓动函数 + Anim 进度 + 引擎生命周期
 *
 * 编译: javac -encoding UTF-8 -cp src -d out src/*.java tests/AnimationEngineTest.java
 * 运行: java -cp out AnimationEngineTest
 */
public class AnimationEngineTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║   AnimationEngine 单元测试        ║");
        System.out.println("╚══════════════════════════════════╝\n");

        testEasingFunctions();
        testAnimProgress();
        testAnimRenderPosition();
        testEngineLifecycle();
        testEngineCompleteAll();
        testEngineClear();
        testMultipleAnims();
        testAnimProgressClampedAtOne();

        System.out.println("\n========================================");
        System.out.printf("  AnimationEngine: %d passed, %d failed%n", passed, failed);
        System.out.println("========================================");
        System.exit(failed > 0 ? 1 : 0);
    }

    // ---- helpers ----

    private static void check(String name, boolean cond) {
        if (cond) {
            System.out.println("  ✓ " + name);
            passed++;
        } else {
            System.out.println("  ✗ " + name + "  ← FAIL");
            failed++;
        }
    }

    private static void checkEquals(String name, int expected, int actual) {
        check(name, expected == actual);
        if (expected != actual)
            System.out.println("      expected: " + expected + "  actual: " + actual);
    }

    private static void checkEqualsF(String name, float expected, float actual, float eps) {
        check(name, Math.abs(expected - actual) < eps);
        if (Math.abs(expected - actual) >= eps)
            System.out.println("      expected: " + expected + "  actual: " + actual);
    }

    private static void checkTrue(String name, boolean cond) {
        check(name, cond);
    }

    // ================================================================
    //  缓动函数测试
    // ================================================================

    static void testEasingFunctions() {
        System.out.println("--- 缓动函数 ---\n");

        // easeOutCubic 边界
        checkEqualsF("easeOutCubic(0) = 0", 0f, AnimationEngine.easeOutCubic(0f), 0.001f);
        checkEqualsF("easeOutCubic(1) = 1", 1f, AnimationEngine.easeOutCubic(1f), 0.001f);

        // easeOutCubic 形状 (t=0.5 应 >0.5，因为快起)
        checkTrue("easeOutCubic(0.5) > 0.5 (快起慢停)",
                AnimationEngine.easeOutCubic(0.5f) > 0.5f);

        // easeOutBack 边界
        checkEqualsF("easeOutBack(0) = 0", 0f, AnimationEngine.easeOutBack(0f), 0.001f);
        checkEqualsF("easeOutBack(1) = 1", 1f, AnimationEngine.easeOutBack(1f), 0.001f);

        // easeOutBack 过冲 (>1 在某点)
        float maxBack = 0;
        for (float t = 0; t <= 1f; t += 0.05f) {
            float v = AnimationEngine.easeOutBack(t);
            if (v > maxBack) maxBack = v;
        }
        checkTrue("easeOutBack 存在过冲 (> 1.0)", maxBack > 1.0f);

        // easeInOutCubic 边界
        checkEqualsF("easeInOutCubic(0) = 0", 0f, AnimationEngine.easeInOutCubic(0f), 0.001f);
        checkEqualsF("easeInOutCubic(1) = 1", 1f, AnimationEngine.easeInOutCubic(1f), 0.001f);
        checkEqualsF("easeInOutCubic(0.5) = 0.5", 0.5f, AnimationEngine.easeInOutCubic(0.5f), 0.001f);

        // easeOutCubic 单调性
        boolean monotonic = true;
        float prev = AnimationEngine.easeOutCubic(0f);
        for (float t = 0.05f; t <= 1f; t += 0.05f) {
            float curr = AnimationEngine.easeOutCubic(t);
            if (curr < prev) { monotonic = false; break; }
            prev = curr;
        }
        checkTrue("easeOutCubic 单调递增", monotonic);
    }

    // ================================================================
    //  Anim.getProgress() 测试
    // ================================================================

    static void testAnimProgress() {
        System.out.println("\n--- Anim 进度 ---\n");

        AnimationEngine.Anim anim = new AnimationEngine.Anim(
                AnimationEngine.Type.SLIDE, 0, 0, 0, 1, 2, 100);

        // 模拟刚创建
        anim.startTime = System.currentTimeMillis();
        float p0 = anim.getProgress();
        checkTrue("刚创建时 progress ~ 0", p0 >= 0f && p0 < 0.1f);

        // 模拟已完成
        anim.startTime = System.currentTimeMillis() - 200; // 200ms ago, duration 100ms
        float p1 = anim.getProgress();
        checkEqualsF("超时后 progress = 1.0", 1.0f, p1, 0.001f);
    }

    // ================================================================
    //  Anim.getProgress() 不超过 1
    // ================================================================

    static void testAnimProgressClampedAtOne() {
        System.out.println("\n--- Anim 进度钳制 ---\n");

        AnimationEngine.Anim anim = new AnimationEngine.Anim(
                AnimationEngine.Type.POP, 0, 0, 2, 50);
        anim.startTime = System.currentTimeMillis() - 10000; // way past
        checkEqualsF("duration=50ms, 10000ms 后 progress = 1.0", 1.0f, anim.getProgress(), 0.001f);
    }

    // ================================================================
    //  Anim 渲染位置
    // ================================================================

    static void testAnimRenderPosition() {
        System.out.println("\n--- Anim 渲染位置 ---\n");

        // SLIDE 在 progress=1 时渲染位置 = 终点
        AnimationEngine.Anim slide = new AnimationEngine.Anim(
                AnimationEngine.Type.SLIDE, 2, 0, 0, 0, 4, 100);
        slide.startTime = 0; // completed
        int SIZE = 80, GAP = 10;
        double ex = GAP + (GAP + SIZE) * 0;  // col 0 target
        double ey = GAP + (GAP + SIZE) * 0;  // row 0 target
        check("SLIDE progress=1 渲染 X = 终点 X",
                Math.abs(ex - slide.getRenderX(SIZE, GAP)) < 0.01);
        check("SLIDE progress=1 渲染 Y = 终点 Y",
                Math.abs(ey - slide.getRenderY(SIZE, GAP)) < 0.01);

        // POP 在 progress=0 时 scale ≈ 0
        AnimationEngine.Anim pop = new AnimationEngine.Anim(
                AnimationEngine.Type.POP, 1, 2, 2, 180);
        pop.startTime = System.currentTimeMillis();
        checkTrue("POP 开始时 scale ≈ 0", pop.getScale() < 0.1);

        // MERGE glow 在中间时 > 0
        AnimationEngine.Anim merge = new AnimationEngine.Anim(
                AnimationEngine.Type.MERGE, 0, 0, 8, 150);
        merge.startTime = System.currentTimeMillis() - 22; // ~0.15 progress → glow rising
        float g = merge.getGlow();
        checkTrue("MERGE 中途 glow > 0", g > 0f);

        // 非合并无 glow
        AnimationEngine.Anim slide2 = new AnimationEngine.Anim(
                AnimationEngine.Type.SLIDE, 0, 0, 0, 1, 2, 100);
        checkEqualsF("SLIDE glow = 0", 0f, slide2.getGlow(), 0.001f);
    }

    // ================================================================
    //  引擎生命周期
    // ================================================================

    static void testEngineLifecycle() {
        System.out.println("\n--- 引擎生命周期 ---\n");

        AnimationEngine engine = new AnimationEngine();
        checkTrue("新建引擎 isRunning() = false", !engine.isRunning());
        checkEquals("新建引擎 animCount() = 0", 0, engine.animCount());

        engine.addSlide(0, 0, 0, 1, 2);
        checkEquals("addSlide 后 animCount() = 1", 1, engine.animCount());

        // start with empty callback should not NPE
        engine.start(null);
        checkTrue("start() 后 isRunning() = true", engine.isRunning());

        // complete immediately for test
        engine.completeAll();
        checkTrue("completeAll() 后 isRunning() = false", !engine.isRunning());
    }

    static void testEngineCompleteAll() {
        System.out.println("\n--- completeAll 回调 ---\n");

        AnimationEngine engine = new AnimationEngine();
        final boolean[] called = {false};
        engine.addPop(0, 0, 2);
        engine.start(() -> called[0] = true);
        engine.completeAll();
        checkTrue("completeAll() 触发 onFinish 回调", called[0]);
        checkTrue("completeAll() 后 isRunning() = false", !engine.isRunning());
        checkEquals("completeAll() 后 animCount() 不变", 1, engine.animCount());
    }

    static void testEngineClear() {
        System.out.println("\n--- clear 清空 ---\n");

        AnimationEngine engine = new AnimationEngine();
        engine.addSlide(0, 0, 1, 0, 4);
        engine.addMerge(0, 0, 8);
        checkEquals("clear 前 animCount() = 2", 2, engine.animCount());

        engine.clear();
        checkEquals("clear 后 animCount() = 0", 0, engine.animCount());
        checkTrue("clear 后 isRunning() = false", !engine.isRunning());
    }

    static void testMultipleAnims() {
        System.out.println("\n--- 多个动画共存 ---\n");

        AnimationEngine engine = new AnimationEngine();
        engine.addSlide(0, 0, 0, 1, 2);
        engine.addSlide(1, 0, 0, 0, 4);
        engine.addPop(3, 3, 2);

        checkEquals("3 个动画添加后 animCount = 3", 3, engine.animCount());

        java.util.List<AnimationEngine.Anim> anims = engine.getAnimations();
        boolean valuesOk = true;
        int[] expected = {2, 4, 2};
        for (int i = 0; i < anims.size(); i++) {
            if (anims.get(i).value != expected[i]) valuesOk = false;
        }
        checkTrue("动画 value 正确", valuesOk);

        checkTrue("anim[0] type = SLIDE", anims.get(0).type == AnimationEngine.Type.SLIDE);
        checkTrue("anim[1] type = SLIDE", anims.get(1).type == AnimationEngine.Type.SLIDE);
        checkTrue("anim[2] type = POP", anims.get(2).type == AnimationEngine.Type.POP);
    }
}
