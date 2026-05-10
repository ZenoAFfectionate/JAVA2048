import java.util.*;

/**
 * 蒙特卡洛树搜索 (MCTS) — 完整 UCT 树 + 五项关键优化。
 *
 * 优化:
 *   1. 贪心 Rollout (启发式引导，非纯随机)
 *   2. 预过滤无效动作 (扩展节点前过滤，分支因子 4→2~3)
 *   3. int[][] 快照 + arraycopy (无 Grid 对象分配，无 GC 压力)
 *   4. Rollout 深度 ≤ 25 步 + 截断评估
 *   5. 完整树结构 + UCT 选择
 *
 * 使用: MCTS.setIterations(n); int dir = MCTS.getBestDirection(grids);
 */
public class MCTS {

    private static final int ROWS = 4, COLS = 4;
    private static final double UCT_C = 1.4;
    private static final int MAX_ROLLOUT = 25;
    private static int iterations = 50;
    private static final Random RNG = new Random();

    public static void setIterations(int n) { iterations = Math.max(1, n); }
    public static int getIterations() { return iterations; }

    // ================================================================
    //  公开 API — 返回最优方向 0=UP 1=DOWN 2=LEFT 3=RIGHT
    // ================================================================

    public static int getBestDirection(Grid[][] grids) {
        int[][] rootState = fromGrid(grids);
        List<Integer> valid = getValidMoves(rootState);
        if (valid.isEmpty()) return 0;
        if (valid.size() == 1)  return valid.get(0);

        Node[] roots = new Node[4];
        for (int dir : valid) {
            int[][] childState = copyBoard(rootState);
            int score = applyMove(childState, dir);
            roots[dir] = new Node(childState, dir);
            roots[dir].moveScore = Math.max(0, score);
        }

        for (int iter = 0; iter < iterations; iter++) {
            int dir = selectRoot(roots, valid);
            Node node = roots[dir];
            int[][] simBoard = copyBoard(node.state);

            while (node.isFullyExpanded() && hasChildren(node)) {
                Node child = bestChild(node);
                if (child == null) break;
                applyMove(simBoard, child.move);
                node = child;
            }

            double gain;

            if (!node.isFullyExpanded()) {
                int newDir = node.getUnexpanded(simBoard);
                if (newDir >= 0) {
                    int[][] childState = copyBoard(simBoard);
                    int score = applyMove(childState, newDir);
                    Node child = new Node(childState, newDir);
                    child.moveScore = Math.max(0, score);
                    child.parent = node;
                    node.children[newDir] = child;
                    node.expandedMask |= (1 << newDir);
                    spawnRandom(childState);
                    gain = Math.max(0, score) + greedyRollout(childState);
                    node = child;
                } else {
                    gain = greedyRollout(simBoard);
                }
            } else {
                gain = greedyRollout(simBoard);
            }

            while (node != null) {
                node.visits++;
                node.totalScore += gain;
                node = node.parent;
            }
            roots[dir].visits++;
            roots[dir].totalScore += gain;
        }

        int best = valid.get(0);
        double bestAvg = -Double.MAX_VALUE;
        for (int dir : valid) {
            double avg = roots[dir].visits > 0
                    ? roots[dir].totalScore / roots[dir].visits : 0;
            if (avg > bestAvg) { bestAvg = avg; best = dir; }
        }
        return best;
    }

    private static int selectRoot(Node[] roots, List<Integer> valid) {
        int best = valid.get(0);
        double bestVal = -Double.MAX_VALUE;
        int totalVisits = 0;
        for (int d : valid) totalVisits += roots[d].visits;
        double logTotal = Math.log(Math.max(1, totalVisits));

        for (int d : valid) {
            Node n = roots[d];
            double exploit = n.visits > 0 ? n.totalScore / n.visits : n.moveScore;
            double explore = UCT_C * Math.sqrt(logTotal / Math.max(1, n.visits));
            double uct = exploit + explore;
            if (uct > bestVal) { bestVal = uct; best = d; }
        }
        return best;
    }

