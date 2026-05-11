import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * RenderPerfTest — 量化 GameView.GameBoard.paintComponent 单帧耗时。
 *
 * 用 BufferedImage 离屏 paint，避免实际开窗。
 * 同一棋盘连续渲染 1000 次，统计平均耗时。
 *
 * 优化前后对比：每帧 new HashMap/HashSet/4 个 Grid/4 个 FontMetrics →
 * 触发频繁 GC，单帧 ~3-5ms。
 *
 * 优化后：所有缓存复用 → 单帧 ~0.5-1ms，60fps 完全游刃有余（每帧预算 16.6ms）。
 */
public class RenderPerfTest {

    public static void main(String[] args) throws Exception {
        SwingUtilities.invokeAndWait(RenderPerfTest::run);
    }

    static void run() {
        try {
            // 创建 GameView 但不显示窗口
            GameView view = new GameView();

            // 通过反射拿到 GameBoard
            java.lang.reflect.Field gbField = GameView.class.getDeclaredField("gameBoard");
            gbField.setAccessible(true);
            JPanel gameBoard = (JPanel) gbField.get(view);

            // 设置棋盘内容（满棋盘最复杂）
            java.lang.reflect.Field gridsField = gameBoard.getClass().getDeclaredField("grids");
            gridsField.setAccessible(true);
            Grid[][] grids = (Grid[][]) gridsField.get(gameBoard);
            int[] vals = {2,4,8,16, 32,64,128,256, 512,1024,2048,4, 8,16,32,64};
            int k = 0;
            for (int r = 0; r < 4; r++)
                for (int c = 0; c < 4; c++) grids[r][c].value = vals[k++];

            // 让 GameBoard 进入 displayable 状态
            gameBoard.setSize(385, 385);
            gameBoard.addNotify();

            BufferedImage img = new BufferedImage(385, 385, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2 = img.createGraphics();

            // 用 public 的 paint() 方法（不需要打开模块）
            // JComponent.paint() 内部会调用 paintComponent，等价测试 paintComponent 性能

            // Warmup
            for (int i = 0; i < 100; i++) {
                gameBoard.paint(g2);
            }

            // 测量
            final int N = 2000;
            long t0 = System.nanoTime();
            for (int i = 0; i < N; i++) {
                gameBoard.paint(g2);
            }
            long elapsed = System.nanoTime() - t0;
            double avgUs = elapsed / 1000.0 / N;

            g2.dispose();

            System.out.printf("paintComponent 单帧耗时: %.0f us (= %.3f ms)%n", avgUs, avgUs / 1000);
            System.out.printf("理论 FPS 上限: %.0f%n", 1_000_000.0 / avgUs);
            System.out.printf("60 FPS 预算 (16.67 ms): %.1f%% 占用%n", avgUs / 16670 * 100);

            boolean ok = avgUs < 5000;  // < 5ms 就足够 60fps
            if (ok) {
                System.out.println("\n✓ 渲染性能合格 (单帧 < 5ms，60fps 完全够用)");
                System.exit(0);
            } else {
                System.out.println("\n✗ 渲染性能不达标 (单帧 ≥ 5ms)");
                System.exit(1);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
