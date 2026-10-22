package org.codirex.webide.workspace;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.io.File;

/** A project: a folder inside the workspace root. The folder name is the project name. */
@Entity(tableName = "projects", indices = {@Index(value = "path", unique = true)})
public class Project {

    @PrimaryKey(autoGenerate = true)
    public long id;

    @NonNull
    public String name = "";

    /** Absolute path of the project folder. */
    @NonNull
    public String path = "";

    public long createdAt;

    public long lastOpenedAt;

    /** Id of the template the project was created from; reserved for the template phase. */
    public String templateId;

    public File dir() {
        return new File(path);
    }
}
