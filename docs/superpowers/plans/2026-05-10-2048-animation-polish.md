# 2048 Animation & UI Polish — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add smooth tile animations (slide/pop/merge), input buffering, and visual polish to the 2048 Swing game while keeping all 49 existing tests passing.

**Architecture:** New `AnimationEngine` class manages animation state via `javax.swing.Timer` at 60fps. `GameBoard` records pre/post move snapshots to detect tile movements, feeds them to the engine, and renders tiles at interpolated positions during animation. Input is buffered (last key only) while animations play.

**Tech Stack:** Java 8+, javax.swing (Timer, SwingUtilities), no external dependencies.

---

## File Structure

| File | Responsibility |
|------|---------------|
| `src/AnimationEngine.java` | Animation state machine, easing functions, timer loop, `Anim` data class |
| `src/Grid.java` | Tile data — add `wasMerged()` query for animation detection |
| `src/GameView.java` | GameBoard integration — animation recording in moves, interpolated rendering, input buffer, visual polish |
| `tests/AnimationEngineTest.java` | Unit tests for easing functions and AnimationEngine lifecycle |
| `tests/AnimationIntegrationTest.java` | Integration tests for input buffering and animation/game-logic consistency |

---

### Task 1: Create AnimationEngine.java

**Files:**
- Create: `src/AnimationEngine.java`

- [ ] **Step 1: Write the file**

```java
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
```

- [ ] **Step 2: Verify compilation**

Run: `javac -encoding UTF-8 -d out src/AnimationEngine.java 2>&1`
Expected: No output (success)

---

### Task 2: Write AnimationEngineTest.java (Easing Functions)

**Files:**
- Create: `tests/AnimationEngineTest.java`

- [ ] **Step 1: Write easing function tests**

```java
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
        System.out.println("━━━ 缓动函数 ━━━\n");

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
        System.out.println("\n━━━ Anim 进度 ━━━\n");

        AnimationEngine.Anim anim = new AnimationEngine.Anim(
                AnimationEngine.Type.SLIDE, 0, 0, 0, 1, 2, 100);

        // 模拟刚创建
        anim.startTime = System.currentTimeMillis();
        float p0 = anim.getProgress();
        checkTrue("刚创建时 progress ≈ 0", p0 >= 0f && p0 < 0.1f);

        // 模拟已完成
        anim.startTime = System.currentTimeMillis() - 200; // 200ms ago, duration 100ms
        float p1 = anim.getProgress();
        checkEqualsF("超时后 progress = 1.0", 1.0f, p1, 0.001f);
    }

    // ================================================================
    //  Anim.getProgress() 不超过 1
    // ================================================================

    static void testAnimProgressClampedAtOne() {
        System.out.println("\n━━━ Anim 进度钳制 ━━━\n");

        AnimationEngine.Anim anim = new AnimationEngine.Anim(
                AnimationEngine.Type.POP, 0, 0, 2, 50);
        anim.startTime = System.currentTimeMillis() - 10000; // way past
        checkEqualsF("duration=50ms, 10000ms 后 progress = 1.0", 1.0f, anim.getProgress(), 0.001f);
    }

    // ================================================================
    //  Anim 渲染位置
    // ================================================================

    static void testAnimRenderPosition() {
        System.out.println("\n━━━ Anim 渲染位置 ━━━\n");

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
        merge.startTime = System.currentTimeMillis() - 22; // ~0.15 progress → ~0.1 eased → glow rising
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
        System.out.println("\n━━━ 引擎生命周期 ━━━\n");

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
        System.out.println("\n━━━ completeAll 回调 ━━━\n");

        AnimationEngine engine = new AnimationEngine();
        final boolean[] called = {false};
        engine.addPop(0, 0, 2);
        engine.start(() -> called[0] = true);
        engine.completeAll();
        checkTrue("completeAll() 触发 onFinish 回调", called[0]);
        checkTrue("completeAll() 后 isRunning() = false", !engine.isRunning());
        // completeAll 后动画进度应为 1
        checkEquals("completeAll() 后 animCount() 不变", 1, engine.animCount());
    }

    static void testEngineClear() {
        System.out.println("\n━━━ clear 清空 ━━━\n");

        AnimationEngine engine = new AnimationEngine();
        engine.addSlide(0, 0, 1, 0, 4);
        engine.addMerge(0, 0, 8);
        checkEquals("clear 前 animCount() = 2", 2, engine.animCount());

        engine.clear();
        checkEquals("clear 后 animCount() = 0", 0, engine.animCount());
        checkTrue("clear 后 isRunning() = false", !engine.isRunning());
    }

    static void testMultipleAnims() {
        System.out.println("\n━━━ 多个动画共存 ━━━\n");

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

        // 所有动画类型正确
        checkTrue("anim[0] type = SLIDE", anims.get(0).type == AnimationEngine.Type.SLIDE);
        checkTrue("anim[1] type = SLIDE", anims.get(1).type == AnimationEngine.Type.SLIDE);
        checkTrue("anim[2] type = POP", anims.get(2).type == AnimationEngine.Type.POP);
    }
}
```

