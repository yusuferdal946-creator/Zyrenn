package com.zyren.app

import com.zyren.app.atelier.MiniAppGuard
import org.junit.Assert.*
import org.junit.Test

class MiniAppGuardTest {
    private val good = "<!doctype html><html><head><title>t</title></head><body><button onclick=\"document.title='x'\">Tamam</button><script>var n=0;</script></body></html>"

    @Test fun cleanAppPasses() = assertTrue(MiniAppGuard.validate(good).isEmpty())

    @Test fun networkAndExternalAreRejected() {
        val bad = listOf(
            "<html><body><script>fetch('https://x.com')</script></body></html>",
            "<html><body><script src=\"https://cdn.x/a.js\"></script></body></html>",
            "<html><body><img src='http://x/y.png'></body></html>",
            "<html><body><style>@import 'a.css'; body{background:url(https://x/y.png)}</style></body></html>",
            "<html><body><script>new WebSocket('wss://x')</script></body></html>",
            "<html><body><iframe src='about:blank'></iframe></body></html>",
            "<html><body><script>eval('1')</script></body></html>",
            "<html><body><script>navigator.mediaDevices.getUserMedia({video:true})</script></body></html>",
            "<html><body><script>location.href='https://x'</script></body></html>",
            "<html><body><a href=\"javascript:alert(1)\">x</a></body></html>"
        )
        bad.forEach { assertTrue("reddedilmeliydi: $it", MiniAppGuard.validate(it).isNotEmpty()) }
    }

    @Test fun oversizeAndBlankRejected() {
        assertTrue(MiniAppGuard.validate("").isNotEmpty())
        assertTrue(MiniAppGuard.validate("<html><body>" + "a".repeat(MiniAppGuard.MAX_BYTES) + "</body></html>").isNotEmpty())
    }

    @Test fun extractFromFenceAndRaw() {
        assertEquals(good, MiniAppGuard.extractHtml("Tamam:\n```html\n$good\n```\nbitti"))
        assertEquals(good, MiniAppGuard.extractHtml("başlık $good sonra"))
        assertNull(MiniAppGuard.extractHtml("hiç kod yok"))
    }

    @Test fun prepareInjectsCspFirstAndIsIdempotent() {
        val p = MiniAppGuard.prepare(good)
        assertTrue(p.indexOf("Content-Security-Policy") in 0 until p.indexOf("<title>"))
        assertTrue(p.contains("connect-src 'none'"))
        assertEquals(p, MiniAppGuard.prepare(p))
        assertTrue(MiniAppGuard.prepare("<div>merhaba</div>").contains("Content-Security-Policy"))
    }

    @Test fun slugTurkishAndEmpty() {
        assertEquals("hesap-defteri", MiniAppGuard.slug("Hesap Defteri"))
        assertEquals("cgisuo", MiniAppGuard.slug("çğışüö"))
        assertEquals("uygulama", MiniAppGuard.slug("!!!"))
        assertTrue(MiniAppGuard.slug("a".repeat(100)).length <= 24)
    }
}
