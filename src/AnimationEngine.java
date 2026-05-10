import javax.swing.Timer;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 动画引擎 — 管理 tile 滑动/弹出/合并动画的生命周期与渲染查询
 *
 * 生命周期: clear() → addSlide/addPop/addMerge (多次) → start(onFinish) → 渲染循环查询 → onFinish 回调
 * 测试模式: completeAll() 瞬间完成所有动画并触发回调
 */
public class AnimationEngine {

    public enum Type { SLIDE, POP, MERGE }

    /**
     * 单个动画的运行时数据。
     * 渲染位置通过 getRenderX/Y 查询，由 GameBoard.paintComponent 每帧调用。
     */
    public static class Anim {
        public final Type type;
        public final int fromRow, fromCol, toRow, toCol;
        public final int row, col;
        public final int value;
        public final int durationMs;
        long startTime;

        // SLIDE 构造
        public Anim(Type type, int fromRow, int fromCol, int toRow, int toCol,
                    int value, int durationMs) {
            this.type = type;
            this.fromRow = fromRow; this.fromCol = fromCol;
            this.toRow = toRow;     this.toCol = toCol;
            this.row = toRow;       this.col = toCol;
            this.value = value;
            this.durationMs = durationMs;
        }

        // POP / MERGE 构造
        public Anim(Type type, int row, int col, int value, int durationMs) {
            this.type = type;
            this.fromRow = row; this.fromCol = col;
            this.toRow = row;   this.toCol = col;
            this.row = row;     this.col = col;
            this.value = value;
            this.durationMs = durationMs;
        }

        /** 原始进度 0..1 (不受缓动影响) */
        public float getProgress() {
            long elapsed = System.currentTimeMillis() - startTime;
            return Math.min(1.0f, Math.max(0.0f, (float) elapsed / durationMs));
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
    private Runnable onFinish;
    private boolean running;

    public boolean isRunning() { return running; }

    public void addSlide(int fromRow, int fromCol, int toRow, int toCol, int value) {
        animations.add(new Anim(Type.SLIDE, fromRow, fromCol, toRow, toCol, value, 120));
    }

    public void addPop(int row, int col, int value) {
        animations.add(new Anim(Type.POP, row, col, value, 180));
    }

    public void addMerge(int row, int col, int value) {
        animations.add(new Anim(Type.MERGE, row, col, value, 150));
    }

    public List<Anim> getAnimations() {
        return Collections.unmodifiableList(animations);
    }

    public int animCount() {
        return animations.size();
    }

    /** 启动动画循环。动画结束后或列表为空时调用 onFinish (在 EDT 上)。 */
    public void start(Runnable onFinish) {
        this.onFinish = onFinish;
        if (animations.isEmpty()) {
            if (onFinish != null) onFinish.run();
            return;
        }
        running = true;
        final long now = System.currentTimeMillis();
        for (Anim a : animations) a.startTime = now;

        timer = new Timer(16, (ActionEvent e) -> {
            boolean allDone = true;
            for (Anim a : animations) {
                if (a.getProgress() < 1.0f) { allDone = false; break; }
            }
            if (allDone) {
                timer.stop();
                running = false;
                if (this.onFinish != null) this.onFinish.run();
            }
        });
        timer.setRepeats(true);
        timer.start();
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
        onFinish = null;
    }
}
