import java.awt.Color;
import java.awt.Font;

/**
 * Grid 类单元测试 — 验证格子创建、颜色、字体、递归移动与合并逻辑
 *
 * 用法:
 *   java -cp out:src GridTest          # 单独运行
 *   TestAll.main() 通过 runAll() 汇总  # 由测试套件调用
 */
public class GridTest {

    public static int passed = 0, failed = 0;

    public static void main(String[] args) {
        runAll();
        if (failed > 0) System.exit(1);
    }

    /** 供 TestAll 调用的入口，不触发 System.exit */
    public static void runAll() {
        passed = 0; failed = 0;
        System.out.println("========================================");
        System.out.println("  Grid 单元测试");
        System.out.println("========================================\n");

        testConstructor();
        testCopy();
        testIsEmpty();
        testGetForeground();
        testGetBackground();
        testGetCheckFont();
        testMergeFlag();
        testMoveUpBasic();
        testMoveDownBasic();
        testMoveLeftBasic();
        testMoveRightBasic();
        testMoveUpNoMergeWhenMerged();
        testRecursiveMoveChaining();
        testMoveIntoEmptyChain();
        testNoMoveWhenBlocked();

        System.out.println("\n========================================");
        System.out.printf("  Grid: %d passed, %d failed%n", passed, failed);
        System.out.println("========================================");
    }

    // ---- helpers ----

