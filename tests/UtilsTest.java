import java.io.*;
import java.util.*;

/**
 * Utils 工具类测试 — XML 读写、AI 提示正确性、深拷贝独立性
 */
public class UtilsTest {

    public static int passed = 0, failed = 0;

    public static void main(String[] args) {
        runAll();
        if (failed > 0) System.exit(1);
    }

    public static void runAll() {
        passed = 0; failed = 0;
        System.out.println("========================================");
        System.out.println("  Utils 工具类测试");
        System.out.println("========================================\n");

        testWriteAndReadXML();
        testTipsReturnsValidAction();
        testDeepCopyIndependence();
        testAISuggestionOnMergeableBoard();
        testAISuggestionOnEmptyBoard();

        System.out.println("\n========================================");
        System.out.printf("  Utils: %d passed, %d failed%n", passed, failed);
        System.out.println("========================================");
    }

    static void check(String name, boolean cond) {
        if (cond) { passed++; System.out.println("  ✓ " + name); }
        else      { failed++; System.out.println("  ✗ " + name + "  <-- FAILED"); }
    }

    static Grid[][] emptyBoard() {
        Grid[][] g = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                g[r][c] = new Grid(0);
        return g;
    }

    // ---- XML 读写 ----

    static void testWriteAndReadXML() {
        System.out.println("[XML 写入与解析]");
        // 使用系统临时目录而非项目根目录的 "data/"，避免在工作区产生残留。
        // 测试结束自动 deleteOnExit() 清理，保证幂等。
        File tmp;
        try {
            tmp = File.createTempFile("java2048-test-save-", ".xml");
            tmp.deleteOnExit();
        } catch (IOException e) {
            System.out.println("  ✗ 创建临时文件失败：" + e.getMessage());
            failed++;
            return;
        }
        String testFile = tmp.getAbsolutePath();

        List<String> keys = new ArrayList<>();
        keys.add("testKey1");
        keys.add("testKey2");
        List<String> vals = new ArrayList<>();
        vals.add("hello");
        vals.add("42");

        try {
            Utils.writeXML(keys, vals, testFile);
        } catch (FileNotFoundException e) {
            System.out.println("  ✗ write XML exception: " + e.getMessage());
            failed++;
            return;
        }
        check("write XML without error", true);

        File f = new File(testFile);
        check("XML file created", f.exists());

        try (BufferedReader br = new BufferedReader(new FileReader(testFile))) {
            String l1 = br.readLine();
            String l2 = br.readLine();
            check("line 1: <testKey1>hello</testKey1>", "<testKey1>hello</testKey1>".equals(l1));
            check("line 2: <testKey2>42</testKey2>", "<testKey2>42</testKey2>".equals(l2));
        } catch (IOException e) {
            System.out.println("  ✗ read XML exception: " + e.getMessage());
            failed++;
            return;
        }
        check("read XML without error", true);

        // 显式清理（除了 deleteOnExit 兜底）
        if (f.exists() && !f.delete()) {
            f.deleteOnExit();
        }
    }

    // ---- AI 提示 ----

    static void testTipsReturnsValidAction() {
        System.out.println("[AI提示 - 返回合法方向]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;
        g[0][1].value = 2;

        String best = Utils.getBestMove(g);
        String[] valid = {"Move Up", "Move Down", "Move Left", "Move Right"};
        boolean ok = false;
        for (String v : valid) if (v.equals(best)) { ok = true; break; }
        check("getBestMove returns valid direction: " + best, ok);
    }

    static void testAISuggestionOnMergeableBoard() {
        System.out.println("[AI提示 - 可合并棋盘应推荐合并方向]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;
        g[0][1].value = 2;
        g[0][2].value = 4;
        g[0][3].value = 8;

        String best = Utils.getBestMove(g);
        // 向左或向右可以合并 2+2
        boolean ok = best.equals("Move Left") || best.equals("Move Right");
        check("should suggest Left or Right to merge 2+2: " + best, ok);
    }

    static void testAISuggestionOnEmptyBoard() {
        System.out.println("[AI提示 - 空棋盘任意方向均有效]");
        Grid[][] g = emptyBoard();
        g[0][0].value = 2;

        String best = Utils.getBestMove(g);
        String[] valid = {"Move Up", "Move Down", "Move Left", "Move Right"};
        boolean ok = false;
        for (String v : valid) if (v.equals(best)) { ok = true; break; }
        check("empty board returns valid direction: " + best, ok);
    }

    // ---- 深拷贝 ----

    static void testDeepCopyIndependence() {
        System.out.println("[深拷贝独立性]");
        Grid[][] orig = emptyBoard();
        orig[0][0].value = 2;
        orig[1][1].value = 8;
        orig[3][3].value = 1024;

        Grid[][] copy = new Grid[4][4];
        for (int r = 0; r < 4; r++)
            for (int c = 0; c < 4; c++)
                copy[r][c] = orig[r][c].copy();

        check("copy[0][0] = 2", copy[0][0].value == 2);
        check("copy[3][3] = 1024", copy[3][3].value == 1024);

        copy[0][0].value = 999;
        check("orig unchanged after copy mutation", orig[0][0].value == 2);
        check("copy mutated independently", copy[0][0].value == 999);
    }
}
