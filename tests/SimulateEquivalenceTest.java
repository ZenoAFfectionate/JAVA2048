import java.util.*;

/**
 * SimulateEquivalenceTest — 对比 MCTS.applyMove(int[][]) 与 src 的 Utils.simulateMove(Grid[][])
 * 在大量随机棋盘上结果是否完全一致。
 *
 * 这是回答"算法是不是真的用 src 中的游戏逻辑"的关键测试：
 * 如果 MCTS 自家实现与 src 不一致，那么 MCTS 在做"另一个游戏"。
 */
public class SimulateEquivalenceTest {
    static int passed = 0, failed = 0;
    static List<String> mismatches = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("━━━ 滑块语义等价性测试: MCTS.applyMove vs Utils.simulateMove ━━━\n");

        Random rng = new Random(42);
        final int TRIALS = 5000;
        int totalCompares = 0;
        int scoreMismatch = 0, boardMismatch = 0, legalityMismatch = 0;

        for (int trial = 0; trial < TRIALS; trial++) {
            // 生成随机棋盘：随机 0~16 个 tile，值在 {0,2,4,...,2048,4096}
            int[][] board = new int[4][4];
            int nFill = rng.nextInt(17);
            for (int i = 0; i < nFill; i++) {
                int r = rng.nextInt(4), c = rng.nextInt(4);
                board[r][c] = 1 << (1 + rng.nextInt(12));  // 2..4096
            }

            for (int dir = 0; dir < 4; dir++) {
                totalCompares++;

                // 路径 A: MCTS 自家 applyMove(int[][])
                int[][] boardA = deepCopy(board);
                int scoreA = MCTS.applyMove(boardA, dir);

                // 路径 B: src 的 Utils.simulateMove(Grid[][])
                Grid[][] gridB = toGrids(board);
                int scoreB = Utils.simulateMove(gridB, dir);
                int[][] boardB = fromGrids(gridB);

                // 比较 1: 合法性
                boolean legalA = scoreA >= 0;
                boolean legalB = scoreB >= 0;
                if (legalA != legalB) {
                    legalityMismatch++;
                    if (mismatches.size() < 5)
                        mismatches.add(String.format("trial=%d dir=%d 合法性不一致 A=%d B=%d board=%s",
                            trial, dir, scoreA, scoreB, dump(board)));
                    continue;  // 后续比较没意义
                }
                if (!legalA) continue;  // 都非法，board 应保持原样

                // 比较 2: 合并得分
                if (scoreA != scoreB) {
                    scoreMismatch++;
                    if (mismatches.size() < 5)
                        mismatches.add(String.format("trial=%d dir=%d score A=%d B=%d board=%s",
                            trial, dir, scoreA, scoreB, dump(board)));
                }

                // 比较 3: 终态棋盘
                if (!boardEquals(boardA, boardB)) {
                    boardMismatch++;
                    if (mismatches.size() < 5)
                        mismatches.add(String.format("trial=%d dir=%d board diff:%n  init=%s%n  A   =%s%n  B   =%s",
                            trial, dir, dump(board), dump(boardA), dump(boardB)));
                }
            }
        }

        System.out.printf("总比较: %d 次 (%d trials × 4 dirs)%n", totalCompares, TRIALS);
        System.out.printf("  合法性不一致: %d%n", legalityMismatch);
        System.out.printf("  合并得分不一致: %d%n", scoreMismatch);
        System.out.printf("  终态棋盘不一致: %d%n", boardMismatch);

        if (mismatches.size() > 0) {
            System.out.println("\n前 5 个不一致样例:");
            for (String m : mismatches) System.out.println("  " + m);
        }

        boolean ok = (legalityMismatch == 0 && scoreMismatch == 0 && boardMismatch == 0);
        System.out.println(ok
            ? "\n✓ MCTS.applyMove 与 Utils.simulateMove 语义完全等价"
            : "\n✗ MCTS 自家实现与 src 不等价！MCTS 在做'另一个游戏'");
        System.exit(ok ? 0 : 1);
    }

    static int[][] deepCopy(int[][] s) {
        int[][] d = new int[4][4];
        for (int r = 0; r < 4; r++) System.arraycopy(s[r], 0, d[r], 0, 4);
        return d;
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
