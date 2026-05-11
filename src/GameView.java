import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * 2048 游戏主界面 — 包含窗口布局、游戏面板、键盘交互与存档
 *
 * <h3>2026-05-11 流畅度优化</h3>
 * <ol>
 *   <li><b>动画补帧</b>：旧 AnimationEngine 的 Timer 仅检查"完成"，<b>从不 repaint</b> →
 *       动画期间没有补帧导致"瞬移"。新版每 16ms 触发 onFrame 回调进行 repaint。</li>
 *   <li><b>消除热路径分配</b>：paintComponent 不再每帧 new Map/Set/Color/Grid/FontMetrics。
 *       所有这些移到字段缓存，按需复用。</li>
 *   <li><b>checkGameOver 缓存</b>：旧实现每次 paintComponent 都遍历棋盘判定，
 *       新版仅在 executeMove 后一次判定，结果缓存为字段。</li>
 *   <li><b>音效懒检查</b>：res/ 目录不存在时直接禁用音效，避免无谓的 new Thread。</li>
 *   <li><b>pendingKey 同步执行</b>：动画结束 → 直接 executeMove（已在 EDT），不走
 *       SwingUtilities.invokeLater 排队，连击响应更跟手。</li>
 *   <li><b>双缓冲</b>：JPanel 默认开启 doubleBuffered，无需额外修改。</li>
 * </ol>
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
    /** 音效系统是否可用（res/ 目录与 wav 文件都存在时为 true） */
    private static final boolean SOUND_AVAILABLE = checkSoundAvailable();
    private static int score = 0;
    private static int bestScore = 0;
    private static boolean hasWon = false;
    static final Random RNG = new Random();

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

    private static boolean checkSoundAvailable() {
        File dir = new File("res");
        if (!dir.isDirectory()) return false;
        return new File("res/move.wav").isFile() || new File("res/merge.wav").isFile();
    }

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

        labelSoundIcon = new JLabel(SOUND_AVAILABLE ? "♪ ON" : "♪ N/A", JLabel.CENTER);
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
    static boolean isSoundOn()          { return soundOn && SOUND_AVAILABLE; }
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

        // 方向常量 (动画追踪用)
        private static final int DIR_UP    = 0;
        private static final int DIR_DOWN  = 1;
        private static final int DIR_LEFT  = 2;
        private static final int DIR_RIGHT = 3;

        final Grid[][] grids = new Grid[ROWS][COLS];
        final AnimationEngine animEngine = new AnimationEngine();
        int pendingKeyCode; // 0 = none

        // 缓存：游戏结束标志（仅 executeMove 后更新一次，paintComponent 直接读）
        private boolean gameOverCached = false;

        // 缓存：用于 paintComponent 的预分配对象（避免每帧 new）
        private final AnimationEngine.Anim[] animByCell = new AnimationEngine.Anim[ROWS * COLS];
        private final boolean[] slideTargetByCell = new boolean[ROWS * COLS];
        /** POP 尚在 delay 内（该格的新方块还不应该显示）。 */
        private final boolean[] popPendingByCell = new boolean[ROWS * COLS];

        // 缓存：4 种字体的 FontMetrics（构造时初始化）
        private final Font[] cachedFonts = new Font[4];
        private final FontMetrics[] cachedMetrics = new FontMetrics[4];

        // 缓存：颜色（避免每帧 new Color）
        private static final Color SHADOW_COLOR    = new Color(0, 0, 0, 25);
        private static final Color HIGHLIGHT_COLOR = new Color(255, 255, 255, 35);
        private static final Color GRID_LINE_COLOR = new Color(187, 173, 160, 60);
        private static final Color OVERLAY_COLOR   = new Color(238, 228, 218, 185);
        private static final Color OVERLAY_TEXT    = new Color(119, 110, 101);

        GameBoard() {
            setOpaque(true);  // 启用快速 fillRect 路径
            // 初始化字体缓存（值任选，仅用于触发不同 font 大小）
            cachedFonts[0] = new Grid(2).getCheckFont();      // size 46
            cachedFonts[1] = new Grid(16).getCheckFont();     // size 40
            cachedFonts[2] = new Grid(256).getCheckFont();    // size 34
            cachedFonts[3] = new Grid(1024).getCheckFont();   // size 28

            restart();
            addKeyListener(this);
            setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
        }

        @Override
        public void addNotify() {
            super.addNotify();
            // 等组件附加到容器后再获取 FontMetrics（getFontMetrics 需要 GraphicsEnvironment）
            Graphics g = getGraphics();
            if (g != null) {
                for (int i = 0; i < 4; i++)
                    cachedMetrics[i] = g.getFontMetrics(cachedFonts[i]);
                g.dispose();
            }
        }

        void restart() {
            animEngine.clear();
            pendingKeyCode = 0;
            gameOverCached = false;
            resetScore();
            hasWon = false;
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    grids[r][c] = new Grid();
            // 初始两个 tile 不使用动画
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
            if (SOUND_AVAILABLE) {
                labelSoundIcon.setText(soundOn ? "♪ ON" : "♪ OFF");
            }
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

        /**
         * 对比 pre/post 快照，为每个移动过的 tile 创建 SLIDE 动画。
         *
         * <h4>2026-05-11 补充修复：合并场景也产生 SLIDE 动画</h4>
         * 旧实现遇到合并 tile 直接 continue，导致 merged tile 的两个原 tile 没有滑动，
         * 视觉上"瞬移到合并位置然后闪光"。
         * 新版：对于 merged tile，扫描两个 pre 位置（与它 post 值的一半相等）都产生 SLIDE。
         *
         * @param preVals 移动前的 value 快照
         * @param dir 方向常量
         */
        private void buildSlideAnims(int[][] preVals, int dir) {
            boolean[][] consumed = new boolean[ROWS][COLS];

            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int postVal = grids[r][c].value;
                    if (postVal == 0) continue;

                    boolean merged = grids[r][c].isMerged();
                    // 需要寻找的 pre 值：合并 tile 找 postVal/2，普通 tile 找 postVal 本身
                    int needed = merged ? postVal / 2 : postVal;
                    // 合并需要找 2 个源；普通需要找 1 个源
                    int toFind = merged ? 2 : 1;
                    // 普通 tile 如果没位置变化（preVals[r][c] == postVal）跳过
                    if (!merged && preVals[r][c] == postVal) continue;

                    int foundCount = 0;
                    if (dir == DIR_UP || dir == DIR_DOWN) {
                        int startR = (dir == DIR_UP) ? ROWS - 1 : 0;
                        int endR   = (dir == DIR_UP) ? -1 : ROWS;
                        int step   = (dir == DIR_UP) ? -1 : 1;
                        for (int pr = startR; pr != endR && foundCount < toFind; pr += step) {
                            if (consumed[pr][c]) continue;
                            if (preVals[pr][c] == needed) {
                                if (pr != r) {  // 已就位的 tile 不动画
                                    animEngine.addSlide(pr, c, r, c, needed);
                                }
                                consumed[pr][c] = true;
                                foundCount++;
                            }
                        }
                    } else {
                        int startC = (dir == DIR_LEFT) ? COLS - 1 : 0;
                        int endC   = (dir == DIR_LEFT) ? -1 : COLS;
                        int step   = (dir == DIR_LEFT) ? -1 : 1;
                        for (int pc = startC; pc != endC && foundCount < toFind; pc += step) {
                            if (consumed[r][pc]) continue;
                            if (preVals[r][pc] == needed) {
                                if (pc != c) {
                                    animEngine.addSlide(r, pc, r, c, needed);
                                }
                                consumed[r][pc] = true;
                                foundCount++;
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
            // ★ BUG 修复（2026-05-11 重影问题根因）：
            //   AnimationEngine.animations 列表在每次 start→onFinish 后并未清空，
            //   所以下一次 buildSlideAnims/buildMergeAnims/addPop 会"累加"在旧动画之上，
            //   导致旧 SLIDE/POP/MERGE 在新一轮里被重新播放（startTime 重置为 now）→
            //   屏幕上出现"上一步动作的方块再次滑过来"的重影。
            //   修复：在每次 move 决策开始前清空 animations。
            //   注意：clear() 只清列表 + 停 timer + 重置 onFinish，因为我们只在
            //   动画"已 finish"时进 executeMove（keyPressed 在 isRunning() 时只
            //   缓存 pendingKey），所以这里 clear 不会误中断进行中的动画。
            animEngine.clear();

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

            // 音效（仅在文件可用时启动线程，避免无谓开销）
            if (SOUND_AVAILABLE && soundOn) {
                new PlaySound("move.wav").start();
                if (moveScore > 0) new PlaySound("merge.wav").start();
            }

            addScore(moveScore);
            refreshUI();
            spawnTileWithAnim();

            // 仅在 move 后做一次 game over 判定，缓存结果
            gameOverCached = checkGameOver();

            // 启动动画：每帧 onFrame 触发 repaint，结束后 onFinish 处理胜负与连击
            animEngine.start(this::repaint, () -> {
                repaint();
                if (!hasWon && checkWin()) {
                    hasWon = true;
                    int res = JOptionPane.showConfirmDialog(frame,
                            "恭喜！你达成了 2048！\n是否继续游戏？",
                            "Victory!", JOptionPane.YES_NO_OPTION);
                    if (res != JOptionPane.YES_OPTION) restart();
                    else repaint();
                }
                if (gameOverCached) {
                    refreshUI();
                    repaint();
                    int res = JOptionPane.showConfirmDialog(frame,
                            "Game Over!  得分: " + score + "\n是否重新开始？",
                            "Game Over", JOptionPane.YES_NO_OPTION);
                    if (res == JOptionPane.YES_OPTION) restart();
                    else repaint();
                }
                // 连击：直接同步 executeMove（已在 EDT），不走 invokeLater 排队
                if (pendingKeyCode != 0) {
                    int k = pendingKeyCode;
                    pendingKeyCode = 0;
                    executeMove(k);
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

            // 分隔线 — 浅色线条模拟网格（用缓存的 Color）
            g2.setColor(GRID_LINE_COLOR);
            for (int i = 1; i < COLS; i++) {
                int lineX = GAP + (GAP + SIZE) * i - GAP / 2;
                g2.fillRoundRect(lineX, GAP, GAP, PANEL_SIZE - GAP * 2, 4, 4);
            }
            for (int i = 1; i < ROWS; i++) {
                int lineY = GAP + (GAP + SIZE) * i - GAP / 2;
                g2.fillRoundRect(GAP, lineY, PANEL_SIZE - GAP * 2, GAP, 4, 4);
            }

            // 重置缓存的动画查找表（不分配新对象）
            // slideTargetByCell[idx]：该 cell 有"尚未完成"的 SLIDE 滑向它；
            //                        第一遍应跳过（避免和滑动中的 tile 重影）。
            // popPendingByCell[idx]：该 cell 有 POP 还在 delay 内（新方块不应提前显示）。
            // animByCell[idx]：该 cell 上"已开始"的 MERGE/POP（在 delay 内的视为未开始）。
            for (int i = 0; i < ROWS * COLS; i++) {
                animByCell[i] = null;
                slideTargetByCell[i] = false;
                popPendingByCell[i] = false;
            }

            boolean animating = animEngine.isRunning();
            if (animating) {
                List<AnimationEngine.Anim> anims = animEngine.getAnimations();
                for (int i = 0, n = anims.size(); i < n; i++) {
                    AnimationEngine.Anim a = anims.get(i);
                    int idx = a.toRow * COLS + a.toCol;
                    if (a.type == AnimationEngine.Type.SLIDE) {
                        if (!a.isDone()) slideTargetByCell[idx] = true;
                    } else if (a.type == AnimationEngine.Type.POP) {
                        if (a.isStarted()) animByCell[idx] = a;
                        else popPendingByCell[idx] = true;  // delay 中 → 该格先不画
                    } else {  // MERGE
                        if (a.isStarted()) animByCell[idx] = a;
                        // MERGE delay 中：此时 grids[r][c] 已是合并后的新值，
                        // 若不跳过会出现"合并值提前显示且没有闪光"——
                        // 但实际上 SLIDE 也在 delay 期滑入此格，所以 slideTargetByCell 会跳过。
                    }
                }
            }

            // 第一遍: 绘制静态 tile + 已开始的 POP/MERGE tile (在网格位置)
            for (int r = 0; r < ROWS; r++) {
                for (int c = 0; c < COLS; c++) {
                    int idx = r * COLS + c;
                    if (slideTargetByCell[idx]) continue;  // SLIDE 滑向此格尚未完成 → 跳过
                    if (popPendingByCell[idx]) continue;   // POP 延迟中 → 该格暂不画
                    AnimationEngine.Anim anim = animByCell[idx];
                    int x = GAP + (GAP + SIZE) * c;
                    int y = GAP + (GAP + SIZE) * r;
                    drawTile(g2, r, c, x, y, anim);
                }
            }

            // 第二遍: 绘制"未完成"的 SLIDE tile (在插值位置)。
            //   已完成的 SLIDE 不再画——此时静态 grids 值已经正确，或 MERGE 闪光即将接管。
            if (animating) {
                List<AnimationEngine.Anim> anims = animEngine.getAnimations();
                for (int i = 0, n = anims.size(); i < n; i++) {
                    AnimationEngine.Anim a = anims.get(i);
                    if (a.type != AnimationEngine.Type.SLIDE) continue;
                    if (a.isDone()) continue;
                    double ax = a.getRenderX(SIZE, GAP);
                    double ay = a.getRenderY(SIZE, GAP);
                    drawSlideTile(g2, a, ax, ay);
                }
            }

            // 游戏结束遮罩（用缓存标志 + 颜色，避免每帧扫盘）
            if (gameOverCached && !animating) {
                g2.setColor(OVERLAY_COLOR);
                g2.fillRoundRect(0, 0, PANEL_SIZE, PANEL_SIZE, 16, 16);
                g2.setColor(OVERLAY_TEXT);
                g2.setFont(FONT_TITLE);
                String msg = "Game Over!";
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(msg, (PANEL_SIZE - fm.stringWidth(msg)) / 2, PANEL_SIZE / 2);
            }
        }

        /** 选择对应字号的 FontMetrics（fallback 到 g.getFontMetrics 防止 addNotify 时 metrics 未就绪） */
        private FontMetrics pickMetrics(Graphics2D g, Font font) {
            int sz = font.getSize();
            int idx;
            if (sz >= 46)      idx = 0;
            else if (sz >= 40) idx = 1;
            else if (sz >= 34) idx = 2;
            else               idx = 3;
            FontMetrics fm = cachedMetrics[idx];
            return fm != null ? fm : g.getFontMetrics(font);
        }

        /** 绘制静态 tile (含 POP scale / MERGE glow) */
        private void drawTile(Graphics2D g, int r, int c, int x, int y,
                              AnimationEngine.Anim anim) {
            Grid tile = grids[r][c];

            boolean isPop = anim != null && anim.type == AnimationEngine.Type.POP;
            boolean isMerge = anim != null && anim.type == AnimationEngine.Type.MERGE;

            if (isPop && anim.getScale() < 0.05) return;

            float glow = isMerge ? anim.getGlow() : 0f;

            // 阴影
            g.setColor(SHADOW_COLOR);
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

            // 发光叠加 (MERGE) — 复用单个 Color 实例的最简办法是只在需要时新建（每帧 alpha 不同）
            if (glow > 0.01f) {
                g.setColor(new Color(1f, 1f, 1f, glow * 0.6f));
                g.fillRoundRect(x, y, SIZE, SIZE, ARC, ARC);
            }

            // 高数值渐变光泽 (>= 1024)
            if (tile.value >= 1024 && !tile.isEmpty()) {
                g.setColor(HIGHLIGHT_COLOR);
                g.fillRoundRect(x, y + SIZE / 2, SIZE, SIZE / 2, ARC, ARC);
            }

            if (tile.isEmpty()) return;

            // 文字
            g.setColor(tile.getForeground());
            Font font = tile.getCheckFont();
            if (isMerge && glow > 0.3f) {
                font = font.deriveFont(font.getSize() * (1f + glow * 0.15f));
            }
            g.setFont(font);
            FontMetrics fm = pickMetrics(g, font);
            String text = String.valueOf(tile.value);
            int tx = x + (SIZE - fm.stringWidth(text)) / 2;
            int ty = y + (SIZE - fm.getHeight()) / 2 + fm.getAscent();
            g.drawString(text, tx, ty);
        }

        /** 在插值位置绘制滑动中的 tile（不再 new Grid，颜色查表） */
        private void drawSlideTile(Graphics2D g, AnimationEngine.Anim a, double ax, double ay) {
            int x = (int) ax;
            int y = (int) ay;

            // 阴影
            g.setColor(SHADOW_COLOR);
            g.fillRoundRect(x + 3, y + 3, SIZE, SIZE, ARC, ARC);

            // 背景：用静态查表函数得到颜色（避免 new Grid）
            g.setColor(Grid.bgColorFor(a.value));
            g.fillRoundRect(x, y, SIZE, SIZE, ARC, ARC);

            // 高数值光泽
            if (a.value >= 1024) {
                g.setColor(HIGHLIGHT_COLOR);
                g.fillRoundRect(x, y + SIZE / 2, SIZE, SIZE / 2, ARC, ARC);
            }

            // 文字
            g.setColor(Grid.fgColorFor(a.value));
            Font font = Grid.fontFor(a.value);
            g.setFont(font);
            FontMetrics fm = pickMetrics(g, font);
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
                gameOverCached = checkGameOver();
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
