package com.villegol.appgastos.source.wallet

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.villegol.appgastos.GastosApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ExpenseActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DELETE) return
        val expenseId = intent.getStringExtra(EXTRA_EXPENSE_ID) ?: return
        val application = context.applicationContext as GastosApplication
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                application.container.expenseRepository.delete(expenseId)
                NotificationManagerCompat.from(application).cancel(expenseId.hashCode())
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DELETE = "com.villegol.appgastos.DELETE_EXPENSE"
        const val EXTRA_EXPENSE_ID = "expense_id"
    }
}
