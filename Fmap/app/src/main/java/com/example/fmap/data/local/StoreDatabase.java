package com.example.fmap.data.local;

import android.content.Context;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@TypeConverters({Converters.class})
@Database(entities = {StoreEntity.class}, version = 1, exportSchema = false)
public abstract class StoreDatabase extends RoomDatabase {

    public abstract StoreDao storeDao();

    private static volatile StoreDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor =
            Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static StoreDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (StoreDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    StoreDatabase.class, "store_database")
                            .fallbackToDestructiveMigration()
                            .build();
                }
            }
        }
        return INSTANCE;
    }
}
