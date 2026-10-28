package com.villegol.appgastos.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import java.time.YearMonth
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.budgetDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "monthly-budgets"
)

class BudgetRepository(context: Context) {
    private val dataStore = context.applicationContext.budgetDataStore

    fun observeMonthlyBudget(month: YearMonth): Flow<Long?> = dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { preferences -> preferences[budgetKey(month)] }

    suspend fun setMonthlyBudget(month: YearMonth, amountMinor: Long?) {
        require(amountMinor == null || amountMinor >= 0L) { "El presupuesto no puede ser negativo." }
        dataStore.edit { preferences ->
            if (amountMinor == null) {
                preferences.remove(budgetKey(month))
            } else {
                preferences[budgetKey(month)] = amountMinor
            }
        }
    }

    private fun budgetKey(month: YearMonth) = longPreferencesKey("budget-$month")
}
