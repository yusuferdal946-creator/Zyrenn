package com.zyren.app

import com.zyren.app.core.tools.JsSandbox
import org.junit.Assert.*
import org.junit.Test

class JsSandboxTest {
    @Test fun arithmetic() = assertEquals("3", JsSandbox().evaluate("1+2").value)
    @Test fun consoleLog() { val r = JsSandbox().evaluate("console.log('a', 1); console.log({x:1}); 5"); assertEquals("a 1\n{\"x\":1}", r.output); assertEquals("5", r.value) }
    @Test fun infiniteLoopStopped() {
        val t0 = System.currentTimeMillis()
        val r = JsSandbox(timeoutMs = 500).evaluate("while(true){}")
        assertTrue(r.error?.contains("zaman aşımı") == true)
        assertTrue("çok geç: ${System.currentTimeMillis() - t0}", System.currentTimeMillis() - t0 < 4000)
    }
    @Test fun tryCatchCannotSwallowTimeout() {
        val r = JsSandbox(timeoutMs = 500).evaluate("while(true){ try { for(;;){} } catch(e) {} }")
        assertTrue(r.error?.contains("zaman aşımı") == true)
    }
    @Test fun noJavaAccess() {
        val s = JsSandbox()
        assertNotNull(s.evaluate("java.lang.System.exit(0)").error)
        assertNotNull(s.evaluate("Packages.java.lang.System.exit(0)").error)
        assertNotNull(s.evaluate("importPackage(java.io)").error)
    }
    @Test fun deepRecursionIsAnError() { assertNotNull(JsSandbox().evaluate("function f(){return f()+1} f()").error) }
    @Test fun syntaxErrorReported() { assertNotNull(JsSandbox().evaluate("var = ;").error) }
    @Test fun customToolCall() { assertEquals("\"7\"", JsSandbox().call("function run(a){return String(a.x+a.y)}", "{\"x\":3,\"y\":4}").value) }
    @Test fun tooLongCodeRejected() { assertNotNull(JsSandbox().evaluate("1;".repeat(20_001)).error) }
    @Test fun outputIsCapped() { assertTrue(JsSandbox().evaluate("for(var i=0;i<5000;i++){console.log('xxxxxxxxxxxxxxxxxxxx')}").output.length <= 21_000) }
}
