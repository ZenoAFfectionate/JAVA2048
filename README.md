# 2048 — Java 课程设计

---

## 项目背景

近年来，移动手游成为电子游戏产业的重要一环。作为手游史上的经典之作，**2048** 是一款兼具趣味性与益智性的开山鼻祖级游戏。

本项目基于 Java Swing 实现完整的 2048 游戏，涵盖图形界面、键盘交互、计分、存档读档、音效、动画系统和五种 AI 算法。

## 游戏规则

在 4×4 的 16 宫格中，使用 **↑ ↓ ← →** 方向键操作：

- 所有方块向指定方向滑动
- 相邻且相同的数字合并为它们的和
- 每次操作后在随机空格生成 2（90%）或 4（10%）
- 当 16 宫格填满且四个方向都无法合并时，游戏结束
- 目标是合并出 **2048** 并获得尽可能高的分数

## 快捷键

| 按键 | 功能 |
|------|------|
| ↑ ↓ ← → | 方向移动 |
| Esc | 重新开始 |
| M | 开关音效 |
| S | 存档 |
| L | 读档 |
| T | AI 智能提示 |

## 项目结构

```
JAVA2048/
├── README.md
├── result.txt                        # 算法对比实验结果
├── src/
│   ├── Main.java                     # 程序入口 (EDT 启动)
│   ├── Grid.java                     # 格子类 — 数值、颜色、字体、递归移动
│   ├── GameView.java                 # 主界面 + GameBoard + 动画渲染
│   ├── AnimationEngine.java          # 动画引擎 — 60fps Timer + 缓动函数
│   ├── PlaySound.java                # 音效播放线程
│   ├── Utils.java                    # XML 存档 + AI 提示 + 共用工具函数
│   └── ExperimentRunner.java         # 算法对比实验入口
├── algo/                             # AI 算法实现
│   ├── FixHeuristic.java             # 固定启发式 — 角落锚定 + 单调性 + 平滑性
│   ├── WeightedGreedy.java           # 加权贪心 — 蛇形权重矩阵
│   ├── Expectmax.java                # 期望最大搜索 — 深度可配 Expectimax
│   └── MCTS.java                     # 蒙特卡洛树搜索 — UCT + 贪心 Rollout
├── tests/
│   ├── TestAll.java                  # 测试套件入口 (汇总全部测试)
│   ├── GridTest.java                 # Grid 单元测试
│   ├── GameLogicTest.java            # 游戏逻辑测试
│   ├── UtilsTest.java                # Utils 工具测试
│   ├── AnimationEngineTest.java      # 动画引擎单元测试 (34 用例)
│   ├── AnimationIntegrationTest.java # 动画集成测试 (19 用例)
│   ├── FixHeuristicTest.java         # FixHeuristic 测试 (11 用例)
│   ├── WeightedGreedyTest.java       # WeightedGreedy 测试 (11 用例)
│   ├── ExpectmaxTest.java            # Expectmax 测试 (13 用例)
│   └── MCTSTest.java                 # MCTS 测试 (28 用例)
├── data/
│   └── test_save.xml                 # 测试用存档样例
├── res/
│   ├── move.wav                      # 移动音效 (可选)
│   └── merge.wav                     # 合并音效 (可选)
├── out/                              # 编译输出目录 (自动生成)
└── docs/                             # 设计文档
    └── superpowers/
        ├── specs/                    # 设计规格书
        └── plans/                    # 实现计划
```

## 系统设计

```
┌──────────────────────────────┐
│          GameView             │
│  ┌────────────────────────┐  │
│  │     标题 & 分数区       │  │
│  ├────────────────────────┤  │
│  │      GameBoard         │  │
│  │   ┌──┬──┬──┬──┐       │  │
│  │   │  │  │  │  │       │  │
│  │   ├──┼──┼──┼──┤       │  │
│  │   │  │  │  │  │       │  │
│  │   ├──┼──┼──┼──┤       │  │
│  │   │  │  │  │  │       │  │
│  │   ├──┼──┼──┼──┤       │  │
│  │   │  │  │  │  │       │  │
│  │   └──┴──┴──┴──┘       │  │
│  └────────────────────────┘  │
│  ┌────────────────────────┐  │
│  │    按键提示 & 菜单栏    │  │
│  └────────────────────────┘  │
└──────────────────────────────┘
```

### 类设计

