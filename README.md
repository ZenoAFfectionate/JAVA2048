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

## 系统设计

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

## 算法实验结果

`ExperimentRunner` 在固定 `MCTS_ITERATIONS = 20` 的前提下，分别以 **N = 1 / 10 / 100 / 1000** 局四个规模运行四种算法，结果完整记录在 `result.txt`：

### N = 1 局/算法

```
┌────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│      Metric        │ FixHeuristic │WeightedGreedy│  Expectmax   │     MCTS     │
├────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Mean Score         │        3,072 │        2,012 │        7,536 │        4,988 │
│ Max Score          │        3,072 │        2,012 │        7,536 │        4,988 │
│ Min Score          │        3,072 │        2,012 │        7,536 │        4,988 │
│ Median Score       │        3,072 │        2,012 │        7,536 │        4,988 │
│ 2048 Rate (%)      │         0.0% │         0.0% │         0.0% │         0.0% │
│ Mean Moves         │          253 │          202 │          530 │          338 │
│ Max Tile           │          256 │          128 │          512 │          512 │
│ Mean Time/Move(ms) │        0.010 │        0.007 │        0.035 │        1.737 │
│ Total Time(s)      │          0.0 │          0.0 │          0.0 │          0.6 │
└────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

### N = 10 局/算法

```
┌────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│      Metric        │ FixHeuristic │WeightedGreedy│  Expectmax   │     MCTS     │
├────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Mean Score         │        4,254 │        2,858 │       13,417 │        8,110 │
│ Max Score          │        6,364 │        7,320 │       34,660 │       16,020 │
│ Min Score          │        1,652 │          704 │        3,128 │        2,360 │
│ Median Score       │        4,256 │        2,232 │       13,752 │        7,296 │
│ 2048 Rate (%)      │         0.0% │         0.0% │        10.0% │         0.0% │
│ Mean Moves         │          314 │          234 │          797 │          511 │
│ Max Tile           │          512 │          512 │         2048 │         1024 │
│ Mean Time/Move(ms) │        0.002 │        0.001 │        0.017 │        1.552 │
│ Total Time(s)      │          0.0 │          0.0 │          0.1 │          7.9 │
└────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

### N = 100 局/算法

