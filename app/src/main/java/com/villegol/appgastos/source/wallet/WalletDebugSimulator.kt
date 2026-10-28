package com.villegol.appgastos.source.wallet

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.villegol.appgastos.GastosApplication
import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.NotificationSnapshot
import com.villegol.appgastos.source.location.LocationCaptureWorker
import java.time.Instant

object WalletDebugSimulator {
    suspend fun simulate(context: Context): Expense? {
        val application = context.applicationContext as GastosApplication
        val snapshot = NotificationSnapshot(
            packageName = "com.google.android.apps.walletnfcrel",
            notificationKey = "debug-wallet-${System.currentTimeMillis()}",
            postedAt = Instant.now(),
            title = "COMERCIO DEMO LOCAL 01",
            text = "CLP11,000 con Banco Demo Crédito ••1111",
            subText = null,
            bigText = null,
            rawExtras = "debug=true"
        )
        val expense = application.container.expenseRepository.ingestWalletSnapshot(snapshot)
        if (expense != null) {
            WorkManager.getInstance(application).enqueue(
                OneTimeWorkRequestBuilder<EnrichExpenseWorker>()
                    .setInputData(workDataOf(EnrichExpenseWorker.EXPENSE_ID to expense.id))
                    .build()
            )
            WorkManager.getInstance(application).enqueue(
                OneTimeWorkRequestBuilder<LocationCaptureWorker>()
                    .setInputData(workDataOf(LocationCaptureWorker.EXPENSE_ID to expense.id))
                    .build()
            )
            NotificationPublisher.show(application, expense)
        }
        return expense
    }
}