| 类 | 职责 |
|----|------|
| `Main` | 程序入口，在 EDT 线程启动 GUI |
| `Grid` | 单个格子的数据与行为：数值、配色、字体、四方向递归移动 |
| `GameView` | 顶层窗口：布局、菜单、分数显示、事件监听 |
| `GameView.GameBoard` | 游戏面板：棋盘状态、移动合并、胜负判定、动画渲染、键盘响应、存档 |
| `AnimationEngine` | 动画引擎：60fps Timer 驱动，管理 tile 滑动/弹出/合并动画生命周期 |
| `PlaySound` | 异步线程播放 WAV 音效 |
| `Utils` | 工具函数：XML 读写、AI Expectimax 搜索、棋盘 save/restore 工具 |
| `ExperimentRunner` | 算法实验：批量运行多局游戏，对比四种 AI 的多维度指标 |

## 核心设计

### 递归移动算法

摒弃传统的迭代方式，采用**递归**实现方块移动：

1. 从移动方向远端开始遍历每个非空格子
2. 检查相邻格子：若为空则交换并递归推进；若值相同且未合并则合并
3. `merge` 标志防止同一次操作中的连环合并

```java
// Grid.java — 以向上移动为例
public int moveUp(Grid[][] grids, int i, int j) {
    if (i == 0) return 0;           // 递归基
    Grid prev = grids[i - 1][j];
    if (prev.isEmpty()) {            // 空格 → 滑入并递归
        prev.value = this.value;
        this.value = 0;
        return prev.moveUp(grids, i - 1, j);
    } else if (prev.value == this.value && !prev.merge) {
        prev.merge = true;           // 合并
        prev.value *= 2;
        this.value = 0;
        return prev.value;
    }
    return 0;
}
```

### 动画系统

基于 `javax.swing.Timer` 的 60fps 动画引擎，提供三种动画类型：

| 动画 | 触发 | 缓动函数 | 时长 |
|------|------|---------|------|
| 滑动 (SLIDE) | 方块移动到新位置 | easeOutCubic | 120ms |
| 弹出 (POP) | 新方块生成 | easeOutBack (弹性过冲) | 180ms |
| 合并闪烁 (MERGE) | 方块合并 | easeInOutCubic | 150ms |

关键设计：
- **动画非阻塞** — 动画期间用户输入缓存在 `pendingKeyCode`，动画结束后执行
- **输入缓冲** — 多次连按仅保留最后一次，避免输入堆积
- **两遍渲染** — 第一遍绘制静态 tile + POP/MERGE 效果，第二遍绘制 SLIDE tile 的插值位置
- **视觉增强** — tile 阴影、高数值渐变光泽 (≥1024)、棋盘分隔线、合并发光

### AI 智能提示 — 2-ply Expectimax

相比原版单步贪心搜索，优化后的提示使用 **两层 Expectimax** 算法：

```
对每个可行方向 dir:
  1. 在副本上模拟移动，得到 immediate_score
  2. 枚举所有空格:
     - 填入 2 (权重 0.9):  继续模拟四方向，取 max_score
     - 填入 4 (权重 0.1):  继续模拟四方向，取 max_score
  3. expected_score = immediate_score + Σ(weight × max_score)
  4. 选择 expected_score 最高的方向
```

这更贴近 2048 的真实博弈结构 —— 玩家（移动）对抗随机环境（落子）。

### 数字配色方案

遵循"色相统一、色调渐变、突出强调"原则：

| 数值 | 背景色 | 前景色 |
|------|--------|--------|
| 0 | `#cdc1b4` | — (隐藏) |
| 2 | `#eee4da` | 黑色 |
| 4 | `#ede0c8` | 黑色 |
| 8 | `#f2b179` | 白色 |
| 16 | `#f59563` | 白色 |
| 32 | `#f67c5f` | 白色 |
| 64 | `#f65e3b` | 白色 |
| 128 | `#edcf72` | 白色 |
| 256 | `#edcc61` | 白色 |
| 512 | `#edc850` | 白色 |
| 1024 | `#edc53f` | 白色 |
| 2048 | `#edc22e` | 白色 |
| ≥4096 | `#248c51` | 白色 |

---

## AI 算法详解

### 1. FixHeuristic — 固定启发式算法

**核心原理**：复刻人类高手的三大黄金规则，通过加权评分函数量化局面优劣。

| 规则 | 说明 | 权重 |
|------|------|------|
| 角落锚定 | 最大值固定在角落，禁止移动后离开 | W=120 |
| 单调性 | 行/列按从大到小梯度排列 | W=40 |
| 平滑性 | 相邻格子 log2 差值尽可能小 | W=-22 |
| 空格奖励 | 空格越多选择越多 | W=280 |
| 合并潜力 | 相邻相等值 bonus | W=25 |

**流程**：遍历 4 个方向 → 模拟移动 → 对结果棋盘打分 → 选评分最高方向。

### 2. WeightedGreedy — 加权贪心算法

**核心原理**：蛇形权重矩阵强制实现单调性和角落锚定。

