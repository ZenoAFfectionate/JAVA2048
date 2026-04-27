/**
 * 测试套件入口 — 依次运行全部测试并汇总结果
 *
 * 设计原则:
 *   每个测试模块提供 runAll() 方法 (不触发 System.exit),
 *   暴露 public static passed/failed 计数器,
 *   TestAll 依次调用各模块的 runAll() 后汇总报告。
 *
 * 这样既支持单独运行 (通过 main 方法 + System.exit),
 * 也支持套件统一汇总。
 *
 * 编译: javac -encoding UTF-8 -cp src -d out src/*.java tests/*.java
 * 运行: java -cp out TestAll
 */
public class TestAll {

    public static void main(String[] args) {
        System.out.println("╔══════════════════════════════════╗");
        System.out.println("║      2048 Game - Test Suite         ║");
        System.out.println("╚══════════════════════════════════╝");
        System.out.println();

        int totalPassed = 0, totalFailed = 0;

        // --- Grid 测试 ---
        System.out.println("━━━ Grid.java 单元测试 ━━━");
        System.out.println();
        GridTest.runAll();
        totalPassed += GridTest.passed;
        totalFailed += GridTest.failed;
        System.out.println();

        // --- 游戏逻辑测试 ---
        System.out.println("━━━ 游戏逻辑测试 ━━━");
        System.out.println();
        GameLogicTest.runAll();
        totalPassed += GameLogicTest.passed;
        totalFailed += GameLogicTest.failed;
        System.out.println();

        // --- Utils 测试 ---
        System.out.println("━━━ Utils 工具类测试 ━━━");
        System.out.println();
        UtilsTest.runAll();
        totalPassed += UtilsTest.passed;
        totalFailed += UtilsTest.failed;
        System.out.println();

        // --- 汇总 ---
        System.out.println("╔══════════════════════════════════╗");
        if (totalFailed == 0) {
            System.out.printf("║  ALL %d TESTS PASSED                ║%n", totalPassed);
        } else {
            System.out.printf("║  %d passed, %d failed               ║%n", totalPassed, totalFailed);
        }
        System.out.println("╚══════════════════════════════════╝");

        if (totalFailed > 0) {
            System.exit(1);
        }
    }
}
