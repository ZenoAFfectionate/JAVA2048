import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2048 游戏主界面 — 包含窗口布局、游戏面板、键盘交互与存档
 */
public class GameView extends JPanel implements ActionListener {

    // ---- 常量 ----
    private static final int FRAME_W = 420;
    private static final int FRAME_H = 560;
    private static final Color BG_COLOR = new Color(0xfaf8ef);
    private static final Color BOARD_BG = new Color(0xbbada0);
    private static final Color ACCENT   = new Color(0x8b7355);

    // ---- 全局状态 ----
    private static boolean soundOn = true;
    private static int score = 0;
    private static int bestScore = 0;
    private static boolean hasWon = false;
    static final Random RNG = new Random();  // 共享随机数生成器

    // ---- 字体 ----
    private static final Font FONT_TITLE = new Font("Arial", Font.BOLD, 52);
    private static final Font FONT_SCORE = new Font("Arial", Font.BOLD, 26);
    private static final Font FONT_BEST  = new Font("Arial", Font.BOLD, 14);
    private static final Font FONT_MENU  = new Font("宋体", Font.PLAIN, 16);
    private static final Font FONT_HINT  = new Font("宋体", Font.PLAIN, 12);

    // ---- 组件 ----
    private JFrame frame;
    private JLabel labelScore, labelBest, labelSoundIcon;
    private GameBoard gameBoard;

    // ================================================================
    //  构造 & 初始化
    // ================================================================
    public GameView() {
        init();
    }

    private void init() {
        frame = new JFrame("2048");
        frame.setSize(FRAME_W, FRAME_H);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setLayout(null);
        frame.getContentPane().setBackground(BG_COLOR);

        JLabel lblTitle = new JLabel("2048", JLabel.LEFT);
        lblTitle.setFont(FONT_TITLE);
        lblTitle.setForeground(new Color(0x776e65));
        lblTitle.setBounds(20, 10, 200, 60);
        frame.add(lblTitle);

        JPanel scorePanel = new JPanel(null);
        scorePanel.setBounds(210, 10, 180, 72);
        scorePanel.setOpaque(false);
        frame.add(scorePanel);

        JLabel lblScoreHdr = mkChip("SCORE", FONT_BEST, 0, 0, 88, 30);
        scorePanel.add(lblScoreHdr);
        labelScore = mkChip("0", FONT_SCORE, 0, 28, 88, 42);
        scorePanel.add(labelScore);

        JLabel lblBestHdr = mkChip("BEST", FONT_BEST, 92, 0, 88, 30);
        scorePanel.add(lblBestHdr);
        labelBest = mkChip("0", FONT_SCORE, 92, 28, 88, 42);
        scorePanel.add(labelBest);

        labelSoundIcon = new JLabel("♪ ON", JLabel.CENTER);
        labelSoundIcon.setFont(new Font("Arial", Font.PLAIN, 11));
        labelSoundIcon.setForeground(new Color(0x776e65));
        labelSoundIcon.setBounds(210, 85, 180, 16);
        frame.add(labelSoundIcon);

        JMenuBar menuBar = new JMenuBar();
        JMenu menu = new JMenu("Game");
        menu.setFont(FONT_MENU);

        addMenuItem(menu, "New Game", "newgame");
        addMenuItem(menu, "Save",      "save");
        addMenuItem(menu, "Load",      "load");
        menu.addSeparator();
        addMenuItem(menu, "Basic Rules", "rules");
        addMenuItem(menu, "About",       "about");
        menu.addSeparator();
        addMenuItem(menu, "Exit", "exit");

        menuBar.add(menu);
        frame.setJMenuBar(menuBar);

        gameBoard = new GameBoard();
        gameBoard.setBounds(10, 108, 385, 385);
        gameBoard.setBackground(BOARD_BG);
        gameBoard.setFocusable(true);
        frame.add(gameBoard);

        JLabel lblHint = new JLabel("↑↓←→ 移动  |  M 音效  |  S 存档  |  L 读档  |  T 提示  |  Esc 重来",
                JLabel.CENTER);
        lblHint.setFont(FONT_HINT);
        lblHint.setForeground(new Color(0xbbada0));
        lblHint.setBounds(10, 500, 388, 18);
        frame.add(lblHint);
    }

    private JLabel mkChip(String text, Font font, int x, int y, int w, int h) {
        JLabel l = new JLabel(text, JLabel.CENTER);
        l.setFont(font);
        l.setForeground(Color.WHITE);
        l.setOpaque(true);
        l.setBackground(ACCENT);
        l.setBounds(x, y, w, h);
        return l;
    }

