import javax.swing.SwingUtilities;
import java.awt.event.KeyEvent;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * GhostingTest — 验证动画系统不会产生重影。
 *
 * 主要验证点：
 *  1. 连续 N 次 executeMove 后，animations 列表不累加（最多只有本次 move 的动画）。
 *  2. MERGE 的 delay 配置正确（= SLIDE_DURATION_MS），保证"先滑动后闪光"。
 *  3. POP 的 delay 配置正确，保证"新方块在 SLIDE 后出现"。
 *  4. AnimationEngine.clear() 后 isRunning()=false、animCount=0。
 *  5. 同一个动画的 getProgress() 在 delay 内返回 0，delay 后单调递增。
 */
public class GhostingTest {
    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(GhostingTest::run);
        System.out.printf("%nGhostingTest: %d passed, %d failed%n", passed, failed);
        System.exit(failed > 0 ? 1 : 0);
    }

    static void run() {
        testAnimationsNotAccumulated();
        testMergeHasDelay();
        testPopHasDelay();
        testSlideNoDelay();
        testClearResetsState();
        testProgressDuringDelay();
        testIsDoneWaitsForDelayPlusDuration();
    }

    static void testAnimationsNotAccumulated() {
        try {
            GameView view = new GameView();
            Field gbField = GameView.class.getDeclaredField("gameBoard");
            gbField.setAccessible(true);
            Object gameBoard = gbField.get(view);

            Field engField = gameBoard.getClass().getDeclaredField("animEngine");
            engField.setAccessible(true);
            AnimationEngine eng = (AnimationEngine) engField.get(gameBoard);

            Field gridsField = gameBoard.getClass().getDeclaredField("grids");
            gridsField.setAccessible(true);
            Grid[][] grids = (Grid[][]) gridsField.get(gameBoard);

            Method execMove = gameBoard.getClass().getDeclaredMethod("executeMove", int.class);
            execMove.setAccessible(true);

            int maxObserved = 0;
            for (int i = 1; i <= 10; i++) {
                // 每次重置为 [2,2,0,0]，LEFT 合并
                for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) grids[r][c].value = 0;
                grids[0][0].value = 2; grids[0][1].value = 2;

                execMove.invoke(gameBoard, KeyEvent.VK_LEFT);
                if (eng.animCount() > maxObserved) maxObserved = eng.animCount();
            }

            // 每次 move 预期：2 SLIDE（两个合并源）+ 1 MERGE + 1 POP = 4
            check("连续 10 次 move 后 animations 不累加（max="+maxObserved+", 期望≤4）",
                  maxObserved <= 4);
        } catch (Exception e) {
            e.printStackTrace();
            check("animation accumulation test 无异常", false);
        }
    }

    static void testMergeHasDelay() {
        AnimationEngine eng = new AnimationEngine();
        eng.addMerge(0, 0, 4);
        AnimationEngine.Anim a = eng.getAnimations().get(0);
        check("MERGE.delayMs == SLIDE_DURATION_MS (="
                + AnimationEngine.SLIDE_DURATION_MS + ")，实际="+a.delayMs,
              a.delayMs == AnimationEngine.SLIDE_DURATION_MS);
    }

    static void testPopHasDelay() {
        AnimationEngine eng = new AnimationEngine();
        eng.addPop(0, 0, 2);
        AnimationEngine.Anim a = eng.getAnimations().get(0);
        check("POP.delayMs == SLIDE_DURATION_MS，实际="+a.delayMs,
              a.delayMs == AnimationEngine.SLIDE_DURATION_MS);
    }

    static void testSlideNoDelay() {
        AnimationEngine eng = new AnimationEngine();
        eng.addSlide(0, 0, 0, 3, 2);
        AnimationEngine.Anim a = eng.getAnimations().get(0);
        check("SLIDE.delayMs == 0，实际="+a.delayMs, a.delayMs == 0);
    }

    static void testClearResetsState() {
        AnimationEngine eng = new AnimationEngine();
        eng.addSlide(0, 0, 0, 3, 2);
        eng.addMerge(0, 3, 4);
        eng.addPop(2, 2, 2);
        check("clear 前 animCount="+eng.animCount(), eng.animCount() == 3);

        eng.clear();
        check("clear 后 animCount=0", eng.animCount() == 0);
        check("clear 后 isRunning=false", !eng.isRunning());
    }

    static void testProgressDuringDelay() {
        AnimationEngine.Anim a = new AnimationEngine.Anim(
            AnimationEngine.Type.MERGE, 0, 0, 4, 150, 100);  // 100ms delay, 150ms duration
        a.startTime = System.currentTimeMillis();

        // 刚 start 时 progress 应该是 0
        check("delay 刚开始 progress=0", a.getProgress() == 0.0f);
        check("delay 刚开始 isStarted=false", !a.isStarted());
        check("delay 刚开始 isDone=false", !a.isDone());

        // 快进到 delay 结束（模拟：手动设置 startTime 为 105ms 前）
        a.startTime = System.currentTimeMillis() - 105;
        check("delay 结束后 progress > 0", a.getProgress() > 0.0f);
        check("delay 结束后 isStarted=true", a.isStarted());
        check("delay 结束后尚未完成 isDone=false", !a.isDone());
    }

    static void testIsDoneWaitsForDelayPlusDuration() {
        AnimationEngine.Anim a = new AnimationEngine.Anim(
            AnimationEngine.Type.POP, 0, 0, 2, 180, 120);  // 120 delay + 180 duration = 300ms 总
        a.startTime = System.currentTimeMillis() - 299;
        check("delay+duration 前 1ms 未完成", !a.isDone());

        a.startTime = System.currentTimeMillis() - 305;
        check("delay+duration 后 isDone=true", a.isDone());
    }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }
}
