package com.zyren.app

import com.zyren.app.core.workspace.BackupCrypto
import com.zyren.app.core.workspace.LimitedOutputStream
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.Random

class BackupCryptoTest {
    private val pass = "doğru-parola-123".toCharArray()
    private val it100k = 100_000

    private fun enc(data: ByteArray, p: CharArray = pass): ByteArray { val o = ByteArrayOutputStream(); BackupCrypto.encrypt(ByteArrayInputStream(data), o, p, it100k); return o.toByteArray() }
    private fun dec(blob: ByteArray, p: CharArray = pass): ByteArray { val o = ByteArrayOutputStream(); BackupCrypto.decrypt(ByteArrayInputStream(blob), o, p); return o.toByteArray() }
    private fun rnd(n: Int) = ByteArray(n).also { Random(42).nextBytes(it) }

    @Test fun roundTripSizes() {
        for (n in listOf(0, 1, 100, BackupCrypto.CHUNK - 1, BackupCrypto.CHUNK, BackupCrypto.CHUNK + 1, BackupCrypto.CHUNK * 2, 200_000)) {
            val d = rnd(n); assertArrayEquals("n=$n", d, dec(enc(d)))
        }
    }
    @Test fun ciphertextHidesPlaintext() {
        val d = "ÇOK-GİZLİ-METİN-ÇOK-GİZLİ-METİN".toByteArray().let { it + it + it }
        val blob = enc(d); assertFalse(String(blob, Charsets.ISO_8859_1).contains("GİZLİ".toByteArray().toString(Charsets.ISO_8859_1)))
        assertTrue(BackupCrypto.isEncrypted(blob)); assertFalse(BackupCrypto.isEncrypted("PK\u0003\u0004zip".toByteArray()))
    }
    @Test fun samePlaintextDifferentCiphertext() { val d = rnd(1000); assertFalse(enc(d).contentEquals(enc(d))) }
    @Test fun wrongPassphraseRejected() {
        val blob = enc(rnd(5000))
        val e = assertThrows(IllegalArgumentException::class.java) { dec(blob, "yanlis-parola-99".toCharArray()) }
        assertTrue(e.message!!.contains("Yanlış parola"))
    }
    @Test fun tamperingDetected() {
        val d = rnd(150_000); val blob = enc(d)
        for (pos in listOf(10, 20, 40, 100, blob.size / 2, blob.size - 5)) {
            val t = blob.copyOf(); t[pos] = (t[pos].toInt() xor 1).toByte()
            assertThrows("pos=$pos", IllegalArgumentException::class.java) { dec(t) }
        }
    }
    @Test fun truncationDetected() {
        val blob = enc(rnd(200_000))
        assertThrows(IllegalArgumentException::class.java) { dec(blob.copyOf(blob.size - 10)) }
        // son parçayı tamamen at → önceki parça "son" sayılır → AAD uyuşmaz
        val chunkLen = 4 + BackupCrypto.CHUNK + 16
        assertThrows(IllegalArgumentException::class.java) { dec(blob.copyOf(33 + chunkLen)) }
    }
    @Test fun chunkReorderDetected() {
        val blob = enc(rnd(3 * BackupCrypto.CHUNK - 1000))
        val h = 33; val c = 4 + BackupCrypto.CHUNK + 16
        val a = blob.copyOfRange(h, h + c); val b = blob.copyOfRange(h + c, h + 2 * c); val rest = blob.copyOfRange(h + 2 * c, blob.size)
        assertThrows(IllegalArgumentException::class.java) { dec(blob.copyOfRange(0, h) + b + a + rest) }
    }
    @Test fun appendedGarbageRejected() { assertThrows(IllegalArgumentException::class.java) { dec(enc(rnd(500)) + byteArrayOf(1, 2, 3, 4, 5, 6)) } }
    @Test fun notAnEncryptedFile() {
        assertThrows(IllegalArgumentException::class.java) { dec("PK-bu-bir-zip-dosyasi-degil-sifreli-0123456789".toByteArray()) }
        assertThrows(IllegalArgumentException::class.java) { dec(ByteArray(0)) }
    }
    @Test fun weakPassphrasesRejected() {
        assertNotNull(BackupCrypto.checkPassphrase("kisa".toCharArray())); assertNotNull(BackupCrypto.checkPassphrase("1234567890123".toCharArray()))
        assertNull(BackupCrypto.checkPassphrase("güçlü-parola-9".toCharArray()))
        assertThrows(IllegalArgumentException::class.java) { enc(rnd(10), "kisa".toCharArray()) }
    }
    @Test fun iterationBoundsEnforced() {
        assertThrows(IllegalArgumentException::class.java) { BackupCrypto.encrypt(ByteArrayInputStream(ByteArray(1)), ByteArrayOutputStream(), pass, 10) }
        val blob = enc(rnd(10)); blob[9] = 0; blob[10] = 0; blob[11] = 0; blob[12] = 1   // tur=1 → reddedilmeli (DoS koruması)
        assertThrows(IllegalArgumentException::class.java) { dec(blob) }
    }
    @Test fun passphraseNotRetained() { val p = "silinecek-parola-1".toCharArray(); enc(rnd(10), p); /* çağıran siler; şifreleme parolayı değiştirmez */ assertEquals("silinecek-parola-1", String(p)) }
    @Test fun limitedOutputStream() {
        assertThrows(IllegalArgumentException::class.java) { LimitedOutputStream(ByteArrayOutputStream(), 10).write(ByteArray(11)) }
        LimitedOutputStream(ByteArrayOutputStream(), 10).write(ByteArray(10))
    }
}