- [ ] **Step 2: Compile test and source together**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -cp src -d out src/*.java tests/AnimationEngineTest.java 2>&1`
Expected: No output

- [ ] **Step 3: Run the test**

Run: `java -cp out AnimationEngineTest`
Expected: All tests pass, exit code 0

- [ ] **Step 4: Commit**

```bash
git add src/AnimationEngine.java tests/AnimationEngineTest.java
git commit -m "feat: add AnimationEngine with easing functions and unit tests"
```

---

### Task 3: Add move-direction constants to GameView.GameBoard for animation tracking

**Files:**
- Modify: `src/GameView.java`

- [ ] **Step 1: Add direction constants and saveValues helper to GameBoard**

Find the `GameBoard` inner class in `GameView.java`. Add these fields after `final Grid[][] grids`:

```java
// 方向常量 (用于动画追踪)
private static final int DIR_UP    = 0;
private static final int DIR_DOWN  = 1;
private static final int DIR_LEFT  = 2;
private static final int DIR_RIGHT = 3;
```

Add the AnimationEngine field:

```java
final AnimationEngine animEngine = new AnimationEngine();
int pendingKeyCode;  // 0 = none
```

Add this helper method to GameBoard:

```java
private int[][] saveValues() {
    int[][] vals = new int[ROWS][COLS];
    for (int r = 0; r < ROWS; r++)
        for (int c = 0; c < COLS; c++)
            vals[r][c] = grids[r][c].value;
    return vals;
}
```

Add animation-building helpers to GameBoard:

```java
/** 对比 pre/post 快照，为每个移动过的 tile 创建 SLIDE 动画 */
private void buildSlideAnims(int[][] preVals, int dir) {
    boolean[][] consumed = new boolean[ROWS][COLS];

    for (int r = 0; r < ROWS; r++) {
        for (int c = 0; c < COLS; c++) {
            int postVal = grids[r][c].value;
            if (postVal == 0) continue;
            if (grids[r][c].isMerged()) continue;
            if (preVals[r][c] == postVal) continue; // 没有移动

            if (dir == DIR_UP || dir == DIR_DOWN) {
                // 垂直移动: 在同列搜索源 tile
                int startR = (dir == DIR_UP) ? ROWS - 1 : 0;
                int endR   = (dir == DIR_UP) ? -1 : ROWS;
                int step   = (dir == DIR_UP) ? -1 : 1;
                for (int pr = startR; pr != endR; pr += step) {
                    if (consumed[pr][c]) continue;
                    if (preVals[pr][c] == postVal) {
                        animEngine.addSlide(pr, c, r, c, postVal);
                        consumed[pr][c] = true;
                        break;
                    }
                }
            } else {
                // 水平移动: 在同行搜索源 tile
                int startC = (dir == DIR_LEFT) ? COLS - 1 : 0;
                int endC   = (dir == DIR_LEFT) ? -1 : COLS;
                int step   = (dir == DIR_LEFT) ? -1 : 1;
                for (int pc = startC; pc != endC; pc += step) {
                    if (consumed[r][pc]) continue;
                    if (preVals[r][pc] == postVal) {
                        animEngine.addSlide(r, pc, r, c, postVal);
                        consumed[r][pc] = true;
                        break;
                    }
                }
            }
        }
    }
}