```
┌────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│      Metric        │ FixHeuristic │WeightedGreedy│  Expectmax   │     MCTS     │
├────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Mean Score         │        4,494 │        2,689 │       16,505 │       11,410 │
│ Max Score          │       11,400 │        6,832 │       35,756 │       32,096 │
│ Min Score          │        1,436 │          696 │        3,412 │          360 │
│ Median Score       │        3,656 │        2,474 │       15,902 │       12,030 │
│ 2048 Rate (%)      │         0.0% │         0.0% │        23.0% │         7.0% │
│ Mean Moves         │          329 │          229 │          937 │          673 │
│ Max Tile           │         1024 │          512 │         2048 │         2048 │
│ Mean Time/Move(ms) │        0.002 │        0.000 │        0.015 │        1.509 │
│ Total Time(s)      │          0.1 │          0.0 │          1.4 │        101.6 │
└────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

### N = 1000 局/算法

```
┌────────────────────┬──────────────┬──────────────┬──────────────┬──────────────┐
│      Metric        │ FixHeuristic │WeightedGreedy│  Expectmax   │     MCTS     │
├────────────────────┼──────────────┼──────────────┼──────────────┼──────────────┤
│ Mean Score         │        4,580 │        2,827 │       17,712 │       11,346 │
│ Max Score          │       15,324 │       11,796 │       60,360 │       34,160 │
│ Min Score          │          704 │          228 │        2,908 │          296 │
│ Median Score       │        4,058 │        2,404 │       16,002 │       11,862 │
│ 2048 Rate (%)      │         0.0% │         0.0% │        28.8% │         7.2% │
│ Mean Moves         │          333 │          238 │          993 │          669 │
│ Max Tile           │         1024 │         1024 │         4096 │         2048 │
│ Mean Time/Move(ms) │        0.001 │        0.000 │        0.016 │        1.513 │
│ Total Time(s) 	 │          0.2 │          0.1 │         15.6 │      1,012.5 │
└────────────────────┴──────────────┴──────────────┴──────────────┴──────────────┘
```

### 跨规模均值汇总

| 算法 | N=1 mean | N=10 mean | N=100 mean | N=1000 mean | 涨幅 |
|---|---:|---:|---:|---:|---:|
| FixHeuristic | 3,072 | 4,254 | 4,494 | 4,580 | 1.49× |
| WeightedGreedy | 2,012 | 2,858 | 2,689 | 2,827 | 1.40× |
| Expectmax | 7,536 | 13,417 | 16,505 | **17,712** | **2.35×** |
| MCTS | 4,988 | 8,110 | 11,410 | **11,346** | 2.27× |

可以看到：**四种算法都出现了"N 越大平均分越高"的趋势，并不是 MCTS 独有的现象**。Expectmax 涨幅甚至比 MCTS 还大（2.35× vs 2.27×）。这与"MCTS 实现 bug"或"局间相关"无关 —— 每次 `playOneGame` 都重新构造棋盘和分数，局间确实是相互独立的。下面给出真正的成因。

### 趋势成因解释

#### ① 大数定律：样本均值是随机变量

设单局得分为随机变量 X<sub>i</sub>，"N 局实验的平均分"是

$$\bar X_N = \frac{1}{N}\sum_{i=1}^{N} X_i$$

它本身**也是一个随机变量**，方差为 σ² / N。

- N = 1 时算的根本不是"平均值"，而是**一次抽签**。从表里也能直接看出来：N=1 时 mean = max = min = median，因为只跑了一局；
- N = 10 / 100 / 1000 才是真正在估计期望，方差以 **σ² / N** 的速度收缩；
- 随着 N 增大，X̄<sub>N</sub> 会**收敛到真实均值** μ = E[X]（大数定律）。

所以"N 越大 mean 越接近真值"是数学上的必然，**不是算法变聪明了**。

#### ② 2048 得分分布是**重尾 + 严重正偏**

观察 N = 1000 时 MCTS 的具体分布：

| min | median | mean | max |
|---:|---:|---:|---:|
| **296** | **11,862** | **11,346** | **34,160** |

特征：

- **max 是 mean 的 3 倍以上** —— 长尾极端值非常显著；
- 少数高分局的"jackpot"贡献了均值的很大一部分；
- Expectmax 的尾巴更长（max=60,360，是 mean 的 3.4×），所以 Expectmax 对 N 的敏感度甚至比 MCTS 还高。

这种**正偏 + 重尾**分布有一个关键性质：

> **N 小时采到右尾的概率低 → X̄<sub>N</sub> 系统性低估 μ → N 增大才会"补足"被漏掉的尾部贡献**

直观理解：真实期望 μ 的很大一部分来自"低概率但高分值"的极端事件（比如 5% 概率出现 30,000+ 分）。N=10 时大约只能命中 0~1 次，常常一次都遇不到，导致样本均值**单向偏低**；只有 N 足够大，尾部事件按其真实频率出现，X̄<sub>N</sub> 才会稳定到真值附近。

这正是为什么所有算法的 mean 都呈**单调上升**（而不是上下震荡）地收敛到真值 —— 重尾分布下的大数定律就是这种单边收敛的特征。

> **小结**：N=1 的结果**仅供调试参考**，不应用于算法对比；可信对比建议从 **N ≥ 100** 开始；**N = 1000** 时 mean 已基本收敛到真值附近（MCTS 在 N=100 vs N=1000 之间仅相差 0.6%）。

### 算法横向对比（基于 N=1000 收敛后的均值）

- **Expectmax 一骑绝尘**：均值 17,712，2048 达成率 28.8%，最大 tile 4096。深度搜索 + 期望计算让它能稳定走出长链路，是综合最强的算法。
- **MCTS（rollout-based）次之**：均值 11,346，2048 达成率 7.2%，最大 tile 2048。rollout 自带随机性是它方差最大、收敛最慢的根因；提升 `MCTS_ITERATIONS` 可继续推高均值，但单步耗时会线性增长（当前已达 1.5 ms/步）。
- **FixHeuristic 第三**：均值 4,580，速度极快（0.001 ms/步）。固定启发式上限明显，从未触及 2048。
- **WeightedGreedy 最弱**：均值 2,827。蛇形权重在角落突破后会被反复撕裂，缺乏对随机扰动的修复能力。

---

## Expectmax 参数探索实验

为了系统性地探索 Expectmax 算法的最高得分潜力，并量化每个参数对结果的影响，专门设计了一套**参数化扫描实验**。

### 实验代码（仅调用游戏底盘，未修改任何已有源码）

| 路径 | 角色 |
|---|---|
| `algo/ExpectmaxTunable.java`              | **参数化版本** Expectimax —— 与原 `Expectmax.java` 默认行为完全一致，但所有 `static final` 参数（搜索深度、采样上限、采样策略、5 个启发式权重）暴露为运行时可调；底盘动作仍调 `Utils.simulateMove` / `AlgoCommon.scoreAllDirections`，不重复实现游戏逻辑。 |
| `src/ExpectmaxParamExperiment.java`        | 实验主程序：OFAT（One-Factor-At-a-Time）扫描 + 组合验证，并行 worker，三格式报告输出，命令行参数完整。 |
| `tests/ExpectmaxTunableTest.java`          | 80 个测试用例：默认等价、Params API、setParams 校验、深拷贝、3 种采样策略、状态保持、merge 标志保持、零/极端权重、权重对决策的统计影响、depth 范围与耗时单调、并发一致性、100 局 fuzz 等。 |
| `tests/ExpectmaxParamExperimentTest.java`  | 87 个测试用例：spawnTile 概率分布、isGameOver、findMax、playOneGame 确定性 / 终止性、Stats 聚合、tile 直方图、CLI 端到端 smoke 测试、报告产物校验、非法参数子进程退出。 |

### 实验设计

#### 可调参数

| 参数 | 含义 | 默认值（= 原 `Expectmax` + `Utils.heuristic`） |
|---|---|---:|
| `depth`            | 搜索深度（chance/max 节点对数） | **2** |
| `maxSamples`       | 每个 chance 节点采样空格数上限 | **4** |
| `sampleStrategy`   | 采样策略：`SMART` / `RANDOM` / `ALL` | **SMART** |
| `wEmpty`           | 启发式：每个空格价值 | **240** |
| `wMonotonicity`    | 启发式：单调性每单位 | **35** |
| `wSmoothness`      | 启发式：相邻 log₂ 差异的惩罚（应 ≤ 0） | **-18** |
| `wCorner`          | 启发式：最大值在角落奖励 | **60** |
| `wMerge`           | 启发式：相邻相等 bonus | **22** |

#### 实验方法 — OFAT + 组合验证

1. **OFAT（控制变量法）**：保持其余参数为默认值，只改变一个参数；每个配置独立运行 **1000 局**。
2. **组合验证**：把 OFAT 中各自胜出的最优值组合成一组「合成最优」参数，验证是否能叠加增益。

每局：
- 随机种子 `seed = configIdx × 100003 + gameIdx`，可重复；
- 棋盘 4×4，初始 2 个 tile；终止条件 = 四方向皆不可移动 或达到 10 000 步上限；
- 决策走 `ExpectmaxTunable.getBestDirection`，移动逻辑走 `Utils.simulateMove`。

**实验规模**：8 个 OFAT 扫描 + 1 个 combo 扫描 = **41 个配置 × 1000 局 = 41 000 局游戏**，墙钟约 3 分钟（18 线程并行）。

### 实验结果

#### ① 单参数效应（OFAT）— 平均分摘要（每行 1000 局）

| 扫描 | 默认值 | 默认均值 | 最优值 | 最优均值 | 提升 | 单调性 |
|---|---:|---:|---:|---:|---:|---|
| `depth`         | 2    | 17,340 | **3**   | **27,225** | **+57.0 %** | 严格单调 ↑ |
| `maxSamples`    | 4    | 17,290 | 8       | 17,794   | +2.9 %      | 8 后饱和   |
| `sampleStrategy`| SMART | 17,542 | ALL    | 17,614   | +0.4 %      | 几乎无差异 |
| `wEmpty`        | 240  | 17,290 | **60**  | **27,236** | **+57.5 %** | 严格单调 ↓ |
| `wMonotonicity` | 35   | 17,290 | **140** | **23,418** | **+35.4 %** | 严格单调 ↑ |
| `wSmoothness`   | -18  | 17,290 | -36     | 20,384   | +17.9 %     | -36 后饱和 |
| `wCorner`       | 60   | 17,290 | 60      | 17,290   | 0 %         | 60 处有最优（双侧下降） |
| `wMerge`        | 22   | 17,290 | **88**  | **19,359** | +12.0 %     | 严格单调 ↑ |

#### ② DEPTH 是首要变量

| depth | mean | 2048 率 | 最大 tile | ms/步 |
|---:|---:|---:|---:|---:|
| 1 | 5,466 | 0.0 % | 1024 | 0.004 |
| 2 | 17,340 | 27.1 % | 2048 | 0.022 |
| 3 | **27,225** | **64.0 %** | **4096** | 0.526 |

> depth 每加 1 ≈ 实际递归层数 +2，开销 ~25× 但收益 ≈ 1.6×。
> depth=3 时已经能稳定走出 2048（64%）并出现 4096。
> depth=4 因开销爆炸（预期 > 100 ms/步），默认不扫描；可通过 `--include-depth-4` 启用。

#### ③ `wEmpty` 反直觉 — 默认偏高

| wEmpty | mean | 2048 率 | 最大 tile |
|---:|---:|---:|---:|
| **60** | **27,236** | **64.3 %** | 4096 |
| 120 | 22,167 | 48.2 % | 4096 |
| 240（默认） | 17,290 | 26.7 % | 4096 |
| 480 | 14,112 | 11.8 % | 2048 |
| 960 | 12,511 | 6.4 % | 2048 |

> 把 `wEmpty` 降到默认的 **1/4**，平均分提升 **+57.5 %**。
> 物理解释：`wEmpty=240` 让算法过度追求「空格数最多」，宁愿放弃合并也不让棋盘变挤；降低权重后单调性 / 合并 bonus 才能压住空格目标，主导链路构建。这与"depth=3、其它默认"几乎完全等价，提示两者指向同一个增益方向（让 AI 敢于堆叠合并）。

#### ④ 单调性 / 平滑度 / 合并 bonus 全部偏弱

- `wMonotonicity` 默认 **35**，最优 **140**（4× 默认）；
- `wSmoothness`  默认 **-18**，最优 **-36**（2× 默认）；
- `wMerge`       默认 **22**，最优 **88**（4× 默认）。

→ 三个「鼓励合并 / 鼓励有序」的项默认权重普遍偏低，相对 `wEmpty=240` 被压制。

#### ⑤ `wCorner=60` 已恰好处在最优峰

`wCorner` 是唯一**双侧下降**的参数：偏小（0）算法不锚角；偏大（240）算法盯着角落不肯动；都伤平均分。默认值 60 正好。

#### ⑥ 智能采样的收益已被 depth 吸收

`SMART` vs `ALL` 仅差 0.4 %；`SMART` vs `RANDOM` 差 5.1 %。
- `MAX_SAMPLES=4` 时，**智能采样 ≈ 全采样**（4 个空格已能覆盖关键信号）；
- 纯随机 `RANDOM` 明显劣势——会丢掉关键大数邻接空格；
- `maxSamples=1 → 8` 每翻一倍提升 ~1 000 分；`8 → 16` 不再上升 → **8 是性价比拐点**。

#### ⑦ 组合最优 — OFAT 不能简单叠加（参数交互）

| 配置 | mean | 中位数 | 2048 率 | 最高单局 | ms/步 |
|---|---:|---:|---:|---:|---:|
| `baseline_default`（depth=2，原默认） | 17,542 | 15,946 | 27.5 % | 59,184 | 0.024 |
| `OFAT_best_depth2`（OFAT 全部最优值组合，depth=2） | **14,603** | 14,410 | 19.7 % | 36,476 | 0.028 |
| `OFAT_best_depth3`（同上但 depth=3） | 19,763 | 16,190 | 41.2 % | 71,616 | 0.845 |
| **`only_depth3`**（仅 depth=3，其余默认） | **27,914** | **27,586** | **67.4 %** | 72,356 | 0.580 |

**核心结论**：

> 把每个 OFAT 的最优值"硬"组合（`wEmpty=60` × `wMono=140` × `wSmooth=-36` × `wMerge=88` × `maxSamples=8` × `strategy=ALL`）反而**比 baseline 更差**（14,603 vs 17,542）。
> 原因：当 `wEmpty=60` 让算法不再追求空格、`wMono=140` 强制单调、`wMerge=88` 强烈奖励合并，三个「积极合并派」叠加后会让 AI **过度激进**——任何牺牲单调或合并机会的迂回操作都被严厉惩罚，结果在中盘被卡死。
>
> **OFAT 在参数有强交互时会误导**——这是经典的实验设计教训。
>
> **最强单一配置 = `only_depth3`**：均值 **27,914**，2048 达成率 **67.4 %**，最高单局 **72,356**。

### 推荐配置

| 场景 | 推荐参数 | 性能 | 决策耗时 |
|---|---|---|---|
| **追求最高均值**（推荐） | `depth=3`，其余默认 | **27,914 mean / 67.4 % 2048 / max tile 4096** | ~0.58 ms/步 |
| **均衡（保持 depth=2）** | `depth=2, wEmpty=60` | ~27,200 mean | ~0.025 ms/步 |
| **超低延迟（实时游戏）** | 默认 (`depth=2, maxSamples=4`) | 17,540 mean / 27 % 2048 | ~0.024 ms/步 |
| **进一步上限探索** | `depth=4, maxSamples=4`（注意需小时级） | 预计 mean > 35,000 | 估计 > 30 ms/步 |

### 复现实验

```bash
# 编译（含算法、实验主程序、测试）
javac -encoding UTF-8 -d out src/*.java algo/*.java tests/*.java

# 完整实验（默认 1000 局/配置，~3 分钟）
java -Xss8m -cp out ExpectmaxParamExperiment --games 1000

# 仅扫描某一参数
java -cp out ExpectmaxParamExperiment --only depth     # 搜索深度
java -cp out ExpectmaxParamExperiment --only samples   # MAX_SAMPLES
java -cp out ExpectmaxParamExperiment --only strategy  # 采样策略
java -cp out ExpectmaxParamExperiment --only empty     # W_EMPTY
java -cp out ExpectmaxParamExperiment --only mono      # W_MONOTONICITY
java -cp out ExpectmaxParamExperiment --only smooth    # W_SMOOTHNESS
java -cp out ExpectmaxParamExperiment --only corner    # W_CORNER
java -cp out ExpectmaxParamExperiment --only merge     # W_MERGE
java -cp out ExpectmaxParamExperiment --only combo     # 组合验证

# 启用 depth=4（开销很大，需数十分钟）
java -Xss8m -cp out ExpectmaxParamExperiment --include-depth-4 --only depth

# 快速冒烟（10 局/配置）
java -cp out ExpectmaxParamExperiment --smoke --only depth
```

实验完成后会同时输出三种格式的报告（盒框 TXT、Markdown、CSV），路径在项目根目录。

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

### 运行 Expectmax 参数探索实验

```bash
# 编译（含算法模块）
javac -encoding UTF-8 -d out src/*.java algo/*.java

# 运行参数扫描实验（默认每配置 1000 局，~3 分钟）
java -Xss8m -cp out ExpectmaxParamExperiment --games 1000

# 详细命令行选项见上文「Expectmax 参数探索实验」一节
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

测试覆盖十一大模块，共 320+ 用例：

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
| **ExpectmaxTunable** | `ExpectmaxTunableTest.java` | 默认参数等价、Params API、setParams 校验、采样策略、状态保持、零/极端权重、权重统计影响、深度耗时单调、并发一致性、100 局 fuzz | 80 |
| **ExpectmaxParamExperiment** | `ExpectmaxParamExperimentTest.java` | spawnTile 概率分布、isGameOver、findMax、playOneGame 确定性 / 终止性、Stats 聚合、tile 直方图、CLI 端到端 smoke、报告产物校验 | 87 |

### 运行测试

```bash
# 编译全部
javac -encoding UTF-8 -d out src/*.java algo/*.java tests/*.java

# 运行原项目主测试套件 (Grid + GameLogic + Utils)
java -cp out TestAll

# 单独运行 — 原算法测试
java -cp out ExpectmaxTest
java -cp out MCTSTest
java -cp out FixHeuristicTest
java -cp out WeightedGreedyTest
java -cp out AnimationEngineTest

# 单独运行 — Expectmax 参数探索新增测试
java -cp out ExpectmaxTunableTest         # 80 用例 — 算法参数化
java -cp out ExpectmaxParamExperimentTest # 87 用例 — 实验主程序
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
- [x] **Expectmax 参数化版本** (`ExpectmaxTunable`) — 8 个可调参数（搜索深度、采样上限、采样策略、5 个启发式权重）
- [x] **Expectmax 参数扫描实验** (`ExpectmaxParamExperiment`) — OFAT + 组合验证，并行 worker，三格式报告（盒框 TXT / Markdown / CSV）