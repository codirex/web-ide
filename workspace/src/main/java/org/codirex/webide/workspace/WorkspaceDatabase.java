package org.codirex.webide.workspace;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

@Database(entities = {Project.class}, version = 1, exportSchema = false)
public abstract class WorkspaceDatabase extends RoomDatabase {

    private static volatile WorkspaceDatabase instance;

    public abstract ProjectDao projectDao();

    static WorkspaceDatabase get(Context context) {
        if (instance == null) {
            synchronized (WorkspaceDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(
                            context.getApplicationContext(), WorkspaceDatabase.class, "workspace.db").build();
                }
            }
        }
        return instance;
    }
}