```
权重矩阵:        [[15, 14, 13, 12],
                  [8,  9,  10, 11],
                  [7,  6,  5,  4],
                  [0,  1,  2,  3]]
```

**流程**：遍历 4 个方向 → 模拟移动 → 计算 Σ(tileValue × weight) → 选加权和最大方向。

### 3. Expectmax — 期望最大搜索

**核心原理**：2048 是随机博弈（对手是随机环境），采用 Expectimax 而非 Minimax。

- **Max 节点**：遍历 4 个方向，选择最大化期望得分的动作
- **Chance 节点**：枚举空格生成 2/4 的可能，计算期望值
- **搜索深度**：固定 2-ply，智能采样 ≤4 个空格
- **优化**：save/restore 原地操作、自适应采样

### 4. MCTS — 蒙特卡洛树搜索

**核心原理**：基于 UCT 公式的树搜索 + 贪心 Rollout。

五项关键优化：

| 优化 | 说明 |
|------|------|
| 贪心 Rollout | 用单调性+平滑度+角落锚定的启发式引导模拟，非纯随机 |
| 预过滤 | 扩展节点前过滤无效方向，分支因子 4→2~3 |
| 数组快照 | `int[][]` + `System.arraycopy`，避免 GC 压力 |
| 深度限制 | Rollout 最长 25 步，截断后用启发式评估 |
| 树结构 | 完整 Select→Expand→Simulate→Backpropagate 循环 |

---

## 算法实验结果 (1000 局/算法)

```
┌────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│      Metric        │ FixHeuristic │WeightedGreedy│  Expectmax   │     MCTS     │
├────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Mean Score         │        4,513 │        1,397 │       21,620 │        1,938 │
│ Max Score          │       15,248 │        5,456 │       76,892 │        5,796 │
│ Min Score          │          224 │          144 │        5,440 │          264 │
│ Median Score       │        4,106 │        1,288 │       16,766 │        1,924 │
│ 2048 Rate (%)      │         0.0% │         0.0% │        43.7% │         0.0% │
│ Mean Moves         │        2,888 │        2,696 │        4,417 │          193 │
│ Max Tile           │         1024 │          512 │         4096 │          512 │
│ Mean Time/Move(ms) │        0.001 │        0.001 │        0.019 │        0.371 │
│ Total Time(s)      │          3.1 │          1.5 │         85.1 │         71.4 │
└────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

### 结果分析

- **Expectmax 碾压式领先**：平均得分 21,620，43.7% 达成 2048，最高得分 76,892，最高 tile 4096。深度搜索带来的战略远见显著优于其他方法。
- **FixHeuristic 第二**：平均得分 4,513，最高 tile 1024。速度极快 (0.001ms/步)，适合实时应用。
- **MCTS 表现偏弱**：平均得分 1,938，未达成 2048。尽管有贪心 Rollout 和完整树结构，但模拟次数不足 (8~20 迭代) 限制了搜索质量。增加迭代次数可提升表现，但会显著增加运行时间。
- **WeightedGreedy 最弱**：固定权重矩阵缺乏对具体局面的适应能力，平均得分仅 1,397。

---

## 环境准备

### 系统要求

- **JDK 8 或更高版本** (编译需要 `javac`，运行仅需 `java`)
- 无外部依赖 —— 仅使用 Java 标准库 (`javax.swing`, `javax.sound`, `java.io` 等)
- 操作系统: Windows / Linux / macOS 均可

### 检查 Java 环境

```bash
java -version    # 应显示 1.8.0 或更高
javac -version   # 确认 JDK 已安装 (非 JRE)
```

---

## 编译与运行

### 运行游戏

```bash
cd /home/kemove/Courses/JAVA2048