    static Grid[][] emptyBoard() {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c] = new Grid(0);
        return g;
    }

    static void clearMerge(Grid[][] g) {
        for (Grid[] row : g)
            for (Grid t : row)
                t.setMerge(false);
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ✓ " + name); }
        else      { failed++; System.out.println("  ✗ " + name + "  <-- FAILED"); }
    }

    // ---- 测试用例 ----

    static void testConstructor() {
        System.out.println("[构造]");
        Grid g0 = new Grid();
        check("default value = 0", g0.getValue() == 0);
        Grid g2 = new Grid(2);
        check("value 2 constructor", g2.getValue() == 2);
        Grid g2048 = new Grid(2048);
        check("value 2048 constructor", g2048.getValue() == 2048);
    }

    static void testCopy() {
        System.out.println("[深拷贝]");
        Grid g = new Grid(64);
        Grid copy = g.copy();
        check("copy same value", copy.getValue() == 64);
        copy.setValue(128);
        check("original unchanged after copy mutation", g.getValue() == 64);
    }

    static void testIsEmpty() {
        System.out.println("[isEmpty]");
        check("new Grid() isEmpty", new Grid().isEmpty());
        check("Grid(2) not isEmpty", !new Grid(2).isEmpty());
        check("Grid(0) isEmpty", new Grid(0).isEmpty());
    }

    static void testGetForeground() {
        System.out.println("[前景色]");
        check("0 foreground = BG color", new Grid(0).getForeground().equals(new Color(0xcdc1b4)));
        check("2 foreground = BLACK", new Grid(2).getForeground().equals(Color.BLACK));
        check("4 foreground = BLACK", new Grid(4).getForeground().equals(Color.BLACK));
        check("8 foreground = WHITE", new Grid(8).getForeground().equals(Color.WHITE));
        check("2048 foreground = WHITE", new Grid(2048).getForeground().equals(Color.WHITE));
    }

    static void testGetBackground() {
        System.out.println("[背景色]");
        check("0   bg = 0xcdc1b4", new Grid(0).getBackground().equals(new Color(0xcdc1b4)));
        check("2   bg = 0xeee4da", new Grid(2).getBackground().equals(new Color(0xeee4da)));
        check("4   bg = 0xede0c8", new Grid(4).getBackground().equals(new Color(0xede0c8)));
        check("8   bg = 0xf2b179", new Grid(8).getBackground().equals(new Color(0xf2b179)));
        check("2048 bg = 0xedc22e", new Grid(2048).getBackground().equals(new Color(0xedc22e)));
        check("4096 bg = 0x248c51 (super)", new Grid(4096).getBackground().equals(new Color(0x248c51)));
    }

    static void testGetCheckFont() {
        System.out.println("[字体大小]");
        Font f1 = new Grid(2).getCheckFont();
        Font f2 = new Grid(16).getCheckFont();
        Font f3 = new Grid(256).getCheckFont();
        Font f4 = new Grid(1024).getCheckFont();
        check("1-digit uses size 46", f1.getSize() == 46);
        check("2-digit uses size 40", f2.getSize() == 40);
        check("3-digit uses size 34", f3.getSize() == 34);
        check("4-digit uses size 28", f4.getSize() == 28);
    }

    static void testMergeFlag() {
        System.out.println("[合并标志]");
        Grid g = new Grid(2);
        check("initial merge = false", !g.isMerged());
        g.setMerge(true);
        check("after setMerge(true)", g.isMerged());
    }

    // ---- 移动测试 ----

    static void testMoveUpBasic() {
        System.out.println("[moveUp - 基本合并]");
        Grid[][] g = emptyBoard();
        g[1][0].value = 2;
        g[2][0].value = 2;
        clearMerge(g);

        int score = g[2][0].moveUp(g, 2, 0);
        check("2+2 merge score = 4", score == 4);
        check("merged cell = 4", g[1][0].value == 4);
        check("original cell = 0", g[2][0].value == 0);
    }

    static void testMoveDownBasic() {
        System.out.println("[moveDown - 基本合并]");
        Grid[][] g = emptyBoard();
        g[0][3].value = 4;
        g[1][3].value = 4;
        clearMerge(g);

        int score = g[0][3].moveDown(g, 0, 3);
        check("4+4 merge score = 8", score == 8);
        check("merged cell = 8", g[1][3].value == 8);
        check("original cell = 0", g[0][3].value == 0);
    }

    static void testMoveLeftBasic() {
        System.out.println("[moveLeft - 基本合并]");
        Grid[][] g = emptyBoard();
        g[0][1].value = 2;
        g[0][2].value = 2;
        clearMerge(g);

        int score = g[0][2].moveLeft(g, 0, 2);
        check("2+2 merge score = 4", score == 4);
        check("merged cell = 4", g[0][1].value == 4);
    }

    static void testMoveRightBasic() {
        System.out.println("[moveRight - 基本合并]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;
        g[0][1].value = 2;
        clearMerge(g);

        int score = g[0][0].moveRight(g, 0, 0);
        check("2+2 merge score = 4", score == 4);
        check("merged cell = 4", g[0][1].value == 4);
        check("original cell = 0", g[0][0].value == 0);
    }

    static void testMoveUpNoMergeWhenMerged() {
        System.out.println("[moveUp - 已合并标记阻止二次合并]");
        Grid[][] g = emptyBoard();
        g[1][0].value = 4;
        g[2][0].value = 4;
        g[1][0].setMerge(true);
        // g[1][0] 已经 merge=true, 不会再次合并
        int score = g[2][0].moveUp(g, 2, 0);
        check("no merge when target already merged", score == 0);
    }

    static void testRecursiveMoveChaining() {
        System.out.println("[递归 - 连环移动]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;
        g[1][0].value = 2;
        g[2][0].value = 2;
        g[3][0].value = 2;
        clearMerge(g);

        int s1 = g[1][0].moveUp(g, 1, 0);
        // (1,0) 2 合并到 (0,0) → 4
        check("first merge: 2+2 = 4", s1 == 4);
        check("cell (0,0) = 4 after first merge", g[0][0].value == 4);
        // 不会连环合并: 剩下的 2 不会被合并到 4
    }

    static void testMoveIntoEmptyChain() {
        System.out.println("[递归 - 穿越空格链]");
        Grid[][] g = emptyBoard();
        g[3][0].value = 2;
        clearMerge(g);

        g[3][0].moveUp(g, 3, 0);
        check("slide to empty top row", g[0][0].value == 2);
        check("original position empty", g[3][0].value == 0);
    }

    static void testNoMoveWhenBlocked() {
        System.out.println("[边界 - 不同值不合并]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;
        g[1][0].value = 8;
        clearMerge(g);

        int score = g[1][0].moveUp(g, 1, 0);
        check("2 and 8 do not merge", score == 0);
        check("both values unchanged", g[0][0].value == 2 && g[1][0].value == 8);
    }
}
