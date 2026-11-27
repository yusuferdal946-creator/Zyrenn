package com.zyren.app

import com.zyren.app.core.engines.AutonomyPermissionManager
import com.zyren.app.core.engines.DecisionEngine
import com.zyren.app.core.engines.ProactiveSuggestionEngine
import com.zyren.app.core.engines.RecoveryManager
import com.zyren.app.core.engines.SelfEvaluationEngine
import com.zyren.app.core.engines.TaskPlanner
import com.zyren.app.core.security.Capability
import org.junit.Assert.*
import org.junit.Test

class EnginesTest {
    // --- DecisionEngine
    @Test fun decisionMatchesToolAndFlagsCritical() {
        val d = DecisionEngine()
        val safe = d.decide("pil kaç", setOf("pil_durumu"))
        assertEquals("pil_durumu", safe.toolName); assertFalse(safe.requiresApproval)
        val crit = d.decide("Ahmet'e mesaj gönder", setOf("mesaj_gonder"))
        assertTrue(crit.requiresApproval)
    }
    @Test fun decisionNoMatchReturnsNull() {
        val d = DecisionEngine()
        val r = d.decide("merhaba nasılsın", setOf("pil_durumu"))
        assertNull(r.toolName); assertFalse(r.requiresApproval)
    }
    @Test fun decisionIgnoresUnavailableTool() {
        val d = DecisionEngine()
        val r = d.decide("pil kaç", setOf("web_arastir"))
        assertNull(r.toolName)
    }
    @Test fun isCriticalSet() {
        val d = DecisionEngine()
        assertTrue(d.isCritical("sms_gonder")); assertTrue(d.isCritical("dosya_sil")); assertFalse(d.isCritical("pil_durumu"))
    }

    // --- SelfEvaluationEngine
    @Test fun selfEvaluationFlagsBlankAndShort() {
        val e = SelfEvaluationEngine()
        assertTrue(e.evaluate("", "merhaba").issues.isNotEmpty())
        assertTrue(e.evaluate("ok", "merhaba").issues.isNotEmpty())
    }
    @Test fun selfEvaluationScoreRange() {
        val e = SelfEvaluationEngine()
        val ev = e.evaluate("Pil seviyen %65.", "pil kaç?")
        assertTrue(ev.score in 0..100)
    }
    @Test fun selfEvaluationQuestionAnswerCheck() {
        val e = SelfEvaluationEngine()
        val ev = e.evaluate("Güzel bir gün.", "pil kaç?")
        assertTrue(ev.issues.isNotEmpty())  // soruya yanıt anahtarı yok
    }

    // --- ProactiveSuggestionEngine
    @Test fun suggestionsForPendingTaskAndContext() {
        val s = ProactiveSuggestionEngine()
        assertTrue(s.suggest("", hasPendingTask = true).isNotEmpty())
        assertTrue(s.suggest("pil düşük", hasPendingTask = false).any { it.contains("Pil") })
    }

    // --- AutonomyPermissionManager
    @Test fun autonomyRequiresApprovalOnlyForCritical() {
        val a = AutonomyPermissionManager()
        assertTrue(a.requiresApproval(Capability.SMS_SEND))
        assertFalse(a.requiresApproval(Capability.FILE_READ))
        assertTrue(a.canActAutonomously(Capability.FILE_READ))
        assertFalse(a.canActAutonomously(Capability.SMS_SEND))
    }

    // --- TaskPlanner
    @Test fun taskPlannerSplitsStepsAndPrioritizes() {
        val p = TaskPlanner()
        val t = p.plan("Acil rapor", "adım1\nadım2;adım3")
        assertEquals(3, t.steps.size); assertEquals(9, t.priority)
        val t2 = p.plan("Sıradan iş", "")
        assertEquals(1, t2.steps.size); assertEquals(5, t2.priority)
    }

    // --- RecoveryManager
    @Test fun recoveryMatchesFailure() {
        val r = RecoveryManager()
        assertTrue(r.recover("bağlantı kurulamadı", 1).contains("Bağlantı"))
        assertTrue(r.recover("API anahtarı yok", 1).contains("anahtarı"))
        assertTrue(r.recover("her şey bozuldu", 3).contains("böl"))
    }
}
