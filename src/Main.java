import javax.swing.SwingUtilities;

/**
 * 程序入口 - 在 EDT (事件调度线程) 上启动 GUI
 */
public class Main {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new GameView().showView());
    }
}
