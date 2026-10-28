package com.villegol.appgastos.source.wallet

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.villegol.appgastos.GastosApplication
import com.villegol.appgastos.MainActivity
import com.villegol.appgastos.R
import com.villegol.appgastos.data.Expense
import com.villegol.appgastos.data.NotificationSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.Instant
import java.util.Currency
import java.util.Locale

private const val GOOGLE_WALLET_PACKAGE = "com.google.android.apps.walletnfcrel"
private const val ACTION_CHANNEL = "wallet_events"

class WalletNotificationListenerService : NotificationListenerService() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        NotificationPublisher.createChannel(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName != GOOGLE_WALLET_PACKAGE) return

        val extras = sbn.notification.extras
        val snapshot = NotificationSnapshot(
            packageName = sbn.packageName,
            notificationKey = sbn.key,
            postedAt = Instant.ofEpochMilli(sbn.postTime),
            title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString(),
            text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString(),
            subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString(),
            bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString(),
            rawExtras = extras.keySet().associateWith { extras.get(it)?.toString() }.toString()
        )

        serviceScope.launch {
            val application = application as GastosApplication
            val expense = application.container.expenseRepository.ingestWalletSnapshot(snapshot)
            if (expense != null) {
                val request = OneTimeWorkRequestBuilder<EnrichExpenseWorker>()
                    .setInputData(workDataOf(EnrichExpenseWorker.EXPENSE_ID to expense.id))
                    .build()
                WorkManager.getInstance(application).enqueue(request)
                val locationRequest = OneTimeWorkRequestBuilder<com.villegol.appgastos.source.location.LocationCaptureWorker>()
                    .setInputData(workDataOf(com.villegol.appgastos.source.location.LocationCaptureWorker.EXPENSE_ID to expense.id))
                    .build()
                WorkManager.getInstance(application).enqueue(locationRequest)
                NotificationPublisher.show(application, expense)
            }
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }
}

object NotificationPublisher {
    fun createChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                ACTION_CHANNEL,
                "Confirmaciones de gastos",
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }

    fun show(context: Context, expense: Expense) {
        val openIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EXPENSE_ID, expense.id)
            putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_EDIT)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            expense.id.hashCode(),
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val receiptIntent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_EXPENSE_ID, expense.id)
            putExtra(MainActivity.EXTRA_ACTION, MainActivity.ACTION_RECEIPT)
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val receiptPendingIntent = PendingIntent.getActivity(
            context,
            expense.id.hashCode() + 1,
            receiptIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val deleteIntent = Intent(context, ExpenseActionReceiver::class.java).apply {
            action = ExpenseActionReceiver.ACTION_DELETE
            putExtra(ExpenseActionReceiver.EXTRA_EXPENSE_ID, expense.id)
        }
        val deletePendingIntent = PendingIntent.getBroadcast(
            context,
            expense.id.hashCode() + 2,
            deleteIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val amount = formatMoney(expense.amountMinor, expense.currency)
        val title = "$amount · ${expense.merchantName ?: "Comercio desconocido"}"
        val builder = NotificationCompat.Builder(context, ACTION_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle(title)
            .setContentText("Toca para editar o identificar este gasto")
            .setContentIntent(openPendingIntent)
            .setAutoCancel(true)
            .addAction(R.drawable.ic_launcher, "BOLETA", receiptPendingIntent)
            .addAction(R.drawable.ic_launcher, "EDITAR", openPendingIntent)
            .addAction(R.drawable.ic_launcher, "BORRAR", deletePendingIntent)

        try {
            NotificationManagerCompat.from(context).notify(expense.id.hashCode(), builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS is optional for correctness: the expense is already persisted.
        }
    }

    private fun formatMoney(amountMinor: Long?, currency: String): String {
        if (amountMinor == null) return "Monto desconocido"
        val formatter = NumberFormat.getCurrencyInstance(Locale("es", "CL"))
        formatter.currency = runCatching { Currency.getInstance(currency) }.getOrDefault(Currency.getInstance("CLP"))
        val divisor = if (currency == "CLP") 1.0 else 100.0
        return formatter.format(amountMinor / divisor)
    }
}
