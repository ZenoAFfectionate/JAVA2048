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

        final Grid[][] grids = new Grid[ROWS][COLS];

        GameBoard() {
            restart();
            addKeyListener(this);
            setPreferredSize(new Dimension(PANEL_SIZE, PANEL_SIZE));
        }

        void restart() {
            resetScore();
            hasWon = false;
            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    grids[r][c] = new Grid();
            spawnTile();
            spawnTile();
            refreshUI();
            repaint();
            requestFocusInWindow();
        }

        // ---- 棋盘操作 ----

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

        /** 随机空格生成 2 (90%) 或 4 (10%) */
        private void spawnTile() {
            List<int[]> empties = emptyCells();
            if (empties.isEmpty()) return;
            int[] cell = empties.get(RNG.nextInt(empties.size()));
            grids[cell[0]][cell[1]].value = RNG.nextDouble() < 0.9 ? 2 : 4;
        }

        // ---- 方向移动 (返回值: 得分; -1 表示无移动) ----

        private int doMoveUp() {
            clearMerge();
            int s = 0;
            boolean moved = false;
            for (int c = 0; c < COLS; c++)
                for (int r = 1; r < ROWS; r++)
                    if (!grids[r][c].isEmpty()) {
                        int v = grids[r][c].moveUp(grids, r, c);
                        if (v >= 0) { s += v; moved = true; }
                    }
            return moved ? s : -1;
        }

        private int doMoveDown() {
            clearMerge();
            int s = 0;
            boolean moved = false;
            for (int c = 0; c < COLS; c++)
                for (int r = ROWS - 2; r >= 0; r--)
                    if (!grids[r][c].isEmpty()) {
                        int v = grids[r][c].moveDown(grids, r, c);
                        if (v >= 0) { s += v; moved = true; }
                    }
            return moved ? s : -1;
        }

        private int doMoveLeft() {
            clearMerge();
            int s = 0;
            boolean moved = false;
            for (int r = 0; r < ROWS; r++)
                for (int c = 1; c < COLS; c++)
                    if (!grids[r][c].isEmpty()) {
                        int v = grids[r][c].moveLeft(grids, r, c);
                        if (v >= 0) { s += v; moved = true; }
                    }
            return moved ? s : -1;
        }

        private int doMoveRight() {
            clearMerge();
            int s = 0;
            boolean moved = false;
            for (int r = 0; r < ROWS; r++)
                for (int c = COLS - 2; c >= 0; c--)
                    if (!grids[r][c].isEmpty()) {
                        int v = grids[r][c].moveRight(grids, r, c);
                        if (v >= 0) { s += v; moved = true; }
                    }
            return moved ? s : -1;
        }

        // ---- 键盘 ----

        @Override
        public void keyPressed(KeyEvent e) {
            int moveScore;
            switch (e.getKeyCode()) {
                case KeyEvent.VK_UP:    moveScore = doMoveUp(); break;
                case KeyEvent.VK_DOWN:  moveScore = doMoveDown(); break;
                case KeyEvent.VK_LEFT:  moveScore = doMoveLeft(); break;
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
            spawnTile();
            refreshUI();
            repaint();

            if (!hasWon && checkWin()) {
                hasWon = true;
                int res = JOptionPane.showConfirmDialog(frame,
                        "恭喜！你达成了 2048！\n是否继续游戏？",
                        "Victory!", JOptionPane.YES_NO_OPTION);
                if (res != JOptionPane.YES_OPTION) restart();
            }

            if (checkGameOver()) {
                refreshUI();
                repaint();
                int res = JOptionPane.showConfirmDialog(frame,
                        "Game Over!  得分: " + score + "\n是否重新开始？",
                        "Game Over", JOptionPane.YES_NO_OPTION);
                if (res == JOptionPane.YES_OPTION) restart();
            }
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
            g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

            // 一次计算所有需要的 FontMetrics 并缓存
            Font[] fonts = {
                new Grid(2).getCheckFont(),
                new Grid(16).getCheckFont(),
                new Grid(256).getCheckFont(),
                new Grid(1024).getCheckFont(),
            };
            FontMetrics[] fms = new FontMetrics[4];
            for (int i = 0; i < 4; i++) fms[i] = g2.getFontMetrics(fonts[i]);

            for (int r = 0; r < ROWS; r++)
                for (int c = 0; c < COLS; c++)
                    drawTile(g2, r, c, fms);

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

        private void drawTile(Graphics2D g, int r, int c, FontMetrics[] fms) {
            Grid tile = grids[r][c];
            int x = GAP + (GAP + SIZE) * c;
            int y = GAP + (GAP + SIZE) * r;

            g.setColor(tile.getBackground());
            g.fillRoundRect(x, y, SIZE, SIZE, ARC, ARC);

            if (tile.isEmpty()) return;

            g.setColor(tile.getForeground());
            Font font = tile.getCheckFont();
            g.setFont(font);

            // 根据字号选择缓存的 FontMetrics
            FontMetrics fm;
            int sz = font.getSize();
            if (sz == 46)      fm = fms[0];
            else if (sz == 40) fm = fms[1];
            else if (sz == 34) fm = fms[2];
            else               fm = fms[3];

            String text = String.valueOf(tile.value);
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