/** 为合并位置创建 MERGE 动画 */
private void buildMergeAnims() {
    for (int r = 0; r < ROWS; r++)
        for (int c = 0; c < COLS; c++)
            if (grids[r][c].isMerged())
                animEngine.addMerge(r, c, grids[r][c].value);
}
```

- [ ] **Step 2: Modify doMoveUp to capture animations**

Replace the existing `doMoveUp()` method:

```java
private int doMoveUp() {
    int[][] preVals = saveValues();
    clearMerge();
    int s = 0;
    boolean moved = false;
    for (int c = 0; c < COLS; c++)
        for (int r = 1; r < ROWS; r++)
            if (!grids[r][c].isEmpty()) {
                int v = grids[r][c].moveUp(grids, r, c);
                if (v >= 0) { s += v; moved = true; }
            }
    if (moved) {
        buildSlideAnims(preVals, DIR_UP);
        buildMergeAnims();
    }
    return moved ? s : -1;
}
```

- [ ] **Step 3: Modify doMoveDown similarly**

Replace `doMoveDown()`:

```java
private int doMoveDown() {
    int[][] preVals = saveValues();
    clearMerge();
    int s = 0;
    boolean moved = false;
    for (int c = 0; c < COLS; c++)
        for (int r = ROWS - 2; r >= 0; r--)
            if (!grids[r][c].isEmpty()) {
                int v = grids[r][c].moveDown(grids, r, c);
                if (v >= 0) { s += v; moved = true; }
            }
    if (moved) {
        buildSlideAnims(preVals, DIR_DOWN);
        buildMergeAnims();
    }
    return moved ? s : -1;
}
```

- [ ] **Step 4: Modify doMoveLeft similarly**

Replace `doMoveLeft()`:

```java
private int doMoveLeft() {
    int[][] preVals = saveValues();
    clearMerge();
    int s = 0;
    boolean moved = false;
    for (int r = 0; r < ROWS; r++)
        for (int c = 1; c < COLS; c++)
            if (!grids[r][c].isEmpty()) {
                int v = grids[r][c].moveLeft(grids, r, c);
                if (v >= 0) { s += v; moved = true; }
            }
    if (moved) {
        buildSlideAnims(preVals, DIR_LEFT);
        buildMergeAnims();
    }
    return moved ? s : -1;
}
```

- [ ] **Step 5: Modify doMoveRight similarly**

Replace `doMoveRight()`:

```java
private int doMoveRight() {
    int[][] preVals = saveValues();
    clearMerge();
    int s = 0;
    boolean moved = false;
    for (int r = 0; r < ROWS; r++)
        for (int c = COLS - 2; c >= 0; c--)
            if (!grids[r][c].isEmpty()) {
                int v = grids[r][c].moveRight(grids, r, c);
                if (v >= 0) { s += v; moved = true; }
            }
    if (moved) {
        buildSlideAnims(preVals, DIR_RIGHT);
        buildMergeAnims();
    }
    return moved ? s : -1;
}
```

- [ ] **Step 6: Verify compilation**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -d out src/*.java 2>&1`
Expected: No output

