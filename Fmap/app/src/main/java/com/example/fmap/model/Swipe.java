package com.example.fmap.model;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper; // 引入 RecyclerView 的滑動觸控輔助工具

/**
 * 卡片滑動動作的資料模型。
 * 專門用來把 ItemTouchHelper 的「方向」轉換成好理解的「動作」。
 */
public final class Swipe {

    /**
     * private 建構子，代表這是一個「工具類」，不應該被 new 出來。
     * 所有的功能都應該透過靜態方法 (static) 來呼叫。
     */
    private Swipe() {}

    /**
     * 定義滑動的動作種類 (列舉 Enum)。
     * 這樣 ViewModel 就可以清楚地判斷使用者是執行了哪個動作。
     */
    public enum Action {
        LIKE,   // 代表「喜歡」，通常對應到右滑
        NOPE    // 代表「不喜歡」，通常對應到左滑
    }

    /**
     * 工具方法：把 ItemTouchHelper 回傳的「方向數字」轉換成我們定義的 Action。
     * @param itemTouchHelperDir ItemTouchHelper.Callback 的 onSwiped 方法傳過來的方向值。
     * @return 回傳對應的 Action (LIKE 或 NOPE)。
     */
    @NonNull
    public static Action fromDirection(int itemTouchHelperDir) {
        // 使用位元運算 (&) 來檢查方向。
        // 這種寫法比 `==` 更安全，因為方向值可能是複合的。

        // 如果方向包含了「往右」
        if ((itemTouchHelperDir & ItemTouchHelper.RIGHT) == ItemTouchHelper.RIGHT) {
            return Action.LIKE; // 就回傳 LIKE
        }
        // 如果方向包含了「往左」
        else if ((itemTouchHelperDir & ItemTouchHelper.LEFT) == ItemTouchHelper.LEFT) {
            return Action.NOPE; // 就回傳 NOPE
        }

        // 其他所有方向 (例如上、下)，都預設當作不喜歡。
        return Action.NOPE;
    }
}
