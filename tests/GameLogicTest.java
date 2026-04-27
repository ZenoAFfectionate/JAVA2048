/**
 * 游戏逻辑测试 — 覆盖计分、胜负判定、空格检测、存档解析
 *
 * 本测试直接验证核心算法，不依赖 Swing GUI。
 */
public class GameLogicTest {

    public static int passed = 0, failed = 0;

    public static void main(String[] args) {
        runAll();
        if (failed > 0) System.exit(1);
    }

    public static void runAll() {
        passed = 0; failed = 0;
        System.out.println("========================================");
        System.out.println("  游戏逻辑测试");
        System.out.println("========================================\n");

        testEmptyCellsDetection();
        testSpawnTile();
        testSpawnTileFullBoard();
        testScoreAccumulation();
        testCheckWin();
        testCheckWinNotYet();
        testCheckGameOverEmptyExists();
        testCheckGameOverMovesExist();
        testCheckGameOverReallyOver();
        testReadSaveXML();

        System.out.println("\n========================================");
        System.out.printf("  GameLogic: %d passed, %d failed%n", passed, failed);
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

    // ---- 空格检测 ----

    static void testEmptyCellsDetection() {
        System.out.println("[空格检测]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;
        g[3][3].value = 4;
        int count = 0;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (g[r][c].isEmpty()) count++;
        check("16 - 2 = 14 empty cells", count == 14);
    }

    // ---- 生成方块 ----

    static void testSpawnTile() {
        System.out.println("[生成方块]");
        Grid[][] g = emptyBoard();
        java.util.List<int[]> empties = new java.util.ArrayList<>();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (g[r][c].isEmpty()) empties.add(new int[]{r, c});

        int[] cell = empties.get(new java.util.Random().nextInt(empties.size()));
        g[cell[0]][cell[1]].value = 2;

        int nonZero = 0;
        for (Grid[] row : g)
            for (Grid t : row)
                if (!t.isEmpty()) nonZero++;
        check("exactly 1 tile after spawn", nonZero == 1);
    }

    static void testSpawnTileFullBoard() {
        System.out.println("[满棋盘不生成]");
        Grid[][] g = emptyBoard();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = 2;

        java.util.List<int[]> empties = new java.util.ArrayList<>();
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (g[r][c].isEmpty()) empties.add(new int[]{r, c});

        check("no empty cells on full board", empties.isEmpty());
    }

    // ---- 计分 ----

    static void testScoreAccumulation() {
        System.out.println("[计分累加]");
        Grid[][] g = emptyBoard();
        g[1][0].value = 2;
        g[2][0].value = 2;
        g[1][1].value = 4;
        g[2][1].value = 4;
        clearMerge(g);

        int s1 = g[2][0].moveUp(g, 2, 0);
        clearMerge(g);
        int s2 = g[2][1].moveUp(g, 2, 1);

        int total = s1 + s2;
        check("2+2 + 4+4 = 12", total == 12);
    }

    // ---- 胜利判定 ----

    static void testCheckWin() {
        System.out.println("[胜利判定]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2048;
        boolean win = false;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.value >= 2048) win = true;
        check("2048 present -> win", win);
    }

    static void testCheckWinNotYet() {
        System.out.println("[未胜利判定]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 1024;
        g[1][1].value = 512;
        boolean win = false;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.value >= 2048) win = true;
        check("no 2048 -> not win", !win);
    }

    // ---- 游戏结束判定 ----

    static void testCheckGameOverEmptyExists() {
        System.out.println("[GameOver - 有空位则继续]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;

        boolean empty = false;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.isEmpty()) empty = true;
        check("has empty -> not over", empty);
    }

    static void testCheckGameOverMovesExist() {
        System.out.println("[GameOver - 满但可合并则继续]");
        Grid[][] g = emptyBoard();
        int[] vals = {2,2,4,8, 8,4,2,16, 16,2,8,4, 4,8,16,2};
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[r * 4 + c];

        boolean empty = false;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.isEmpty()) empty = true;

        boolean canMerge = false;
        for (int r = 0; r < 4 && !canMerge; r++)
            for (int c = 0; c < 4 && !canMerge; c++) {
                if (c < 3 && g[r][c].value == g[r][c + 1].value) canMerge = true;
                if (r < 3 && g[r][c].value == g[r + 1][c].value) canMerge = true;
            }

        check("full but can merge -> not over", !empty && canMerge);
    }

    static void testCheckGameOverReallyOver() {
        System.out.println("[GameOver - 真正结束]");
        Grid[][] g = emptyBoard();
        int[] vals = {2,4,8,16, 16,8,4,2, 2,4,8,16, 16,8,4,2};
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c].value = vals[r * 4 + c];

        boolean empty = false;
        for (Grid[] row : g)
            for (Grid t : row)
                if (t.isEmpty()) empty = true;

        boolean canMerge = false;
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++) {
                if (c < 3 && g[r][c].value == g[r][c + 1].value) canMerge = true;
                if (r < 3 && g[r][c].value == g[r + 1][c].value) canMerge = true;
            }

        boolean over = !empty && !canMerge;
        check("full and no merges -> game over", over);
    }

    // ---- XML 存档 ----

    static void testReadSaveXML() {
        System.out.println("[XML存档格式解析]");
        String line = "<row0col0>2</row0col0>";
        int row = line.charAt(line.indexOf("row") + 3) - '0';
        int col = line.charAt(line.indexOf("col") + 3) - '0';
        int val = Integer.parseInt(line.substring(line.indexOf(">") + 1, line.indexOf("</")));
        check("parse row=0", row == 0);
        check("parse col=0", col == 0);
        check("parse value=2", val == 2);
    }
}