    private static Node bestChild(Node parent) {
        Node best = null;
        double bestVal = -Double.MAX_VALUE;
        double logP = Math.log(Math.max(1, parent.visits));
        for (Node c : parent.children) {
            if (c == null) continue;
            double exploit = c.visits > 0 ? c.totalScore / c.visits : c.moveScore;
            double explore = UCT_C * Math.sqrt(logP / Math.max(1, c.visits));
            if (exploit + explore > bestVal) { bestVal = exploit + explore; best = c; }
        }
        return best;
    }

    private static boolean hasChildren(Node n) {
        for (Node c : n.children) if (c != null) return true;
        return false;
    }

    // ================================================================
    //  贪心 Rollout
    // ================================================================

    static int greedyRollout(int[][] board) {
        int totalScore = 0;
        for (int step = 0; step < MAX_ROLLOUT; step++) {
            int bestDir = -1, bestEval = Integer.MIN_VALUE;
            for (int d = 0; d < 4; d++) {
                int[][] trial = copyBoard(board);
                int score = applyMove(trial, d);
                if (score < 0) continue;
                int eval = score + evalBoard(trial);
                if (eval > bestEval) { bestEval = eval; bestDir = d; }
            }
            if (bestDir < 0) break;
            int sc = applyMove(board, bestDir);
            totalScore += Math.max(0, sc);
            spawnRandom(board);
        }
        return totalScore + evalBoard(board);
    }

