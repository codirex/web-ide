package org.codirex.webide;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.appcompat.app.AlertDialog;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.function.Consumer;
import java.util.function.Function;

/** Small dialogs shared by the screens. */
final class Dialogs {

    private Dialogs() {
    }

    /**
     * Asks for a line of text. The dialog stays open and shows the message returned by
     * {@code validator} until it returns {@code null}.
     */
    static void promptText(Context context, int titleRes, String initial, int positiveRes,
                           Function<String, String> validator, Consumer<String> onOk) {
        View view = LayoutInflater.from(context).inflate(R.layout.dialog_text_input, null);
        final TextInputLayout layout = view.findViewById(R.id.inputLayout);
        final TextInputEditText input = view.findViewById(R.id.input);
        String start = initial == null ? "" : initial;
        input.setText(start);
        input.setSelection(start.length());

        final AlertDialog dialog = new MaterialAlertDialogBuilder(context)
                .setTitle(titleRes)
                .setView(view)
                .setPositiveButton(positiveRes, null)
                .setNegativeButton(android.R.string.cancel, null)
                .create();
        dialog.setOnShowListener(d -> {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String text = String.valueOf(input.getText());
                String error = validator.apply(text);
                if (error != null) {
                    layout.setError(error);
                } else {
                    dialog.dismiss();
                    onOk.accept(text);
                }
            });
            input.requestFocus();
        });
        if (dialog.getWindow() != null) {
            dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE);
        }
        dialog.show();
    }

    static void confirm(Context context, String title, String message, int positiveRes, Runnable onOk) {
        new MaterialAlertDialogBuilder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(positiveRes, (d, which) -> onOk.run())
                .setNegativeButton(android.R.string.cancel, null)
                .show();
    }
}