    private void addMenuItem(JMenu menu, String label, String cmd) {
        JMenuItem item = new JMenuItem(label);
        item.setFont(FONT_MENU);
        item.setActionCommand(cmd);
        item.addActionListener(this);
        menu.add(item);
    }

    public void showView() {
        frame.setVisible(true);
        gameBoard.requestFocusInWindow();
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        String cmd = e.getActionCommand();
        if ("newgame".equals(cmd)) {
            gameBoard.restart();
        } else if ("save".equals(cmd)) {
            gameBoard.saveToXML();
        } else if ("load".equals(cmd)) {
            gameBoard.loadFromXML();
        } else if ("rules".equals(cmd)) {
            JOptionPane.showMessageDialog(frame,
                    "1. Arrow keys to merge powers of two\n"
                    + "2. Goal: reach 2048 and score high\n"
                    + "3. Esc - New Game\n"
                    + "4. M   - Toggle Sound\n"
                    + "5. S   - Save\n"
                    + "6. L   - Load\n"
                    + "7. T   - AI Hint",
                    "Rules", JOptionPane.INFORMATION_MESSAGE);
        } else if ("about".equals(cmd)) {
            JOptionPane.showMessageDialog(frame,
                    "Author: Liang Pang @ SCUT\n"
                    + "Student ID: 202030100266\n"
                    + "All rights reserved, academic use only.",
                    "About", JOptionPane.INFORMATION_MESSAGE);
        } else if ("exit".equals(cmd)) {
            int res = JOptionPane.showConfirmDialog(frame,
                    "Sure to exit?", "Exit", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) System.exit(0);
        }
    }

    // ---- 静态访问 ----
    static boolean isSoundOn()          { return soundOn; }
    static int     getScore()           { return score; }
    static void    addScore(int s)      { score += s; }
    static void    toggleSound()        { soundOn = !soundOn; }
    static int     getBestScore()       { return bestScore; }
    static void    updateBestScore()    { if (score > bestScore) bestScore = score; }
    static void    resetScore()         { score = 0; }
    static boolean hasWon()             { return hasWon; }
    static void    setHasWon(boolean v) { hasWon = v; }

    // ================================================================
    //  GameBoard — 游戏核心面板
    // ================================================================
    class GameBoard extends JPanel implements KeyListener {

        static final int GAP  = 10;
        static final int SIZE = 83;
        static final int ARC  = 12;
        static final int ROWS = 4;
        static final int COLS = 4;
        private static final int PANEL_SIZE = GAP + (SIZE + GAP) * COLS;

        // 缓存的 FontMetrics，每次 paintComponent 时更新
        private transient FontMetrics[] cachedMetrics = null;

        // 方向常量 (动画追踪用)
        private static final int DIR_UP    = 0;
        private static final int DIR_DOWN  = 1;
        private static final int DIR_LEFT  = 2;
        private static final int DIR_RIGHT = 3;

        final Grid[][] grids = new Grid[ROWS][COLS];
        final AnimationEngine animEngine = new AnimationEngine();
        int pendingKeyCode; // 0 = none

        GameBoard() {
            restart();
            addKeyListener(this);
            setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
        }

