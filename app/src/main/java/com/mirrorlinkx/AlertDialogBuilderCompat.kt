package com.mirrorlinkx

import android.app.AlertDialog
import android.content.Context
import android.view.View

object AlertDialogBuilderCompat {
    fun show(context: Context, title: String, view: View, onPositive: () -> Unit) {
        AlertDialog.Builder(context).setTitle(title).setView(view)
            .setPositiveButton("Connect") { _, _ -> onPositive() }
            .setNegativeButton("Cancel", null).show()
    }
}
