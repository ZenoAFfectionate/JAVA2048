import javax.swing.SwingUtilities;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AnimationFrameRateTest — 验证 AnimationEngine 在动画播放期间真正补帧。
 *
 * 旧实现的致命 BUG：Timer 启动后只检查"是否完成"，<b>从不调用 onFrame/repaint</b>，
 * 因此动画期间用户屏幕上没有中间帧，看到的就是"瞬移/卡顿"。
 *
 * 这个测试通过 onFrame 计数器验证：
 *   - 一个 120ms 的 SLIDE 动画期间应该被调用至少 5 次 (每 16ms 一次 ≈ 7 次)
 *   - 一个 180ms 的 POP 动画期间应该被调用至少 8 次
 *
 * 实现说明：测试在主线程跑，让 EDT 自然处理 Swing Timer 事件；
 * 用 AtomicInteger / AtomicBoolean + sleep poll 等待结果。
 */
public class AnimationFrameRateTest {

    static int passed = 0, failed = 0;

    public static void main(String[] args) throws Exception {
        // 主线程触发，让 EDT 处理 Timer
        testSlideHasFrames();
        testPopHasFrames();
        testMultipleAnimsHaveFrames();
        testEmptyAnimDoesNotStartTimer();
        testOnFrameNullSafety();

        System.out.printf("%nAnimationFrameRate: %d passed, %d failed%n", passed, failed);
        System.exit(failed > 0 ? 1 : 0);
    }

    /** 等待动画结束（最多 timeoutMs），轮询 finished 标志 */
    static void waitForFinish(AtomicBoolean finished, long timeoutMs) {
        long t0 = System.currentTimeMillis();
        while (!finished.get() && System.currentTimeMillis() - t0 < timeoutMs) {
            try { Thread.sleep(5); } catch (InterruptedException e) { /* ignore */ }
        }
    }

    /** 在 EDT 上启动动画 */
    static void startOnEdt(AnimationEngine eng, Runnable onFrame, Runnable onFinish) throws Exception {
        SwingUtilities.invokeAndWait(() -> eng.start(onFrame, onFinish));
    }

    /** 120ms SLIDE 动画期间 onFrame 应该被调用至少 5 次 */
    static void testSlideHasFrames() throws Exception {
        AnimationEngine eng = new AnimationEngine();
        eng.addSlide(0, 0, 0, 3, 4);

        AtomicInteger frameCount = new AtomicInteger();
        AtomicBoolean finished = new AtomicBoolean();
        startOnEdt(eng, frameCount::incrementAndGet, () -> finished.set(true));
        waitForFinish(finished, 500);

        check("SLIDE: onFrame 在动画期间被调用 ≥ 5 次 (实际=" + frameCount.get() + ")",
              frameCount.get() >= 5);
        check("SLIDE: 动画完成后 onFinish 被调用", finished.get());
    }

    /** 180ms POP 动画期间 onFrame 应该被调用至少 8 次 */
    static void testPopHasFrames() throws Exception {
        AnimationEngine eng = new AnimationEngine();
        eng.addPop(2, 2, 4);

        AtomicInteger frameCount = new AtomicInteger();
        AtomicBoolean finished = new AtomicBoolean();
        startOnEdt(eng, frameCount::incrementAndGet, () -> finished.set(true));
        waitForFinish(finished, 500);

        check("POP: onFrame 在动画期间被调用 ≥ 8 次 (实际=" + frameCount.get() + ")",
              frameCount.get() >= 8);
        check("POP: onFinish 被调用", finished.get());
    }

    /** 一次 move 通常产生多个 SLIDE + 1 POP；onFrame 应该按最长动画 (180ms POP) 持续 */
    static void testMultipleAnimsHaveFrames() throws Exception {
        AnimationEngine eng = new AnimationEngine();
        eng.addSlide(0, 0, 0, 3, 2);
        eng.addSlide(1, 0, 1, 3, 4);
        eng.addSlide(2, 0, 2, 3, 8);
        eng.addMerge(0, 3, 4);
        eng.addPop(3, 1, 2);

        AtomicInteger frameCount = new AtomicInteger();
        AtomicBoolean finished = new AtomicBoolean();
        startOnEdt(eng, frameCount::incrementAndGet, () -> finished.set(true));
        waitForFinish(finished, 500);

        check("多动画: onFrame 调用 ≥ 8 次 (实际=" + frameCount.get() + ")",
              frameCount.get() >= 8);
        check("多动画: 全部完成后 onFinish 被调用", finished.get());
    }

    /** 空动画列表时 start() 应该立即调 onFinish，不启动 Timer */
    static void testEmptyAnimDoesNotStartTimer() throws Exception {
        AnimationEngine eng = new AnimationEngine();
        AtomicInteger frameCount = new AtomicInteger();
        AtomicBoolean finished = new AtomicBoolean();
        startOnEdt(eng, frameCount::incrementAndGet, () -> finished.set(true));

        check("空动画: onFinish 立即调用", finished.get());
        check("空动画: onFrame 未被调用", frameCount.get() == 0);
        check("空动画: isRunning=false", !eng.isRunning());
    }

    /** onFrame=null 时不应崩溃 */
    static void testOnFrameNullSafety() throws Exception {
        AnimationEngine eng = new AnimationEngine();
        eng.addPop(0, 0, 2);

        AtomicBoolean finished = new AtomicBoolean();
        try {
            startOnEdt(eng, null, () -> finished.set(true));
            waitForFinish(finished, 500);
            check("onFrame=null 不崩溃且 onFinish 仍触发", finished.get());
        } catch (Exception e) {
            check("onFrame=null 不崩溃", false);
        }
    }

    static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }
}
