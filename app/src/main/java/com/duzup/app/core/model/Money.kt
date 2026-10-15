package com.duzup.app.core.model

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/** Currency-aware money handling. Amounts are always integer minor units, never floating point. */
object Money {

    sealed interface Parsed {
        data class Ok(val minorUnits: Long) : Parsed
        data object Empty : Parsed
        data object Invalid : Parsed
        data object TooManyDecimals : Parsed
        data object NotPositive : Parsed
        data object TooLarge : Parsed
    }

    private const val MAX_MINOR_UNITS = 99_999_999_999_999L

    val supportedCurrencies: List<String> = listOf(
        "PKR", "USD", "EUR", "GBP", "INR", "AED", "SAR", "CAD", "AUD", "JPY", "CNY", "BDT",
        "LKR", "NPR", "TRY", "KRW", "SGD", "MYR", "IDR", "PHP", "EGP", "ZAR", "NGN", "KES",
        "BRL", "MXN", "CHF", "SEK", "NOK", "DKK", "PLN", "KWD", "BHD", "QAR", "OMR",
    )

    private val symbolOverrides = mapOf("PKR" to "Rs.")

    fun isValidCode(code: String): Boolean = runCatching { Currency.getInstance(code) }.isSuccess

    fun fractionDigits(code: String): Int =
        runCatching { Currency.getInstance(code).defaultFractionDigits }.getOrDefault(2).coerceAtLeast(0)

    /** Parses user input like "8500", "1,250.50". Rejects zero, negatives and surplus decimals. */
    fun parse(input: String, code: String): Parsed {
        val cleaned = input.trim().replace(",", "").replace(" ", "")
        if (cleaned.isEmpty()) return Parsed.Empty
        if (!cleaned.matches(Regex("""\d*\.?\d*""")) || cleaned == ".") return Parsed.Invalid
        val digits = fractionDigits(code)
        val fractionPart = cleaned.substringAfter('.', "")
        if (fractionPart.length > digits) return Parsed.TooManyDecimals
        val value = runCatching { BigDecimal(cleaned) }.getOrNull() ?: return Parsed.Invalid
        if (value.signum() <= 0) return Parsed.NotPositive
        val minor = value.movePointRight(digits).setScale(0, RoundingMode.UNNECESSARY)
        if (minor > BigDecimal(MAX_MINOR_UNITS)) return Parsed.TooLarge
        return Parsed.Ok(minor.toLong())
    }

    /** Plain editable text for an amount: "8500" or "8500.50" (no grouping, no symbol). */
    fun toInputText(minorUnits: Long, code: String): String {
        val digits = fractionDigits(code)
        val value = BigDecimal.valueOf(minorUnits).movePointLeft(digits)
        return if (minorUnits % pow10(digits) == 0L) value.setScale(0).toPlainString()
        else value.setScale(digits, RoundingMode.UNNECESSARY).toPlainString()
    }

    fun symbolFor(code: String, locale: Locale = Locale.getDefault()): String =
        symbolOverrides[code] ?: runCatching { Currency.getInstance(code).getSymbol(locale) }.getOrDefault(code)

    /** "Rs. 8,500", "$1,250.50". Whole amounts drop the fraction. */
    fun format(minorUnits: Long, code: String, locale: Locale = Locale.getDefault()): String {
        val digits = fractionDigits(code)
        val whole = minorUnits % pow10(digits) == 0L
        val nf = NumberFormat.getNumberInstance(locale).apply {
            minimumFractionDigits = if (whole) 0 else digits
            maximumFractionDigits = if (whole) 0 else digits
            isGroupingUsed = true
        }
        val number = nf.format(BigDecimal.valueOf(minorUnits).movePointLeft(digits))
        val symbol = symbolFor(code, locale)
        val needsSpace = symbol.length > 1 && (symbol.last().isLetter() || symbol.last() == '.')
        return if (needsSpace) "$symbol $number" else "$symbol$number"
    }

    private fun pow10(n: Int): Long {
        var r = 1L
        repeat(n) { r *= 10 }
        return r
    }
}
