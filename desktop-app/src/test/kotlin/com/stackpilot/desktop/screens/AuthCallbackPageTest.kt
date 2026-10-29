package com.stackpilot.desktop.screens

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Guards the page the browser lands on after the site hands the session back.
 *
 * The first version wrote raw UTF-8 bytes with no charset declaration anywhere,
 * so the browser fell back to Windows-1252 and the reader saw "âœ“ Signed in â€"".
 */
class AuthCallbackPageTest {

    @Test
    fun `page declares utf-8 so the copy is not mojibake`() {
        val html = callbackPage(ok = true)
        assertTrue(html.contains("<meta charset=\"utf-8\">"), "page must declare its charset")
        assertTrue(html.contains("You're signed in"), "success copy is missing")
        assertFalse(html.contains("\u00e2\u0153"), "page still contains mojibake characters")
        assertFalse(html.contains("\u00e2\u20ac"), "page still contains mojibake characters")
    }

    @Test
    fun `page carries the tool logo and name`() {
        val html = callbackPage(ok = true)
        assertTrue(
            html.contains("data:image/png;base64,"),
            "app logo should be inlined as a data URI so the page needs no network"
        )
        assertTrue(html.contains("StackPilot"), "brand name is missing")
    }

    @Test
    fun `cancelled variant renders its own copy`() {
        val html = callbackPage(ok = false)
        assertTrue(html.contains("Sign-in wasn't completed"))
        assertTrue(html.contains("StackPilot"))
    }
}
