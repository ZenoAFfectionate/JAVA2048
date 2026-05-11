import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 动画引擎 — 管理 tile 滑动/弹出/合并动画的生命周期与渲染查询
 *
 * <p>生命周期: clear() → addSlide/addPop/addMerge (多次) → start(onFrame, onFinish) →
 * Timer 每 ~16ms 调用 onFrame 触发重绘 → 全部完成后调用 onFinish。
 *
 * <p>关键修复 (2026-05-11)：
 *   旧实现的 Timer 仅检查"是否全部完成"，<b>从不调用 repaint</b> →
 *   动画期间没有补帧，看到的就是"瞬移"/"闪一下"，这是游戏卡顿的核心原因。
 *   新版增加 onFrame 回调，每 ~16ms 触发一次 GameBoard.repaint()，得到流畅的 60fps 动画。
 *
 * <p>测试模式: completeAll() 瞬间完成所有动画并触发回调
 */
public class AnimationEngine {

    public enum Type { SLIDE, POP, MERGE }

    /** 帧定时器周期（毫秒）。16ms ≈ 62.5 fps，匹配主流显示器刷新率。 */
    private static final int FRAME_INTERVAL_MS = 16;

    /**
     * 单个动画的运行时数据。
     * 渲染位置通过 getRenderX/Y 查询，由 GameBoard.paintComponent 每帧调用。
     *
     * <p>delayMs: 动画启动后延迟多少毫秒才开始（用于 MERGE 等待 SLIDE 完成）。
     */
    public static class Anim {
        public final Type type;
        public final int fromRow, fromCol, toRow, toCol;
        public final int row, col;
        public final int value;
        public final int durationMs;
        public final int delayMs;
        long startTime;

        // SLIDE 构造（无 delay）
        public Anim(Type type, int fromRow, int fromCol, int toRow, int toCol,
                    int value, int durationMs) {
            this(type, fromRow, fromCol, toRow, toCol, value, durationMs, 0);
        }

        // SLIDE 构造（带 delay）
        public Anim(Type type, int fromRow, int fromCol, int toRow, int toCol,
                    int value, int durationMs, int delayMs) {
            this.type = type;
            this.fromRow = fromRow; this.fromCol = fromCol;
            this.toRow = toRow;     this.toCol = toCol;
            this.row = toRow;       this.col = toCol;
            this.value = value;
            this.durationMs = durationMs;
            this.delayMs = delayMs;
        }

        // POP / MERGE 构造（无 delay）
        public Anim(Type type, int row, int col, int value, int durationMs) {
            this(type, row, col, value, durationMs, 0);
        }

        // POP / MERGE 构造（带 delay）
        public Anim(Type type, int row, int col, int value, int durationMs, int delayMs) {
            this.type = type;
            this.fromRow = row; this.fromCol = col;
            this.toRow = row;   this.toCol = col;
            this.row = row;     this.col = col;
            this.value = value;
            this.durationMs = durationMs;
            this.delayMs = delayMs;
        }

        /** 是否已过 delay 阶段（开始真正动画）。 */
        public boolean isStarted() {
            return System.currentTimeMillis() - startTime >= delayMs;
        }

        /** 是否已完成（含 delay + duration）。 */
        public boolean isDone() {
            return System.currentTimeMillis() - startTime >= delayMs + durationMs;
        }

        /** 原始进度 0..1 (不受缓动影响；delay 内返回 0)。 */
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - startTime - delayMs;
            if (elapsed <= 0) return 0.0f;
            return Math.min(1.0f, (float) elapsed / durationMs);
        }

        /** 缓动后进度 0..1 (POP 可能 >1 弹性过冲) */
        public float getEasedProgress() {
            float t = getProgress();
            switch (type) {
                case SLIDE: return easeOutCubic(t);
                case POP:   return easeOutBack(t);
                case MERGE: return easeInOutCubic(t);
                default:    return t;
            }
        }

        /** 当前渲染 X 坐标 (像素，左上角) */
        public double getRenderX(int tileSize, int gap) {
            if (type == Type.SLIDE) {
                float p = getEasedProgress();
                double fx = gap + (gap + tileSize) * fromCol;
                double tx = gap + (gap + tileSize) * toCol;
                return fx + (tx - fx) * p;
            }
            return gap + (gap + tileSize) * col;
        }

        /** 当前渲染 Y 坐标 (像素，左上角) */
        public double getRenderY(int tileSize, int gap) {
            if (type == Type.SLIDE) {
                float p = getEasedProgress();
                double fy = gap + (gap + tileSize) * fromRow;
                double ty = gap + (gap + tileSize) * toRow;
                return fy + (ty - fy) * p;
            }
            return gap + (gap + tileSize) * row;
        }

        /** 当前缩放 (仅 POP 使用) */
        public double getScale() {
            if (type == Type.POP) {
                return Math.max(0.0, getEasedProgress());
            }
            return 1.0;
        }