# 编译
javac -encoding UTF-8 -d out src/*.java

# 运行
java -cp out Main
```

### 运行算法实验

```bash
# 编译（含算法模块）
javac -encoding UTF-8 -d out src/*.java algo/*.java

# 运行实验 (结果输出到 result.txt)
java -cp out ExperimentRunner
```

### 单独运行某算法

```java
// 获取最优方向 (0=UP, 1=DOWN, 2=LEFT, 3=RIGHT)
int dir = Expectmax.getBestDirection(grids);
int dir = FixHeuristic.getBestDirection(grids);
int dir = WeightedGreedy.getBestDirection(grids);
int dir = MCTS.getBestDirection(grids);

// MCTS 可调迭代次数
MCTS.setIterations(100);
```

---

## 测试

测试覆盖七大模块，共 153+ 用例：

| 模块 | 文件 | 测试重点 | 用例数 |
|------|------|----------|--------|
| **Grid** | `GridTest.java` | 构造、拷贝、颜色映射、字体、merge 标志、四方向移动、递归 | 43 |
| **GameLogic** | `GameLogicTest.java` | 空格检测、方块生成、计分、胜负判定、XML 存档解析 | 12 |
| **Utils** | `UtilsTest.java` | XML 读写、AI 提示、深拷贝独立性 | 12 |
| **AnimationEngine** | `AnimationEngineTest.java` | 缓动函数、Anim 进度、引擎生命周期 | 34 |
| **Animation** | `AnimationIntegrationTest.java` | 输入缓冲、动画与游戏逻辑一致性 | 19 |
| **FixHeuristic** | `FixHeuristicTest.java` | 角落锚定、可合并、状态保持、确定性 | 11 |
| **WeightedGreedy** | `WeightedGreedyTest.java` | 权重矩阵、角落偏向、加权和、状态保持 | 11 |
| **Expectmax** | `ExpectmaxTest.java` | 合并偏好、深度搜索、性能、状态保持 | 13 |
| **MCTS** | `MCTSTest.java` | 滑动逻辑、合法方向、快照拷贝、迭代设定、状态保持 | 28 |

### 运行测试

```bash
# 编译全部
javac -encoding UTF-8 -d out src/*.java algo/*.java tests/*.java

# 运行全部
java -cp out TestAll

# 单独运行
java -cp out MCTSTest
java -cp out AnimationEngineTest
```

---

## 已实现功能

### 核心玩法
- [x] 4×4 棋盘，随机生成 2/4
- [x] 四方向递归移动与合并
- [x] merge 标志防止连环合并
- [x] 实时计分与最高分记录
- [x] 游戏结束判定
- [x] 达成 2048 胜利提示

### UI/UX
- [x] 经典暖色系配色
- [x] 数字自适应字体大小
- [x] 抗锯齿渲染 (LCD HRGB + FractionalMetrics)
- [x] Game Over / Victory 半透明遮罩
- [x] 实时音效状态指示
- [x] 底部按键提示栏
- [x] 方块滑动动画 (easeOutCubic, 120ms)
- [x] 新方块弹出动画 (easeOutBack, 180ms)
- [x] 合并闪烁光晕 (easeInOutCubic, 150ms)
- [x] Tile 阴影 + 高数值渐变光泽 (≥1024)
- [x] 棋盘分隔线
- [x] 输入缓冲 (动画期间缓存方向键)

### 辅助功能
- [x] 存档/读档（XML 格式）
- [x] 音效开关（移动 + 合并）
- [x] AI 智能提示（2-ply Expectimax）
- [x] 退出确认对话框
- [x] Game 菜单（规则/关于/退出）

### AI 算法
- [x] FixHeuristic — 固定启发式 (角落锚定 + 单调性 + 平滑性)
- [x] WeightedGreedy — 蛇形权重矩阵贪心
- [x] Expectmax — 深度 2-ply Expectimax + 智能采样 + save/restore
- [x] MCTS — UCT 树搜索 + 贪心 Rollout + 五项优化
- [x] 算法实验框架 (10/100/1000/10000 局) + 多维度指标对比

---

## 设计心得

本次课程设计的核心是基于 Java Swing 开发经典游戏 2048。在实现过程中遇到了连环合并、数字覆盖、分数累加遗漏等问题，需要对比运行结果与预期行为，经过仔细分析才能定位错误。

关键设计决策：

- **递归代替迭代**：使代码更简洁，逻辑更清晰，天然避免了一次操作中的连环合并问题。
- **解耦移动与合并**：整体定向移动由 GameBoard 负责，单个格子的移动合并由 Grid 实现，提高了代码复用性。
- **深拷贝隔离 AI 搜索**：AI 模拟必须在独立副本上进行，避免污染当前游戏状态。
- **动画非阻塞设计**：动画期间输入缓存，动画结束自动执行，保证交互流畅不丢键。
- **int[][] 快照优化**：MCTS 全部在原始数组上操作，避免 Grid 对象分配和 GC，大幅提升搜索速度。

通过本次课程设计，不仅锻炼了问题划分与求解、算法设计与分析的能力，更在实践中加深了对 Java 面向对象编程思想的理解。四种 AI 算法的实现和对比实验，更是深入探索了启发式搜索、概率搜索和随机模拟搜索在实际博弈问题中的应用。

## 参考文献

1. [美] 埃尔克. *Java 核心技术*. 机械工业出版社, 2012
2. [美] 埃尔克. *Java 编程思想*. 机械工业出版社, 2007
3. [美] 科尔曼. *算法导论(第三版)*. 机械工业出版社, 2012
4. [美] 罗素. *人工智能(现代方法)*. 人民邮电出版社, 2022
5. 刘彦君, 金飞虎. *JavaEE 开发技术与案例教程*. 人民邮电出版社, 2014
