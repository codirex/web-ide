package org.codirex.webide.workspace;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.lifecycle.LiveData;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Projects of the workspace. Every project is a folder directly inside the workspace root, and the
 * database remembers when it was created and last opened. All work happens on a background thread;
 * results are delivered on the main thread.
 */
public final class ProjectRepository {

    /** Result of an asynchronous operation. */
    public interface Callback<T> {
        void onSuccess(T value);

        void onError(String message);
    }

    private static ProjectRepository instance;

    private final ProjectDao dao;
    private final File root;
    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    private ProjectRepository(Context context, File root) {
        this.dao = WorkspaceDatabase.get(context).projectDao();
        this.root = root;
    }

    public static synchronized ProjectRepository get(Context context, File root) {
        if (instance == null) {
            instance = new ProjectRepository(context, root);
        }
        return instance;
    }

    public File getRoot() {
        return root;
    }

    public LiveData<List<Project>> observeAll() {
        return dao.observeAll();
    }

    /**
     * Makes the database match the folders on disk: folders that are not known yet (for example
     * created with git clone in the terminal) become projects, and projects whose folder is gone
     * are forgotten.
     */
    public void sync() {
        io.execute(() -> {
            try {
                if (!root.isDirectory() && !root.mkdirs()) {
                    return;
                }
                Set<String> onDisk = new HashSet<>();
                File[] folders = root.listFiles(f -> f.isDirectory() && !f.getName().startsWith("."));
                long now = System.currentTimeMillis();
                if (folders != null) {
                    for (File folder : folders) {
                        String path = folder.getAbsolutePath();
                        onDisk.add(path);
                        if (dao.findByPath(path) == null) {
                            dao.insert(newProject(folder.getName(), path, now));
                        }
                    }
                }
                for (Project project : dao.getAll()) {
                    if (!onDisk.contains(project.path)) {
                        dao.delete(project);
                    }
                }
            } catch (RuntimeException ignored) {
                // The list is refreshed again the next time the screen opens.
            }
        });
    }

    public void findById(long id, Callback<Project> callback) {
        run(callback, () -> {
            Project project = dao.findById(id);
            if (project == null) {
                throw new IOException("Project not found");
            }
            return project;
        });
    }

    public void create(String name, Callback<Project> callback) {
        run(callback, () -> {
            checkName(name);
            File folder = new File(root, name);
            if (Files.exists(folder.toPath())) {
                throw new IOException("A project named \"" + name + "\" already exists");
            }
            if (!folder.mkdirs()) {
                throw new IOException("Cannot create the project folder");
            }
            return store(folder, name);
        });
    }

    /** Creates a project from a zip file. The name is made unique if it is taken. */
    public void importZip(String name, InputStream zip, Callback<Project> callback) {
        run(callback, () -> {
            checkName(name);
            File folder = FileOps.uniqueChild(root, name);
            if (!folder.mkdirs()) {
                throw new IOException("Cannot create the project folder");
            }
            try {
                ZipTransfer.unzip(zip, folder, ZipTransfer.DEFAULT_MAX_BYTES);
            } catch (IOException | RuntimeException e) {
                FileOps.delete(folder);
                throw e;
            }
            return store(folder, folder.getName());
        });
    }

    /** Writes the project as a zip, leaving out node_modules. */
    public void exportZip(Project project, OutputStream out, Callback<Integer> callback) {
        run(callback, () -> {
            File folder = ownFolder(project);
            return ZipTransfer.zipDirectory(folder, out, true, project.name);
        });
    }

    public void rename(Project project, String newName, Callback<Project> callback) {
        run(callback, () -> {
            checkName(newName);
            File folder = ownFolder(project);
            File renamed = FileOps.rename(folder, newName);
            project.name = newName;
            project.path = renamed.getAbsolutePath();
            dao.update(project);
            return project;
        });
    }

    /** Deletes the project folder and forgets the project. */
    public void delete(Project project, Callback<Void> callback) {
        run(callback, () -> {
            File folder = ownFolder(project);
            FileOps.delete(folder);
            dao.delete(project);
            return null;
        });
    }

    public void touch(long id) {
        io.execute(() -> {
            try {
                dao.touch(id, System.currentTimeMillis());
            } catch (RuntimeException ignored) {
                // Only affects the sort order.
            }
        });
    }

    // --- helpers ---------------------------------------------------------------------------

    private interface Work<T> {
        T run() throws IOException;
    }

    private <T> void run(Callback<T> callback, Work<T> work) {
        io.execute(() -> {
            try {
                T result = work.run();
                main.post(() -> callback.onSuccess(result));
            } catch (IOException | RuntimeException e) {
                String message = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                main.post(() -> callback.onError(message));
            }
        });
    }

    private void checkName(String name) throws IOException {
        String problem = FileOps.validateName(name);
        if (problem != null) {
            throw new IOException(problem);
        }
    }

    /** The project folder, after checking that it really is inside the workspace root. */
    private File ownFolder(Project project) throws IOException {
        File folder = project.dir();
        if (!FileOps.isInside(root, folder) || folder.equals(root)) {
            throw new IOException("This folder is outside the workspace");
        }
        return folder;
    }

    private Project store(File folder, String name) {
        Project project = newProject(name, folder.getAbsolutePath(), System.currentTimeMillis());
        project.id = dao.insert(project);
        return project;
    }

    private static Project newProject(String name, String path, long now) {
        Project project = new Project();
        project.name = name;
        project.path = path;
        project.createdAt = now;
        project.lastOpenedAt = now;
        return project;
    }
}
