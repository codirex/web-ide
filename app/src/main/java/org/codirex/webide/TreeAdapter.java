package org.codirex.webide;

import android.graphics.Typeface;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import org.codirex.webide.databinding.ItemTreeRowBinding;
import org.codirex.webide.workspace.FileTree;

/** The rows of the file tree: indentation by depth, a chevron for folders, an icon and the name. */
final class TreeAdapter extends ListAdapter<FileTree.Row, TreeAdapter.Holder> {

    interface Listener {
        void onClick(FileTree.Row row);

        void onLongClick(FileTree.Row row, View anchor);
    }

    private static final DiffUtil.ItemCallback<FileTree.Row> DIFF = new DiffUtil.ItemCallback<FileTree.Row>() {
        @Override
        public boolean areItemsTheSame(@NonNull FileTree.Row a, @NonNull FileTree.Row b) {
            return a.file.getPath().equals(b.file.getPath());
        }

        @Override
        public boolean areContentsTheSame(@NonNull FileTree.Row a, @NonNull FileTree.Row b) {
            return a.depth == b.depth && a.expanded == b.expanded && a.directory == b.directory;
        }
    };

    private final Listener listener;
    private String selectedPath;

    TreeAdapter(Listener listener) {
        super(DIFF);
        this.listener = listener;
    }

    /** Marks a file (shown in bold). Pass null for none. */
    void setSelectedPath(String path) {
        selectedPath = path;
        notifyItemRangeChanged(0, getItemCount());
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(ItemTreeRowBinding.inflate(LayoutInflater.from(parent.getContext()), parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder holder, int position) {
        FileTree.Row row = getItem(position);
        ItemTreeRowBinding b = holder.binding;

        int indent = Math.round(b.getRoot().getResources().getDimension(R.dimen.tree_indent));
        int base = Math.round(4 * b.getRoot().getResources().getDisplayMetrics().density);
        b.getRoot().setPaddingRelative(base + row.depth * indent, b.getRoot().getPaddingTop(),
                b.getRoot().getPaddingEnd(), b.getRoot().getPaddingBottom());

        if (row.directory) {
            b.chevron.setVisibility(View.VISIBLE);
            b.chevron.setImageResource(row.expanded ? R.drawable.ic_expand_more : R.drawable.ic_chevron_right);
            b.icon.setImageResource(R.drawable.ic_folder);
        } else {
            b.chevron.setVisibility(View.INVISIBLE);
            b.icon.setImageResource(R.drawable.ic_file);
        }
        b.name.setText(row.name());
        boolean selected = row.file.getPath().equals(selectedPath);
        b.name.setTypeface(null, selected ? Typeface.BOLD : Typeface.NORMAL);

        b.getRoot().setOnClickListener(v -> listener.onClick(row));
        b.getRoot().setOnLongClickListener(v -> {
            listener.onLongClick(row, v);
            return true;
        });
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final ItemTreeRowBinding binding;

        Holder(ItemTreeRowBinding binding) {
            super(binding.getRoot());
            this.binding = binding;
        }
    }
}
