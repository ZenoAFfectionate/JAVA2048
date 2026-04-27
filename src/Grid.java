import java.awt.Color;
import java.awt.Font;

/**
 * 方格类 - 封装单个格子的数值、颜色、字体与递归移动逻辑
 */
public class Grid {
    public int value;
    private boolean merge;

    // 多级字体以适应不同位数
    private static final Font FONT_1 = new Font("宋体", Font.BOLD, 46);
    private static final Font FONT_2 = new Font("宋体", Font.BOLD, 40);
    private static final Font FONT_3 = new Font("宋体", Font.BOLD, 34);
    private static final Font FONT_4 = new Font("宋体", Font.BOLD, 28);

    // 颜色查找表 — index = log2(value), value=0 单独处理
    private static final Color[] BG_TABLE = {
        new Color(0xcdc1b4), // 0
        new Color(0xeee4da), // 2
        new Color(0xede0c8), // 4
        new Color(0xf2b179), // 8
        new Color(0xf59563), // 16
        new Color(0xf67c5f), // 32
        new Color(0xf65e3b), // 64
        new Color(0xedcf72), // 128
        new Color(0xedcc61), // 256
        new Color(0xedc850), // 512
        new Color(0xedc53f), // 1024
        new Color(0xedc22e), // 2048
    };
    private static final Color BG_SUPER = new Color(0x248c51);

    private static final Color FG_LIGHT = Color.BLACK;
    private static final Color FG_DARK  = Color.WHITE;
    private static final Color FG_EMPTY = BG_TABLE[0];

    public Grid() {
        this(0);
    }

    public Grid(int value) {
        this.value = value;
        this.merge = false;
    }

    /** 深拷贝：用于 AI 搜索时创建独立副本 */
    public Grid copy() {
        Grid g = new Grid(this.value);
        g.merge = this.merge;
        return g;
    }

    /** 重置为空状态（AI save/restore 用） */
    public void reset() {
        this.value = 0;
        this.merge = false;
    }

    public boolean isEmpty() {
        return value == 0;
    }

    // ---- 外观 ----

    public Color getForeground() {
        if (value == 0) return FG_EMPTY;
        if (value <= 4)  return FG_LIGHT;
        return FG_DARK;
    }

    public Color getBackground() {
        if (value == 0) return BG_TABLE[0];
        int idx = 31 - Integer.numberOfLeadingZeros(value);
        return idx < BG_TABLE.length ? BG_TABLE[idx] : BG_SUPER;
    }

    public Font getCheckFont() {
        if (value < 10)        return FONT_1;
        else if (value < 100)  return FONT_2;
        else if (value < 1000) return FONT_3;
        return FONT_4;
    }

    // ---- getter / setter ----

    public int  getValue()          { return value; }
    public void setValue(int value) { this.value = value; }
    public void setMerge(boolean f) { this.merge = f; }
    public boolean isMerged()       { return merge; }

    // ================================================================
    //  递归移动
    // ================================================================

    public int moveUp(Grid[][] grids, int i, int j) {
        if (i == 0) return 0;
        return moveTo(grids, i, j, i - 1, j);
    }

    public int moveDown(Grid[][] grids, int i, int j) {
        if (i == 3) return 0;
        return moveTo(grids, i, j, i + 1, j);
    }

    public int moveLeft(Grid[][] grids, int i, int j) {
        if (j == 0) return 0;
        return moveTo(grids, i, j, i, j - 1);
    }

    public int moveRight(Grid[][] grids, int i, int j) {
        if (j == 3) return 0;
        return moveTo(grids, i, j, i, j + 1);
    }

    /**
     * 统一移动逻辑 — 递归向目标方向滑动直至边缘或合并
     */
    private int moveTo(Grid[][] grids, int i, int j, int ni, int nj) {
        if (ni < 0 || ni >= 4 || nj < 0 || nj >= 4) return 0;
        Grid target = grids[ni][nj];
        if (target.isEmpty()) {
            target.value = this.value;
            this.value = 0;
            // 递归推进到下一格，方向向量 (ni-i, nj-j)
            return target.moveTo(grids, ni, nj,
                    ni + (ni - i), nj + (nj - j));
        } else if (target.value == this.value && !target.merge) {
            target.merge = true;
            target.value *= 2;
            this.value = 0;
            return target.value;
        }
        return 0;
    }
}
