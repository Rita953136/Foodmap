package com.example.fmap.model;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.ItemTouchHelper;

/**
 * 卡片滑動動作模型。
 * - 右滑：LIKE
 * - 左滑：NOPE
 *
 * 可搭配你的 SwipeCallback / ItemTouchHelper 使用：
 *   Swipe.Action action = Swipe.fromDirection(direction);
 *   viewModel.handleSwipeAction(action, place);
 */
public final class Swipe {

    private Swipe() {}

    /** 對外使用的動作種類（HomeViewModel 依此判斷收藏/丟垃圾桶） */
    public enum Action {
        LIKE,   // 右滑
        NOPE    // 左滑
    }

    /**
     * 依據 ItemTouchHelper 的方向回傳對應動作：
     * - RIGHT -> LIKE
     * - LEFT  -> NOPE
     * 其他方向預設回傳 NOPE（也可以改成丟 IllegalArgumentException）
     */
    @NonNull
    public static Action fromDirection(int itemTouchHelperDir) {
        if ((itemTouchHelperDir & ItemTouchHelper.RIGHT) == ItemTouchHelper.RIGHT) {
            return Action.LIKE;
        } else if ((itemTouchHelperDir & ItemTouchHelper.LEFT) == ItemTouchHelper.LEFT) {
            return Action.NOPE;
        }
        // 其它方向（UP/DOWN）視需求自行擴充；這裡先當作 NOPE。
        return Action.NOPE;
    }
}
