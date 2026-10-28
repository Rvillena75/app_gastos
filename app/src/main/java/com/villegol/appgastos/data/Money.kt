package com.villegol.appgastos.data

import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

fun formatMoney(amountMinor: Long?, currency: String = "CLP"): String {
    if (amountMinor == null) return "Monto desconocido"
    val formatter = NumberFormat.getCurrencyInstance(Locale("es", "CL"))
    formatter.currency = runCatching { Currency.getInstance(currency) }.getOrDefault(Currency.getInstance("CLP"))
    val divisor = if (currency == "CLP") 1.0 else 100.0
    return formatter.format(amountMinor / divisor)
}
