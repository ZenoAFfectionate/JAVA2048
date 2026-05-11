import java.util.*;

/**
 * GameLogicCorrectnessTest — 验证 src/Utils.simulateMove 与 src/Grid.moveX 的
 * 实现是否符合 2048 标准规则。
 *
 * 用一个独立的"参考实现"（基于"列/行抽出 → 压缩 → 合并 → 复位"的规范实现）
 * 与 src 的递归实现做对比。
 */
public class GameLogicCorrectnessTest {

    public static void main(String[] args) {
        System.out.println("━━━ 游戏逻辑正确性: src vs 参考实现 ━━━\n");

        // === Phase 1: 手工构造的关键测试场景 ===
        section("关键场景");
        testSlide("[空格滑动] 单 tile 在底，UP 应到顶",
            board(new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{2,0,0,0}}), 0,
            board(new int[][]{{2,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 0);
        testSlide("[简单合并] 一行末尾 [0,0,2,2] LEFT → [4,0,0,0] 得分 4",
            board(new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,2,2}}), 2,
            board(new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{4,0,0,0}}), 4);
        testSlide("[非连环] [2,2,2,2] LEFT → [4,4,0,0] 得分 8（不是 8 → [8,0,0,0]）",
            board(new int[][]{{2,2,2,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 2,
            board(new int[][]{{4,4,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 8);
        testSlide("[隔空合并] [2,0,2,0] LEFT → [4,0,0,0] 得分 4",
            board(new int[][]{{2,0,2,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 2,
            board(new int[][]{{4,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 4);
        testSlide("[已就位无合并] [4,2,0,0] LEFT → 非法 (-1)，状态不变",
            board(new int[][]{{4,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 2,
            board(new int[][]{{4,2,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), -1);
        testSlide("[一次合并不连环] [4,4,2,2] LEFT → [8,4,0,0] 得分 12",
            board(new int[][]{{4,4,2,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 2,
            board(new int[][]{{8,4,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 12);
        testSlide("[左中阻挡] [2,4,2,2] LEFT → [2,4,4,0] 得分 4（4 不能跨 2 与 2 合并）",
            board(new int[][]{{2,4,2,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 2,
            board(new int[][]{{2,4,4,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 4);
        testSlide("[中间合并保留前 tile] [2,4,4,0] LEFT → [2,8,0,0] 得分 8",
            board(new int[][]{{2,4,4,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 2,
            board(new int[][]{{2,8,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 8);
        testSlide("[竖直合并] (0,0)=2,(1,0)=2 UP → (0,0)=4 得分 4",
            board(new int[][]{{2,0,0,0},{2,0,0,0},{0,0,0,0},{0,0,0,0}}), 0,
            board(new int[][]{{4,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 4);
        testSlide("[RIGHT 隔空合并] [2,0,0,2] RIGHT → [0,0,0,4] 得分 4",
            board(new int[][]{{2,0,0,2},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 3,
            board(new int[][]{{0,0,0,4},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 4);
        testSlide("[DOWN 隔空合并] (0,0)=2,(3,0)=2 DOWN → (3,0)=4 得分 4",
            board(new int[][]{{2,0,0,0},{0,0,0,0},{0,0,0,0},{2,0,0,0}}), 1,
            board(new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{4,0,0,0}}), 4);
        testSlide("[贴墙不合法] [2,4,8,16] UP → 非法",
            board(new int[][]{{2,4,8,16},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 0,
            board(new int[][]{{2,4,8,16},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), -1);
        testSlide("[全 0] UP → 非法",
            board(new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), 0,
            board(new int[][]{{0,0,0,0},{0,0,0,0},{0,0,0,0},{0,0,0,0}}), -1);

        // === Phase 2: 与"参考实现"在大量随机棋盘上对比 ===
        section("vs 参考实现 5000 个随机棋盘");
        comparisonAgainstReference(5000);

        System.out.printf("%n━━━ 总计: %d passed, %d failed ━━━%n", passed, failed);
        if (failures.size() > 0) {
            System.out.println("\n失败明细:");
            for (String f : failures) System.out.println("  - " + f);
        }
        System.exit(failed > 0 ? 1 : 0);
    }

    // ================================================================
    //  src 测试
    // ================================================================

    static void testSlide(String name, int[][] init, int dir, int[][] expected, int expScore) {
        Grid[][] g = toGrids(init);
        int score = Utils.simulateMove(g, dir);
        int[][] actual = fromGrids(g);

        boolean scoreOk = (score == expScore);
        boolean boardOk = boardEquals(actual, expected);
        // 当 expScore == -1（非法）时，棋盘也应保持原样
        if (expScore == -1) boardOk = boardEquals(actual, init);

        if (scoreOk && boardOk) {
            System.out.println("  ✓ " + name);
            passed++;
        } else {
            System.out.println("  ✗ " + name);
            System.out.println("      expected score=" + expScore + " board=" + dump(expected));
            System.out.println("      actual   score=" + score + " board=" + dump(actual));
            failed++;
            failures.add(name);
        }
    }

    // ================================================================
    //  参考实现 (基于规范的 row/col 处理)
    // ================================================================

    /** 单行向左压缩+合并的参考实现，返回 [得分, moved(0/1)] */
    static int[] referenceLeftRow(int[] row) {
        int n = row.length;
        // step 1: 移除 0
        int[] compact = new int[n];
        int k = 0;
        for (int v : row) if (v != 0) compact[k++] = v;
        // step 2: 合并
        int[] merged = new int[n];
        int m = 0;
        int score = 0;
        int i = 0;
        while (i < k) {
            if (i + 1 < k && compact[i] == compact[i + 1]) {
                merged[m++] = compact[i] * 2;
                score += compact[i] * 2;
                i += 2;
            } else {
                merged[m++] = compact[i];
                i++;
            }
        }
        // 检查是否变化
        boolean moved = false;
        for (int j = 0; j < n; j++) if (row[j] != merged[j]) { moved = true; break; }
        if (!moved) return new int[]{-1, 0};

        for (int j = 0; j < n; j++) row[j] = merged[j];
        return new int[]{score, 1};
    }

    /** 参考实现 simulateMove，返回 score，不可行 -1，落地修改 board */
    static int referenceSimulate(int[][] board, int dir) {
        // 把方向化归为 LEFT
        // dir: 0=UP 1=DOWN 2=LEFT 3=RIGHT
        int[][] rows = extractRows(board, dir);
        int totalScore = 0;
        boolean anyMove = false;
        for (int[] row : rows) {
            int[] r = referenceLeftRow(row);
            if (r[0] >= 0) {
                totalScore += r[0];
                anyMove = true;
            }
        }
        if (!anyMove) return -1;
        putBackRows(board, rows, dir);
        return totalScore;
    }

    static int[][] extractRows(int[][] board, int dir) {
        // 抽出"应该左压缩"的 4 行
        int[][] rows = new int[4][4];
        switch (dir) {
            case 0: // UP — 列为行，从 [0][c]..[3][c]
                for (int c = 0; c < 4; c++)
                    for (int r = 0; r < 4; r++) rows[c][r] = board[r][c];
                break;
            case 1: // DOWN — 列为行，反向
                for (int c = 0; c < 4; c++)
                    for (int r = 0; r < 4; r++) rows[c][r] = board[3 - r][c];
                break;
            case 2: // LEFT
                for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) rows[r][c] = board[r][c];
                break;
            case 3: // RIGHT — 反向
                for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) rows[r][c] = board[r][3 - c];
                break;
        }
        return rows;
    }

    static void putBackRows(int[][] board, int[][] rows, int dir) {
        switch (dir) {
            case 0:
                for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) board[r][c] = rows[c][r];
                break;
            case 1:
                for (int c = 0; c < 4; c++) for (int r = 0; r < 4; r++) board[3 - r][c] = rows[c][r];
                break;
            case 2:
                for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) board[r][c] = rows[r][c];
                break;
            case 3:
                for (int r = 0; r < 4; r++) for (int c = 0; c < 4; c++) board[r][3 - c] = rows[r][c];
                break;
        }
    }

    // ================================================================
    //  与参考对比
    // ================================================================

    static void comparisonAgainstReference(int trials) {
        Random rng = new Random(2026);
        int legalityMismatch = 0, scoreMismatch = 0, boardMismatch = 0;
        int compares = 0;

        for (int t = 0; t < trials; t++) {
            int[][] board = new int[4][4];
            int nFill = rng.nextInt(17);
            for (int i = 0; i < nFill; i++) {
                int r = rng.nextInt(4), c = rng.nextInt(4);
                board[r][c] = 1 << (1 + rng.nextInt(11));
            }
            for (int dir = 0; dir < 4; dir++) {
                compares++;
                // src
                Grid[][] g = toGrids(board);
                int srcScore = Utils.simulateMove(g, dir);
                int[][] srcResult = fromGrids(g);

                // ref
                int[][] refBoard = new int[4][4];
                for (int r = 0; r < 4; r++) System.arraycopy(board[r], 0, refBoard[r], 0, 4);
                int refScore = referenceSimulate(refBoard, dir);

                // 比较
                if ((srcScore < 0) != (refScore < 0)) legalityMismatch++;
                else if (srcScore >= 0 && srcScore != refScore) scoreMismatch++;
                else if (srcScore >= 0 && !boardEquals(srcResult, refBoard)) boardMismatch++;
            }
        }
        System.out.printf("  比较 %d 次%n", compares);
        System.out.printf("  合法性差异: %d  得分差异: %d  终态差异: %d%n",
            legalityMismatch, scoreMismatch, boardMismatch);
        boolean ok = (legalityMismatch == 0 && scoreMismatch == 0 && boardMismatch == 0);
        if (ok) {
            System.out.println("  ✓ src 与参考实现完全一致");
            passed++;
        } else {
            System.out.println("  ✗ src 与参考实现不一致 → 游戏底盘有 BUG");
            failed++;
            failures.add("comparison vs reference");
        }
    }

    // ================================================================
    //  helpers
    // ================================================================

    static int passed = 0, failed = 0;
    static List<String> failures = new ArrayList<>();

    static void section(String s) { System.out.println("[" + s + "]"); }

    static int[][] board(int[][] b) {
        int[][] r = new int[4][4];
        for (int i = 0; i < 4; i++) System.arraycopy(b[i], 0, r[i], 0, 4);
        return r;
    }

    static Grid[][] toGrids(int[][] s) {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++) {
                g[r][c] = new Grid();
                g[r][c].value = s[r][c];
            }
        return g;
    }

    static int[][] fromGrids(Grid[][] g) {
        int[][] s = new int[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                s[r][c] = g[r][c].value;
        return s;
    }

    static boolean boardEquals(int[][] a, int[][] b) {
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                if (a[r][c] != b[r][c]) return false;
        return true;
    }

    static String dump(int[][] b) {
        StringBuilder sb = new StringBuilder("[");
        for (int r = 0; r < 4; r++) {
            for (int c = 0; c < 4; c++) { sb.append(b[r][c]); sb.append(','); }
            sb.append('|');
        }
        sb.append(']');
        return sb.toString();
    }
}
