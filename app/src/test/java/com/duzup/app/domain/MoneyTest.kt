package com.duzup.app.domain

import com.duzup.app.core.model.Money
import com.duzup.app.core.model.Money.Parsed
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class MoneyTest {
    private val us = Locale.US

    @Test
    fun fractionDigits_areCurrencyAware() {
        assertEquals(2, Money.fractionDigits("USD"))
        assertEquals(0, Money.fractionDigits("JPY"))
        assertEquals(3, Money.fractionDigits("KWD"))
    }

    @Test
    fun parse_wholeAndDecimalAmounts() {
        assertEquals(Parsed.Ok(850_000), Money.parse("8500", "PKR"))
        assertEquals(Parsed.Ok(125_050), Money.parse("1,250.50", "USD"))
        assertEquals(Parsed.Ok(5_000), Money.parse("5000", "JPY"))
        assertEquals(Parsed.Ok(1_234_567), Money.parse("1234.567", "KWD"))
        assertEquals(Parsed.Ok(50), Money.parse(".5", "USD"))
    }

    @Test
    fun parse_rejectsBadInput() {
        assertEquals(Parsed.Empty, Money.parse("  ", "USD"))
        assertEquals(Parsed.Invalid, Money.parse("abc", "USD"))
        assertEquals(Parsed.Invalid, Money.parse("12.3.4", "USD"))
        assertEquals(Parsed.Invalid, Money.parse("-5", "USD"))
        assertEquals(Parsed.NotPositive, Money.parse("0", "USD"))
        assertEquals(Parsed.NotPositive, Money.parse("0.00", "USD"))
        assertEquals(Parsed.TooManyDecimals, Money.parse("1.999", "USD"))
        assertEquals(Parsed.TooManyDecimals, Money.parse("1.5", "JPY"))
        assertEquals(Parsed.TooLarge, Money.parse("999999999999999", "USD"))
    }

    @Test
    fun format_dropsFractionForWholeAmounts_andGroups() {
        assertEquals("$8,500", Money.format(850_000, "USD", us))
        assertEquals("$1,250.50", Money.format(125_050, "USD", us))
        assertEquals("Rs. 18,490", Money.format(1_849_000, "PKR", us))
        assertEquals("¥5,000", Money.format(5_000, "JPY", us))
    }

    @Test
    fun toInputText_roundTripsThroughParse() {
        for ((minor, code) in listOf(850_000L to "PKR", 125_050L to "USD", 5_000L to "JPY", 1_234_567L to "KWD")) {
            val text = Money.toInputText(minor, code)
            assertEquals(Parsed.Ok(minor), Money.parse(text, code))
        }
        assertEquals("8500", Money.toInputText(850_000, "PKR"))
        assertEquals("1250.50", Money.toInputText(125_050, "USD"))
    }

    @Test
    fun supportedCurrencies_areAllValidIsoCodes() {
        assertTrue(Money.supportedCurrencies.all(Money::isValidCode))
    }
}
