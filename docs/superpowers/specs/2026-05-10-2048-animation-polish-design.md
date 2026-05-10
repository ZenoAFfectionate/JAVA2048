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

- 现有 49 个测试用例全部保持通过
- 动画引擎可通过 `completeAll()` 在测试中即时完成，不增加测试执行时间
- 手动验证: 启动游戏后操作方向键，观察动画是否流畅

## 文件清单

| 文件 | 操作 | 说明 |
|------|------|------|
| `src/AnimationEngine.java` | 新增 | 动画管理器 + 缓动 |
| `src/GameView.java` | 修改 | 集成动画、输入缓冲、UI美化 |
| `src/Grid.java` | 略改 | 移除 merge 标志的公开暴露 |
| 其他文件 | 不变 | |