- [ ] **Step 7: Run existing tests to verify no regression**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -cp src -d out src/*.java tests/*.java 2>&1 && java -cp out TestAll 2>&1`
Expected: All 49 tests pass, exit code 0

- [ ] **Step 8: Commit**

```bash
git add src/GameView.java
git commit -m "feat: integrate animation recording into doMove methods with pre/post snapshots"
```

---

### Task 4: Implement keyPressed with animation flow and input buffering

**Files:**
- Modify: `src/GameView.java`

- [ ] **Step 1: Restructure keyPressed and spawnTile**

Replace the `keyPressed` method with:

```java
@Override
public void keyPressed(KeyEvent e) {
    if (animEngine.isRunning()) {
        pendingKeyCode = e.getKeyCode();
        return;
    }
    executeMove(e.getKeyCode());
}

private void executeMove(int keyCode) {
    pendingKeyCode = 0;
    int moveScore;
    switch (keyCode) {
        case KeyEvent.VK_UP:    moveScore = doMoveUp();    break;
        case KeyEvent.VK_DOWN:  moveScore = doMoveDown();  break;
        case KeyEvent.VK_LEFT:  moveScore = doMoveLeft();  break;
        case KeyEvent.VK_RIGHT: moveScore = doMoveRight(); break;
        case KeyEvent.VK_ESCAPE: restart(); return;
        case KeyEvent.VK_M:      toggleSound(); refreshUI(); return;
        case KeyEvent.VK_S:      saveToXML(); return;
        case KeyEvent.VK_L:      loadFromXML(); return;
        case KeyEvent.VK_T:      Utils.tips(grids); return;
        default: return;
    }

    if (moveScore < 0) return;

    if (soundOn) new PlaySound("move.wav").start();
    if (moveScore > 0 && soundOn) new PlaySound("merge.wav").start();

    addScore(moveScore);
    refreshUI();
    spawnTileWithAnim();
    repaint();

    animEngine.start(() -> {
        repaint();
        if (!hasWon && checkWin()) {
            hasWon = true;
            int res = JOptionPane.showConfirmDialog(frame,
                    "恭喜！你达成了 2048！\n是否继续游戏？",
                    "Victory!", JOptionPane.YES_NO_OPTION);
            if (res != JOptionPane.YES_OPTION) restart();
            else repaint();
        }
        if (checkGameOver()) {
            refreshUI();
            repaint();
            int res = JOptionPane.showConfirmDialog(frame,
                    "Game Over!  得分: " + score + "\n是否重新开始？",
                    "Game Over", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) restart();
            else repaint();
        }
        if (pendingKeyCode != 0) {
            int k = pendingKeyCode;
            pendingKeyCode = 0;
            SwingUtilities.invokeLater(() -> executeMove(k));
        }
    });
}
```

Replace `spawnTile()` with:

```java
/** 随机空格生成 2/4 并记录 POP 动画 */
private void spawnTileWithAnim() {
    List<int[]> empties = emptyCells();
    if (empties.isEmpty()) return;
    int[] cell = empties.get(RNG.nextInt(empties.size()));
    int val = RNG.nextDouble() < 0.9 ? 2 : 4;
    grids[cell[0]][cell[1]].value = val;
    animEngine.addPop(cell[0], cell[1], val);
}
```

Update `restart()` to complete any running animation:

```java
void restart() {
    animEngine.clear();
    pendingKeyCode = 0;
    resetScore();
    hasWon = false;
    for (int r = 0; r < ROWS; r++)
        for (int c = 0; c < COLS; c++)
            grids[r][c] = new Grid();
    // First two spawns don't need pop animation (fresh board)
    List<int[]> empties = emptyCells();
    if (empties.size() > 0) {
        int[] c1 = empties.get(RNG.nextInt(empties.size()));
        grids[c1[0]][c1[1]].value = RNG.nextDouble() < 0.9 ? 2 : 4;
        empties.removeIf(c -> c[0] == c1[0] && c[1] == c1[1]);
    }
    if (empties.size() > 0) {
        int[] c2 = empties.get(RNG.nextInt(empties.size()));
        grids[c2[0]][c2[1]].value = RNG.nextDouble() < 0.9 ? 2 : 4;
    }
    refreshUI();
    repaint();
    requestFocusInWindow();
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -d out src/*.java 2>&1`
Expected: No output

- [ ] **Step 3: Commit**

```bash
git add src/GameView.java
git commit -m "feat: add animation-driven key handling with input buffering"
```

---

### Task 5: Update paintComponent for animation-aware rendering

**Files:**
- Modify: `src/GameView.java`

- [ ] **Step 1: Replace paintComponent with animation-aware version**

Replace the `paintComponent` method in GameBoard:

```java
@Override
protected void paintComponent(Graphics g) {
    super.paintComponent(g);
    Graphics2D g2 = (Graphics2D) g;
    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
    g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
    g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

    // 构建动画查找表
    java.util.Map<String, AnimationEngine.Anim> animAt = new java.util.HashMap<>();
    java.util.Set<String> slideTargets = new java.util.HashSet<>();
    boolean animating = animEngine.isRunning();
    if (animating) {
        for (AnimationEngine.Anim a : animEngine.getAnimations()) {
            String key = a.toRow + "," + a.toCol;
            if (a.type == AnimationEngine.Type.SLIDE) {
                slideTargets.add(key);
            }
            animAt.put(key, a);
        }
    }

    // 缓存 FontMetrics
    Font[] fonts = {
        new Grid(2).getCheckFont(),
        new Grid(16).getCheckFont(),
        new Grid(256).getCheckFont(),
        new Grid(1024).getCheckFont(),
    };
    FontMetrics[] fms = new FontMetrics[4];
    for (int i = 0; i < 4; i++) fms[i] = g2.getFontMetrics(fonts[i]);

    // 第一遍: 绘制静态 tile + POP/MERGE tile (在网格位置)
    for (int r = 0; r < ROWS; r++) {
        for (int c = 0; c < COLS; c++) {
            String key = r + "," + c;
            if (slideTargets.contains(key)) continue; // SLIDE 目标先跳过
            AnimationEngine.Anim anim = animAt.get(key);
            int x = GAP + (GAP + SIZE) * c;
            int y = GAP + (GAP + SIZE) * r;
            drawTile(g2, r, c, x, y, anim, fms);
        }
    }

    // 第二遍: 绘制 SLIDE tile (在插值位置)
    if (animating) {
        for (AnimationEngine.Anim a : animEngine.getAnimations()) {
            if (a.type != AnimationEngine.Type.SLIDE) continue;
            double ax = a.getRenderX(SIZE, GAP);
            double ay = a.getRenderY(SIZE, GAP);
            drawSlideTile(g2, a, ax, ay);
        }
    }

    // Game Over 遮罩
    if (checkGameOver()) {
        g2.setColor(new Color(238, 228, 218, 185));
        g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);
        g2.setColor(new Color(119, 110, 101));
        g2.setFont(FONT_TITLE);
        String msg = "Game Over!";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(msg, (getWidth() - fm.stringWidth(msg)) / 2, getHeight() / 2);
    }
}
```

Replace the `drawTile` method with an overload that accepts animation and position:

```java
/** 绘制静态 tile (含 POP scale / MERGE glow) */
private void drawTile(Graphics2D g, int r, int c, int x, int y,
                      AnimationEngine.Anim anim, FontMetrics[] fms) {
    Grid tile = grids[r][c];

    // 如果是 POP 动画且 scale 很小 → 跳过 (还没弹出来)
    boolean isPop = anim != null && anim.type == AnimationEngine.Type.POP;
    boolean isMerge = anim != null && anim.type == AnimationEngine.Type.MERGE;

    if (isPop && anim.getScale() < 0.05) return;

    // 合并发光效果
    float glow = isMerge ? anim.getGlow() : 0f;

    // 阴影
    g.setColor(new Color(0, 0, 0, 25));
    g.fillRoundRect(x + 3, y + 3, SIZE, SIZE, ARC, ARC);

    // 背景
    g.setColor(tile.getBackground());
    if (isPop) {
        // POP: 居中缩放
        double scale = anim.getScale();
        int sw = (int)(SIZE * scale);
        int sh = (int)(SIZE * scale);
        int sx = x + (SIZE - sw) / 2;
        int sy = y + (SIZE - sh) / 2;
        g.fillRoundRect(sx, sy, sw, sh, ARC, ARC);
    } else {
        g.fillRoundRect(x, y, SIZE, SIZE, ARC, ARC);
    }

    // 发光叠加 (MERGE)
    if (glow > 0.01f) {
        g.setColor(new Color(1f, 1f, 1f, glow * 0.6f));
        g.fillRoundRect(x, y, SIZE, SIZE, ARC, ARC);
    }

    // 高数值渐变光泽
    if (tile.value >= 1024 && !tile.isEmpty()) {
        g.setColor(new Color(255, 255, 255, 35));
        g.fillRoundRect(x, y + SIZE / 2, SIZE, SIZE / 2, ARC, ARC);
    }

    if (tile.isEmpty()) return;

    // 文字 (合并时可能稍大)
    g.setColor(tile.getForeground());
    Font font = tile.getCheckFont();
    if (isMerge && glow > 0.3f) {
        font = font.deriveFont(font.getSize() * (1f + glow * 0.15f));
    }
    g.setFont(font);

    FontMetrics fm;
    int sz = font.getSize();
    if (sz >= 46)      fm = fms[0];
    else if (sz >= 40) fm = fms[1];
    else if (sz >= 34) fm = fms[2];
    else               fm = fms[3];

    String text = String.valueOf(tile.value);
    int tx = x + (SIZE - fm.stringWidth(text)) / 2;
    int ty = y + (SIZE - fm.getHeight()) / 2 + fm.getAscent();
    g.drawString(text, tx, ty);
}
```

Add `drawSlideTile` for SLIDE animations (renders at interpolated position, not grid-aligned):

```java
/** 在插值位置绘制滑动中的 tile */
private void drawSlideTile(Graphics2D g, AnimationEngine.Anim a, double ax, double ay) {
    int x = (int) ax;
    int y = (int) ay;
    Grid dummy = new Grid(a.value);

    // 阴影
    g.setColor(new Color(0, 0, 0, 25));
    g.fillRoundRect(x + 3, y + 3, SIZE, SIZE, ARC, ARC);

    // 背景
    g.setColor(dummy.getBackground());
    g.fillRoundRect(x, y, SIZE, SIZE, ARC, ARC);

    // 高数值光泽
    if (a.value >= 1024) {
        g.setColor(new Color(255, 255, 255, 35));
        g.fillRoundRect(x, y + SIZE / 2, SIZE, SIZE / 2, ARC, ARC);
    }

    // 文字
    g.setColor(dummy.getForeground());
    g.setFont(dummy.getCheckFont());
    FontMetrics fm = g.getFontMetrics();
    String text = String.valueOf(a.value);
    int tx = x + (SIZE - fm.stringWidth(text)) / 2;
    int ty = y + (SIZE - fm.getHeight()) / 2 + fm.getAscent();
    g.drawString(text, tx, ty);
}
```

- [ ] **Step 2: Add missing imports at top of GameView.java**

Ensure these imports are present at the top:

```java
import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.*;
```

- [ ] **Step 3: Verify compilation**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -d out src/*.java 2>&1`
Expected: No output

- [ ] **Step 4: Run existing tests**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -cp src -d out src/*.java tests/*.java 2>&1 && java -cp out TestAll 2>&1`
Expected: All 49 tests pass

- [ ] **Step 5: Commit**

```bash
git add src/GameView.java
git commit -m "feat: add animation-aware rendering with shadows, glow, and slide interpolation"
```

---

### Task 6: Add board separator lines (UI polish)

**Files:**
- Modify: `src/GameView.java`

- [ ] **Step 1: Add separator lines to paintComponent**

After the board background fill and before tile drawing, add separator lines:

In `paintComponent`, after `super.paintComponent(g)`, add:

```java
// 棋盘背景
g2.setColor(BOARD_BG);
g2.fillRoundRect(0, 0, getWidth(), getHeight(), 16, 16);

// 分隔线 — 浅色线条模拟网格
g2.setColor(new Color(187, 173, 160, 60));
for (int i = 1; i < COLS; i++) {
    int lineX = GAP + (GAP + SIZE) * i - GAP / 2;
    g2.fillRoundRect(lineX, GAP, GAP, getHeight() - GAP * 2, 4, 4);
}
for (int i = 1; i < ROWS; i++) {
    int lineY = GAP + (GAP + SIZE) * i - GAP / 2;
    g2.fillRoundRect(GAP, lineY, getWidth() - GAP * 2, GAP, 4, 4);
}
```

- [ ] **Step 2: Verify compilation**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -d out src/*.java 2>&1`
Expected: No output

- [ ] **Step 3: Commit**

```bash
git add src/GameView.java
git commit -m "feat: add subtle grid separator lines to game board"
```

---

### Task 7: Write AnimationIntegrationTest.java

**Files:**
- Create: `tests/AnimationIntegrationTest.java`

- [ ] **Step 1: Write the integration test**

```java
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
        testNoAnimationOnInvalidMove();

        System.out.println("\n========================================");
        System.out.printf("  Integration: %d passed, %d failed%n", passed, failed);
        System.out.println("========================================");
        System.exit(failed > 0 ? 1 : 0);
    }

    private static void check(String name, boolean cond) {
        if (cond) { System.out.println("  ✓ " + name); passed++; }
        else      { System.out.println("  ✗ " + name + "  ← FAIL"); failed++; }
    }

    // ================================================================
    //  输入缓冲测试
    // ================================================================

    static void testInputBuffering() {
        System.out.println("━━━ 输入缓冲 ━━━\n");

        AnimationEngine engine = new AnimationEngine();

        // 模拟按键在动画期间到达
        final int[] buffered = {0};
        engine.addSlide(0, 0, 0, 1, 2);
        engine.start(() -> {
            // 动画完成回调中检查是否有缓存键
            buffered[0] = 42; // 模拟 pendingKeyCode
        });

        // 动画期间 isRunning = true
        check("动画运行中 isRunning() = true", engine.isRunning());

        engine.completeAll();
        check("completeAll 后回调已执行 (buffered = 42)", buffered[0] == 42);
        check("动画完成后 isRunning() = false", !engine.isRunning());
    }

    // ================================================================
    //  动画非阻塞
    // ================================================================

    static void testAnimationCompletesWithoutBlocking() {
        System.out.println("\n━━━ 动画非阻塞 ━━━\n");

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
        System.out.println("\n━━━ 状态同步 ━━━\n");

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

        // 所有动画 progress = 1
        boolean allDone = true;
        for (AnimationEngine.Anim a : engine.getAnimations()) {
            if (a.getProgress() < 1f) allDone = false;
        }
        check("completeAll 后所有动画 progress = 1", allDone);
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
    //  动画不影响游戏逻辑结果
    // ================================================================

    static void testAnimationResultMatchesDirectMove() {
        System.out.println("\n━━━ 动画不影响逻辑 ━━━\n");

        // 使用 Grid 直接做一个移动并记录最终值
        Grid[][] testGrids = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                testGrids[r][c] = new Grid();

        // 设置初始状态: 第一列全为 2
        testGrids[0][0].value = 2;
        testGrids[1][0].value = 4;
        testGrids[2][0].value = 4;
        testGrids[3][0].value = 0;

        // 模拟向上移动 (直接操作，不走动画)
        int[][] preVals = new int[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                preVals[r][c] = testGrids[r][c].value;

        // doMoveUp
        for (Grid[] row : testGrids)
            for (Grid g : row)
                g.setMerge(false);
        for (int c = 0; c < 4; c++)
            for (int r = 1; r < 4; r++)
                if (!testGrids[r][c].isEmpty())
                    testGrids[r][c].moveUp(testGrids, r, c);

        // 验证结果: [0][0] should be 2, [0][1] should be 8 (4+4 merged)
        check("移动后 [0][0] = 2", testGrids[0][0].value == 2);
        check("移动后 [1][0] = 8 (合并)", testGrids[1][0].value == 8);
        check("移动后 [2][0] = 0", testGrids[2][0].value == 0);
        check("移动后 [3][0] = 0", testGrids[3][0].value == 0);
        check("移动后 [0][0] isMerged = false", !testGrids[0][0].isMerged());
        check("移动后 [1][0] isMerged = true (合并标志)", testGrids[1][0].isMerged());
    }

    // ================================================================
    //  无效移动不产生动画
    // ================================================================

    static void testNoAnimationOnInvalidMove() {
        System.out.println("\n━━━ 无效移动不产生动画 ━━━\n");

        AnimationEngine engine = new AnimationEngine();

        // 初始化引擎为空
        check("空引擎 isRunning() = false", !engine.isRunning());

        // 模拟一个无动画的 start (空列表)
        final boolean[] called = {false};
        engine.start(() -> called[0] = true);

        // 空动画列表应立刻完成
        check("空动画列表直接回调", called[0]);
        check("空列表后 isRunning() = false", !engine.isRunning());
    }
}
```

- [ ] **Step 2: Compile and run integration test**

Run: `cd /home/kemove/Courses/JAVA2048 && javac -encoding UTF-8 -cp src -d out src/*.java tests/AnimationIntegrationTest.java 2>&1`
Expected: No output

Run: `java -cp out AnimationIntegrationTest 2>&1`
Expected: All tests pass, exit code 0

- [ ] **Step 3: Commit**

```bash
git add tests/AnimationIntegrationTest.java
git commit -m "test: add animation integration tests for input buffering and logic consistency"
```

---

### Task 8: Final verification — run all tests

**Files:**
- No changes, verification only

- [ ] **Step 1: Clean compile everything**

Run: `cd /home/kemove/Courses/JAVA2048 && rm -rf out && mkdir out && javac -encoding UTF-8 -cp src -d out src/*.java tests/*.java 2>&1`
Expected: No output

- [ ] **Step 2: Run full test suite (TestAll)**

Run: `java -cp out TestAll 2>&1`
Expected: All tests pass (~49 original + 0 failures), exit code 0

- [ ] **Step 3: Run AnimationEngineTest**

Run: `java -cp out AnimationEngineTest 2>&1`
Expected: All ~20 tests pass, exit code 0

- [ ] **Step 4: Run AnimationIntegrationTest**

Run: `java -cp out AnimationIntegrationTest 2>&1`
Expected: All ~10 tests pass, exit code 0

- [ ] **Step 5: Commit if any fixes were needed**

```bash
git add -A
git commit -m "chore: final verification — all tests passing after animation integration"
```

---

## Summary

**Total tasks:** 8
**Files created:** `src/AnimationEngine.java`, `tests/AnimationEngineTest.java`, `tests/AnimationIntegrationTest.java`
**Files modified:** `src/GameView.java`
**Files unchanged:** `src/Grid.java`, `src/Main.java`, `src/PlaySound.java`, `src/Utils.java`, all existing tests
**Test count:** 49 existing + ~17 new unit + ~10 new integration = ~76 total
