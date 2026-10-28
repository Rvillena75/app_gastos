package com.villegol.appgastos.source.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.villegol.appgastos.GastosApplication
import com.villegol.appgastos.source.wallet.awaitResult
import java.time.Instant

class LocationCaptureWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val expenseId = inputData.getString(EXPENSE_ID) ?: return Result.failure()
        val hasFine = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return Result.success()

        return try {
            val client = LocationServices.getFusedLocationProviderClient(applicationContext)
            val location = runCatching {
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, CancellationTokenSource().token)
                    .awaitResult()
            }.getOrNull() ?: client.lastLocation.awaitResult()
            if (location != null) {
                val app = applicationContext as GastosApplication
                app.container.expenseRepository.attachLocationEvidence(
                    expenseId = expenseId,
                    latitude = location.latitude,
                    longitude = location.longitude,
                    accuracy = location.accuracy,
                    timestamp = Instant.ofEpochMilli(location.time)
                )
            }
            Result.success()
        } catch (_: SecurityException) {
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    companion object { const val EXPENSE_ID = "expense_id" }
}
