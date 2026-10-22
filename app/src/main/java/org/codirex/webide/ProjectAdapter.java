package org.codirex.webide;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.codirex.webide.databinding.ItemProjectBinding;
import org.codirex.webide.workspace.Project;

/** List of projects. */
final class ProjectAdapter extends ListAdapter<Project, ProjectAdapter.Holder> {

    interface Listener {
        void onOpen(Project project);

        void onMenu(Project project, View anchor);
    }

    private static final DiffUtil.ItemCallback<Project> DIFF = new DiffUtil.ItemCallback<Project>() {
        @Override
        public boolean areItemsTheSame(@NonNull Project a, @NonNull Project b) {
            return a.id == b.id;
        }

        @Override
        public boolean areContentsTheSame(@NonNull Project a, @NonNull Project b) {
            return a.name.equals(b.name) && a.path.equals(b.path) && a.lastOpenedAt == b.lastOpenedAt;
        }
    };

    private final Listener listener;

    ProjectAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemProjectBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        Project project = getItem(position);
        holder.binding.name.setText(project.name);
        holder.binding.path.setText("~/projects/" + project.name);
        holder.binding.getRoot().setOnClickListener(v -> listener.onOpen(project));
        holder.binding.more.setOnClickListener(v -> listener.onMenu(project, v));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemProjectBinding binding;

        Holder(ItemProjectBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
