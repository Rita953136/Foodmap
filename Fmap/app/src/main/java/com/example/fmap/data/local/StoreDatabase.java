package com.example.fmap.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @TypeConverters({Converters.class}): 告訴資料庫，要使用我們寫好的「翻譯官」(Converters.java)。
 * @Database(...): 告訴 Room 這是資料庫的藍圖。
 *   - entities = {StoreEntity.class}: 這個資料庫裡包含「店家資料表」。
 *   - version = 1: 資料庫的版本號，如果未來修改資料表結構，需要增加這個數字。
 */
@TypeConverters({Converters.class})
@Database(entities = {StoreEntity.class}, version = 1, exportSchema = false)
public abstract class StoreDatabase extends RoomDatabase {

    public abstract StoreDao storeDao();
    private static volatile StoreDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    /**
     * 取得資料庫實例的「官方唯一入口」。
     * 這段程式碼使用了「單例模式 (Singleton)」和「雙重檢查鎖定」，
     * 目的是要確保在任何情況下，整個 App 都只會有一個資料庫連線，
     * 這樣既省資源又安全。
     *
     * @param context App 的上下文環境。
     * @return 回傳唯一的 StoreDatabase 實例。
     */
    public static StoreDatabase getDatabase(final Context context) {
        // 第一次檢查：如果實例已經存在，就直接回傳，最快。
        if (INSTANCE == null) {
            // 如果實例不存在，就加上「同步鎖」，避免多個執行緒同時來建立資料庫。
            synchronized (StoreDatabase.class) {
                // 第二次檢查：進來後再檢查一次，可能在等待鎖的時候，別的執行緒已經建立好了。
                if (INSTANCE == null) {
                    // 真的確定沒有，才開始建立新的資料庫實例。
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    StoreDatabase.class, "store_database")
                            // 如果資料庫版本升級，但沒有提供升級規則，就清空舊資料重建。
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        // 回傳這個得來不易的唯一實例。
        return INSTANCE;
    }
}
