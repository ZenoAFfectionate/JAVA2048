# 2048 动画与UI优化 — 设计文档

日期: 2026-05-10

## 目标

在现有 Java Swing 基础上，添加动画系统和 UI 美化，提升视觉流畅度和交互体验。保持零外部依赖，兼容现有测试。

## 范围

### 包含
- 滑动动画 (tile 从原位平滑移动到目标位)
- 弹出动画 (新 tile 从小缩放弹入)
- 合并闪烁 (合并后短暂白色光晕)
- Tile 阴影和圆角
- 输入缓冲 (动画期间缓存一次方向键)
- 分数标签微交互
- 高数值 tile 渐变光泽
- 格子分隔线

### 不包含
- 迁移到 JavaFX 或其他框架
- 触摸/鼠标拖拽操作
- 回放/撤销功能

## 架构

```
AnimationEngine (新增)
├── Timer (60fps, javax.swing.Timer)
├── List<TileAnimation> — 当前动画集合
├── 查询: isAnimating(), getTransform(tile) -> (dx, dy, scale, glow)
├── 事件: onFinished -> callback
└── 输入: addAnimations(list), completeAll() — 快速完成所有动画

TileAnimation (AnimationEngine 内部类)
├── int fromRow, fromCol, toRow, toCol  (滑动)
├── int row, col (弹出位置)
├── AnimationType: SLIDE, POP, MERGE
├── long startTime, duration
├── float easeOutCubic(float t) — 缓动函数
└── float getProgress() — 0..1 插值

GameView.GameBoard (修改)
├── AnimationEngine animEngine
├── boolean inputPending / int pendingKeyCode  (输入缓冲)
├── Set<Point> mergeTargets (本次移动产生的合并位置)
├── 绘制流程: paint tiles → paint animations overlay → paint game over
└── 绘制增强: 阴影、分隔线、渐变光泽

Grid (小幅修改)
├── 新增字段用于标记合并目标 (当前回合)
└── 其他不变
```

## 动画流程

```
用户按键 → keyPressed
  ├── 正在动画中? → 缓存 keyCode 到 pendingKeyCode, return
  ├── doMoveX() → 记录移动前后位置到 AnimationEngine
  │   └── 标记 mergeTargets 集合
  ├── spawnTile() → 记录新 tile 弹出动画
  ├── 启动 AnimationEngine timer
  └── 动画完成回调:
        ├── 如 pendingKeyCode 非空 → 递归调用 keyPressed(pendingKeyCode)
        └── 清除 pendingKeyCode
```

## 缓动函数

- 滑动: `easeOutCubic(t) = 1 - (1-t)^3` (快起慢停)
- 弹出: `easeOutBack(t)` — 带弹性过冲
- 合并闪烁: `easeInOutCubic(t)` — 两端平滑

## 测试策略

### 现有测试
- 现有 49 个测试用例全部保持通过（回归验证）
- 动画引擎通过 `completeAll()` 在测试中即时完成，保证测试不受动画 Timer 影响

### 新增测试 — `tests/AnimationEngineTest.java` (约 12 用例)

**缓动函数测试 (4 用例):**
- `easeOutCubic(0) = 0, easeOutCubic(1) = 1` — 边界值
- `easeOutCubic(0.5)` — 验证曲线形状 (应 > 0.5，快起慢停)
- `easeOutBack(0) = 0, easeOutBack(1) = 1` — 边界值
- 弹性过冲验证: `easeOutBack(t)` 在某点 > 1 (过冲特征)

**AnimationEngine 核心测试 (5 用例):**
- 初始状态 `isAnimating() == false`
- `addSlideAnimation` 后 `isAnimating() == true`
- `completeAll()` 后 `isAnimating() == false`，且 tile 位置更新到位
- `addPopAnimation` / `addMergeGlow` 后动画列表非空
- `clear()` 清空所有动画

**TileAnimation 逻辑测试 (3 用例):**
- `getProgress()` 在动画到期后返回 1.0 (不超过 1)
- `getTransform()` 在 progress=1 时返回终点位置
- 多个动画同时进行时互不干扰

### 集成测试 — `tests/AnimationIntegrationTest.java` (约 5 用例)

**输入缓冲测试 (3 用例):**
- 动画期间按键 → keyCode 被缓存 (不丢失输入)
- 动画完成后自动执行缓存的按键
- 动画期间多次按键 → 仅缓存最后一次 (保持最优)

**动画与游戏逻辑一致性 (2 用例):**
- 开启动画的情况下执行 move + spawn 后，棋盘状态与无动画时一致
- `completeAll()` 后棋盘状态立即同步到最终状态

### 手动验证
- 启动游戏后操作方向键，观察动画流畅度
- 快速连按方向键，确认输入缓冲正常工作
- 观察 tile 弹出、滑动、合并闪烁是否平滑

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/AnimationEngine.java` | 新增 | 动画管理器 + 缓动 |
| `src/GameView.java` | 修改 | 集成动画、输入缓冲、UI美化 |
| `src/Grid.java` | 略改 | 移除 merge 标志的公开暴露 |
| `tests/AnimationEngineTest.java` | 新增 | 缓动函数 + TileAnimation + AnimationEngine (12 用例) |
| `tests/AnimationIntegrationTest.java` | 新增 | 输入缓冲 + 动画一致性 (5 用例) |
| 其他文件 | 不变 | |
