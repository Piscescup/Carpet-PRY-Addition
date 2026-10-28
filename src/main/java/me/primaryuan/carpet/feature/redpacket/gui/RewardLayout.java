package me.primaryuan.carpet.feature.redpacket.gui;

/**
 * Centers reward icons in the four rows between the header and footer.
 */
final class RewardLayout {
    private static final int COLUMNS = 9;
    private static final int ROWS = 4;
    static final int PAGE_SIZE = COLUMNS * ROWS;

    private RewardLayout() {
    }

    static int[] slots(int count) {
        if (count < 0 || count > PAGE_SIZE) throw new IllegalArgumentException("Invalid reward count");
        int[] slots = new int[count];
        if (count == 0) return slots;
        int rows = (count + COLUMNS - 1) / COLUMNS;
        int firstRow = 1 + (ROWS - rows) / 2;
        int index = 0;
        for (int row = 0; row < rows; row++) {
            int items = count / rows + (row < count % rows ? 1 : 0);
            int firstColumn = (COLUMNS - items) / 2;
            for (int item = 0; item < items; item++) {
                // Even-sized rows leave the middle slot free to stay symmetric.
                int column = firstColumn + item + (items % 2 == 0 && item >= items / 2 ? 1 : 0);
                slots[index++] = (firstRow + row) * COLUMNS + column;
            }
        }
        return slots;
    }
}
