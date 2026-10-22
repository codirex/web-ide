package org.codirex.webide.workspace;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.Query;
import androidx.room.Update;

import java.util.List;

@Dao
public interface ProjectDao {

    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC, name COLLATE NOCASE ASC")
    LiveData<List<Project>> observeAll();

    @Query("SELECT * FROM projects")
    List<Project> getAll();

    @Query("SELECT * FROM projects WHERE id = :id")
    Project findById(long id);

    @Query("SELECT * FROM projects WHERE path = :path LIMIT 1")
    Project findByPath(String path);

    @Query("UPDATE projects SET lastOpenedAt = :time WHERE id = :id")
    void touch(long id, long time);

    @Insert
    long insert(Project project);

    @Update
    void update(Project project);

    @Delete
    void delete(Project project);
}