        void restart() {
            animEngine.clear();
            pendingKeyCode = 0;
            resetScore();
            hasWon = false;
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    grids[r][c] = new Grid();
            // 初始两个 tile 不使用动画 (新鲜棋盘)
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

        // ---- 棋盘操作 ----

        private int[][] saveValues() {
            int[][] vals = new int[ROWS][COLS];
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    vals[r][c] = grids[r][c].value;
            return vals;
        }

        private void clearMerge() {
            for (Grid[] row : grids)
                for (Grid g : row)
                    g.setMerge(false);
        }

        private void refreshUI() {
            labelScore.setText(String.valueOf(score));
            if (score > bestScore) {
                bestScore = score;
                labelBest.setText(String.valueOf(bestScore));
            }
            labelSoundIcon.setText(soundOn ? "♪ ON" : "♪ OFF");
        }

        /** 获取所有空格 (仅供 spawnTile 使用) */
        private List<int[]> emptyCells() {
            List<int[]> list = new ArrayList<>(16);
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    if (grids[r][c].isEmpty())
                        list.add(new int[]{r, c});
            return list;
        }

        /** 随机空格生成 2/4 并记录 POP 动画 */
        private void spawnTileWithAnim() {
            List<int[]> empties = emptyCells();
            if (empties.isEmpty()) return;
            int[] cell = empties.get(RNG.nextInt(empties.size()));
            int val = RNG.nextDouble() < 0.9 ? 2 : 4;
            grids[cell[0]][cell[1]].value = val;
            animEngine.addPop(cell[0], cell[1], val);
        }

        // ---- 动画构建 ----

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

        // ---- 方向移动 (返回值: 得分; -1 表示无移动) ----

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

        // ---- 键盘 ----

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

        @Override public void keyReleased(KeyEvent e) {}
        @Override public void keyTyped(KeyEvent e)    {}

        // ---- 胜负判断 ----

        private boolean checkWin() {
            for (Grid[] row : grids)
                for (Grid g : row)
                    if (g.value >= 2048) return true;
            return false;
        }

        /** 一次遍历同时检查空格与可合并对 */
        private boolean checkGameOver() {
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++) {
                    if (grids[r][c].isEmpty()) return false;
                    int v = grids[r][c].value;
                    if (c < COLS - 1 && grids[r][c + 1].value == v) return false;
                    if (r < ROWS - 1 && grids[r + 1][c].value == v) return false;
                }
            return true;
        }

        // ---- 绘制 ----

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_LCD_HRGB);
            g2.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);

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
                    if (slideTargets.contains(key)) continue;
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

        /** 绘制静态 tile (含 POP scale / MERGE glow) */
        private void drawTile(Graphics2D g, int r, int c, int x, int y,
                              AnimationEngine.Anim anim, FontMetrics[] fms) {
            Grid tile = grids[r][c];

            boolean isPop = anim != null && anim.type == AnimationEngine.Type.POP;
            boolean isMerge = anim != null && anim.type == AnimationEngine.Type.MERGE;

            if (isPop && anim.getScale() < 0.05) return;

            float glow = isMerge ? anim.getGlow() : 0f;

            // 阴影
            g.setColor(new Color(0, 0, 0, 25));
            g.fillRoundRect(x + 3, y + 3, SIZE, SIZE, ARC, ARC);

            // 背景
            g.setColor(tile.getBackground());
            if (isPop) {
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

            // 高数值渐变光泽 (>= 1024)
            if (tile.value >= 1024 && !tile.isEmpty()) {
                g.setColor(new Color(255, 255, 255, 35));
                g.fillRoundRect(x, y + SIZE / 2, SIZE, SIZE / 2, ARC, ARC);
            }

            if (tile.isEmpty()) return;

            // 文字 (合并时稍大)
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

        // ---- 存档 / 读档 ----

        void saveToXML() {
            List<String> keys = new ArrayList<>();
            List<String> vals = new ArrayList<>();
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    keys.add("row" + r + "col" + c);
            for (Grid[] row : grids)
                for (Grid g : row)
                    vals.add(String.valueOf(g.value));
            keys.add("score");
            vals.add(String.valueOf(score));
            keys.add("best");
            vals.add(String.valueOf(bestScore));

            try {
                Utils.writeXML(keys, vals, "save.xml");
                JOptionPane.showMessageDialog(frame, "存档成功！", "Save",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (FileNotFoundException ex) {
                JOptionPane.showMessageDialog(frame, "存档失败: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        void loadFromXML() {
            File f = new File("save.xml");
            if (!f.exists()) {
                JOptionPane.showMessageDialog(frame, "未找到存档文件！", "Load",
                        JOptionPane.WARNING_MESSAGE);
                return;
            }
            try (BufferedReader br = new BufferedReader(new FileReader(f))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.contains("score")) {
                        score = extractValue(line);
                    } else if (line.contains("best")) {
                        bestScore = extractValue(line);
                    } else if (line.contains("row")) {
                        int row = line.charAt(line.indexOf("row") + 3) - '0';
                        int col = line.charAt(line.indexOf("col") + 3) - '0';
                        grids[row][col].value = extractValue(line);
                    }
                }
                hasWon = checkWin();
                refreshUI();
                repaint();
                JOptionPane.showMessageDialog(frame, "读档成功！", "Load",
                        JOptionPane.INFORMATION_MESSAGE);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "读档失败: " + ex.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            }
        }

        private int extractValue(String xml) {
            return Integer.parseInt(xml.substring(xml.indexOf(">") + 1, xml.indexOf("</")));
        }
    }
}