    private static int evalBoard(int[][] b) {
        int empty = 0;
        int monoRow = 0, monoCol = 0;
        int smoothPenalty = 0;
        int mergeBonus = 0;
        int maxV = 0, maxR = 0, maxC = 0;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                int v = b[r][c];
                if (v == 0) { empty++; continue; }

                int logV = 31 - Integer.numberOfLeadingZeros(v);
                if (v > maxV) { maxV = v; maxR = r; maxC = c; }

                if (c + 1 < COLS && b[r][c + 1] != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(b[r][c + 1]);
                    smoothPenalty += Math.abs(logV - logN);
                }
                if (r + 1 < ROWS && b[r + 1][c] != 0) {
                    int logN = 31 - Integer.numberOfLeadingZeros(b[r + 1][c]);
                    smoothPenalty += Math.abs(logV - logN);
                }

                if (c + 1 < COLS && b[r][c + 1] == v) mergeBonus += logV;
                if (r + 1 < ROWS && b[r + 1][c] == v) mergeBonus += logV;
            }
        }

        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS - 1; c++)
                if (b[r][c] != 0 && b[r][c + 1] != 0)
                    monoRow += (b[r][c] >= b[r][c + 1]) ? 1 : -1;
        for (int c = 0; c < COLS; c++)
            for (int r = 0; r < ROWS - 1; r++)
                if (b[r][c] != 0 && b[r + 1][c] != 0)
                    monoCol += (b[r][c] >= b[r + 1][c]) ? 1 : -1;

        int cornerBonus = 0;
        if ((maxR == 0 || maxR == 3) && (maxC == 0 || maxC == 3))
            cornerBonus = (31 - Integer.numberOfLeadingZeros(maxV)) * 80;

        return empty * 220
             + (monoRow + monoCol) * 30
             - smoothPenalty * 16
             + mergeBonus * 18
             + cornerBonus;
    }

    // ================================================================
    //  Node
    // ================================================================

    static class Node {
        int[][] state;
        int move;
        int moveScore;
        Node[] children = new Node[4];
        Node parent;
        int visits;
        double totalScore;
        int expandedMask;

        Node(int[][] state, int move) {
            this.state = state;
            this.move = move;
        }

        boolean isFullyExpanded() {
            return Integer.bitCount(expandedMask) >= 4;
        }

        int getUnexpanded(int[][] board) {
            for (int d = 0; d < 4; d++) {
                if ((expandedMask & (1 << d)) != 0) continue;
                int[][] trial = copyBoard(board);
                int sc = applyMove(trial, d);
                expandedMask |= (1 << d);
                if (sc >= 0) return d;
            }
            return -1;
        }
    }

    // ================================================================
    //  棋盘操作 (纯 int[][])
    // ================================================================

    static int[][] fromGrid(Grid[][] g) {
        int[][] b = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                b[r][c] = g[r][c].value;
        return b;
    }

    static int[][] copyBoard(int[][] src) {
        int[][] dst = new int[ROWS][COLS];
        for (int r = 0; r < ROWS; r++)
            System.arraycopy(src[r], 0, dst[r], 0, COLS);
        return dst;
    }

    static List<Integer> getValidMoves(int[][] board) {
        List<Integer> valid = new ArrayList<>(4);
        for (int d = 0; d < 4; d++) {
            int[][] test = copyBoard(board);
            if (applyMove(test, d) >= 0) valid.add(d);
        }
        return valid;
    }

    static int applyMove(int[][] b, int dir) {
        boolean[] merged = new boolean[16];
        int score = 0;
        boolean moved = false;

        switch (dir) {
            case 0: // UP
                for (int c = 0; c < COLS; c++)
                    for (int r = 1; r < ROWS; r++)
                        if (b[r][c] != 0) { int s = slide(b, merged, r, c, 0); if (s >= 0) { score += s; moved = true; } }
                break;
            case 1: // DOWN
                for (int c = 0; c < COLS; c++)
                    for (int r = ROWS - 2; r >= 0; r--)
                        if (b[r][c] != 0) { int s = slide(b, merged, r, c, 1); if (s >= 0) { score += s; moved = true; } }
                break;
            case 2: // LEFT
                for (int r = 0; r < ROWS; r++)
                    for (int c = 1; c < COLS; c++)
                        if (b[r][c] != 0) { int s = slide(b, merged, r, c, 2); if (s >= 0) { score += s; moved = true; } }
                break;
            case 3: // RIGHT
                for (int r = 0; r < ROWS; r++)
                    for (int c = COLS - 2; c >= 0; c--)
                        if (b[r][c] != 0) { int s = slide(b, merged, r, c, 3); if (s >= 0) { score += s; moved = true; } }
                break;
        }
        return moved ? score : -1;
    }

    private static int slide(int[][] b, boolean[] merged, int r, int c, int dir) {
        int dr = (dir == 0) ? -1 : (dir == 1) ? 1 : 0;
        int dc = (dir == 2) ? -1 : (dir == 3) ? 1 : 0;

        int nr = r + dr, nc = c + dc;
        while (nr >= 0 && nr < ROWS && nc >= 0 && nc < COLS && b[nr][nc] == 0) {
            nr += dr; nc += dc;
        }

        if (nr < 0 || nr >= ROWS || nc < 0 || nc >= COLS) {
            // 已到边界: 移到该方向的最远端
            int tr = r, tc = c;
            if (dir == 0) tr = 0; else if (dir == 1) tr = ROWS - 1;
            else if (dir == 2) tc = 0; else tc = COLS - 1;
            if (tr != r || tc != c) { b[tr][tc] = b[r][c]; b[r][c] = 0; }
            return (tr != r || tc != c) ? 0 : -1;  // 移动了→0, 未动→-1
        }

        int idx = nr * COLS + nc;
        if (b[nr][nc] == b[r][c] && !merged[idx]) {
            merged[idx] = true;
            b[nr][nc] *= 2; b[r][c] = 0;
            return b[nr][nc];  // 合并, 返回得分
        }

        // 被不同值阻挡: 移到目标相邻位置 (如果有空档)
        int tr = nr - dr, tc = nc - dc;
        if (tr != r || tc != c) { b[tr][tc] = b[r][c]; b[r][c] = 0; return 0; }
        return -1;  // 紧邻阻挡, 无法移动
    }

    static void spawnRandom(int[][] board) {
        int[] empties = new int[16];
        int count = 0;
        for (int r = 0; r < ROWS; r++)
            for (int c = 0; c < COLS; c++)
                if (board[r][c] == 0)
                    empties[count++] = (r << 8) | c;
        if (count == 0) return;
        int idx = empties[RNG.nextInt(count)];
        board[idx >> 8][idx & 0xFF] = RNG.nextDouble() < 0.9 ? 2 : 4;
    }

    public static String getDirectionName(int dir) {
        switch (dir) {
            case 0: return "Up";
            case 1: return "Down";
            case 2: return "Left";
            case 3: return "Right";
            default: return "?";
        }
    }
}