        /** 合并光晕强度 0..1 (仅 MERGE 使用，先升后降) */
        public float getGlow() {
            if (type != Type.MERGE) return 0f;
            float p = getEasedProgress();
            if (p < 0.3f) return p / 0.3f;
            return 1.0f - (p - 0.3f) / 0.7f;
        }
    }

    // ================================================================
    //  缓动函数 (public static 便于测试)
    // ================================================================

    /** easeOutCubic: 快起慢停 */
    public static float easeOutCubic(float t) {
        float u = 1.0f - t;
        return 1.0f - u * u * u;
    }

    /** easeOutBack: 带弹性过冲 */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        double u = (double)(t - 1.0f);
        return (float)(1.0 + c3 * u * u * u + c1 * u * u);
    }

    /** easeInOutCubic: 两端平滑 */
    public static float easeInOutCubic(float t) {
        if (t < 0.5f) return 4.0f * t * t * t;
        float u = -2.0f * t + 2.0f;
        return 1.0f - u * u * u / 2.0f;
    }

    // ================================================================
    //  引擎实例
    // ================================================================

    private final List<Anim> animations = new ArrayList<>();
    private Timer timer;
    private Runnable onFrame;       // ★ 新增：每帧回调（用于触发 repaint）
    private Runnable onFinish;
    private boolean running;

    public boolean isRunning() { return running; }

    /** SLIDE 动画时长（毫秒）—— 同时用作 MERGE / POP 的 delay，保证它们在 SLIDE 结束后才开始。 */
    public static final int SLIDE_DURATION_MS = 120;

    public void addSlide(int fromRow, int fromCol, int toRow, int toCol, int value) {
        animations.add(new Anim(Type.SLIDE, fromRow, fromCol, toRow, toCol, value, SLIDE_DURATION_MS));
    }

    /**
     * POP 用于新方块出现：等所有 SLIDE 完成后再弹出（delay=SLIDE_DURATION_MS），
     * 避免新方块与滑动方块同时出现造成视觉混乱。
     */
    public void addPop(int row, int col, int value) {
        animations.add(new Anim(Type.POP, row, col, value, 180, SLIDE_DURATION_MS));
    }

    /**
     * MERGE 闪光：等 SLIDE 完成后才闪光（delay=SLIDE_DURATION_MS），
     * 否则合并目标格在 SLIDE 期间同时显示"已合并的 4"和"滑入的两个 2"→ 重影。
     */
    public void addMerge(int row, int col, int value) {
        animations.add(new Anim(Type.MERGE, row, col, value, 150, SLIDE_DURATION_MS));
    }

    public List<Anim> getAnimations() {
        return Collections.unmodifiableList(animations);
    }

    public int animCount() {
        return animations.size();
    }

    /**
     * 启动动画循环。
     *
     * @param onFrame  每帧调用 (~16ms)，用于触发 GameBoard.repaint()。
     *                 可为 null（表示静默推进时间）。
     * @param onFinish 全部动画完成后调用一次 (EDT 上)。
     */
    public void start(Runnable onFrame, Runnable onFinish) {
        this.onFrame = onFrame;
        this.onFinish = onFinish;
        if (animations.isEmpty()) {
            if (onFinish != null) onFinish.run();
            return;
        }
        running = true;
        final long now = System.currentTimeMillis();
        for (Anim a : animations) a.startTime = now;

        timer = new Timer(FRAME_INTERVAL_MS, (ActionEvent e) -> {
            // 每帧先 repaint（让正在播放的动画显示中间状态），再判定结束
            if (this.onFrame != null) this.onFrame.run();

            // allDone = 所有动画已过 delay + duration
            boolean allDone = true;
            for (Anim a : animations) {
                if (!a.isDone()) { allDone = false; break; }
            }
            if (allDone) {
                timer.stop();
                running = false;
                // 最后再 repaint 一次画终态
                if (this.onFrame != null) this.onFrame.run();
                if (this.onFinish != null) this.onFinish.run();
            }
        });
        timer.setRepeats(true);
        timer.start();
    }

    /**
     * 旧 API（保留兼容）：仅传 onFinish，无每帧回调。
     * 新代码应使用 {@link #start(Runnable, Runnable)} 同时传 onFrame 与 onFinish。
     */
    public void start(Runnable onFinish) {
        start(null, onFinish);
    }

    /** 立即完成所有动画并触发回调 (用于测试、ESC 重启等场景) */
    public void completeAll() {
        if (timer != null) timer.stop();
        for (Anim a : animations) a.startTime = 0;
        running = false;
        if (onFinish != null) {
            Runnable cb = onFinish;
            onFinish = null;
            cb.run();
        }
    }

    /** 清空动画列表并停止 timer (不触发回调) */
    public void clear() {
        if (timer != null) timer.stop();
        animations.clear();
        running = false;
        onFrame = null;
        onFinish = null;
    }
}
