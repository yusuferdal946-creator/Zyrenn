package com.zyren.app.core.workspace

import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Yedek ZIP'i için parola tabanlı şifreleme (AES-256-GCM, parçalı/akışlı).
 *
 * Anahtar yönetimi: anahtar HİÇBİR YERDE saklanmaz. Kullanıcı parolasından PBKDF2-HMAC-SHA256 (rastgele 16 bayt tuz,
 * 200.000 tur; tur sayısı başlıkta) ile türetilir, kullanımdan sonra bellekten silinir. Parola kaybolursa dosya KURTARILAMAZ.
 *
 * Biçim: MAGIC(9) | tur(4) | tuz(16) | nonce-öneki(4) || { uzunluk(4) | şifreli-parça(≤64 KB + 16 etiket) }...
 * Her parçanın nonce'u = önek(4) + sayaç(8); AAD = başlık + sayaç + "son parça" bayrağı. Bu sayede parça sırası değiştirilemez,
 * silinemez, dosya kesilemez ve tek bir bayt bile değiştirilemez (GCM etiketi doğrulaması başarısız olur).
 */
object BackupCrypto {
    private val MAGIC = "ZYRENENC1".toByteArray(Charsets.US_ASCII)
    const val CHUNK = 64 * 1024
    const val MIN_PASS = 10
    const val DEFAULT_ITER = 200_000
    private const val SALT = 16
    private const val PREFIX = 4
    private const val TAG = 16
    private const val HEADER = 9 + 4 + SALT + PREFIX

    fun isEncrypted(head: ByteArray, n: Int = head.size): Boolean = n >= MAGIC.size && MAGIC.indices.all { head[it] == MAGIC[it] }

    /** Parola zayıfsa açıklama döner, uygunsa null. */
    fun checkPassphrase(p: CharArray): String? = when {
        p.size < MIN_PASS -> "Parola en az $MIN_PASS karakter olmalı"
        p.all { it.isDigit() } -> "Parola yalnızca rakamlardan oluşmamalı"
        else -> null
    }

    private fun deriveKey(pass: CharArray, salt: ByteArray, iter: Int): SecretKeySpec {
        val spec = PBEKeySpec(pass, salt, iter, 256)
        try {
            val kb = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            val key = SecretKeySpec(kb, "AES")   // kopyalar
            Arrays.fill(kb, 0)
            return key
        } finally { spec.clearPassword() }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, prefix: ByteArray, header: ByteArray, counter: Long, last: Boolean): Cipher {
        val iv = ByteBuffer.allocate(12).put(prefix).putLong(counter).array()
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(mode, key, GCMParameterSpec(128, iv))
        c.updateAAD(header)
        c.updateAAD(ByteBuffer.allocate(9).putLong(counter).put(if (last) 1.toByte() else 0.toByte()).array())
        return c
    }

    private fun readUpTo(i: InputStream, n: Int): ByteArray {
        val buf = ByteArray(n); var off = 0
        while (off < n) { val r = i.read(buf, off, n - off); if (r < 0) break; off += r }
        return if (off == n) buf else buf.copyOf(off)
    }

    private fun readFully(i: InputStream, buf: ByteArray) {
        var off = 0
        while (off < buf.size) { val r = i.read(buf, off, buf.size - off); if (r < 0) throw EOFException("Kesik paket"); off += r }
    }

    /** -1: temiz dosya sonu. */
    private fun readLen(i: InputStream): Int {
        val b = ByteArray(4); var off = 0
        while (off < 4) { val r = i.read(b, off, 4 - off); if (r < 0) break; off += r }
        if (off == 0) return -1
        require(off == 4) { "Kesik paket" }
        return ByteBuffer.wrap(b).int
    }

    fun encrypt(input: InputStream, out: OutputStream, pass: CharArray, iterations: Int = DEFAULT_ITER, rnd: SecureRandom = SecureRandom()) {
        checkPassphrase(pass)?.let { throw IllegalArgumentException(it) }
        require(iterations in 100_000..2_000_000) { "Tur sayısı 100.000–2.000.000 olmalı" }
        val salt = ByteArray(SALT).also { rnd.nextBytes(it) }
        val prefix = ByteArray(PREFIX).also { rnd.nextBytes(it) }
        val header = ByteBuffer.allocate(HEADER).put(MAGIC).putInt(iterations).put(salt).put(prefix).array()
        out.write(header)
        val key = deriveKey(pass, salt, iterations)
        var cur = readUpTo(input, CHUNK)
        var counter = 0L
        while (true) {
            val next = if (cur.size == CHUNK) readUpTo(input, CHUNK) else ByteArray(0)
            val last = next.isEmpty()
            val ct = cipher(Cipher.ENCRYPT_MODE, key, prefix, header, counter, last).doFinal(cur)
            out.write(ByteBuffer.allocate(4).putInt(ct.size).array()); out.write(ct)
            if (last) break
            cur = next; counter++
        }
        out.flush()
    }

    /** Yanlış parola/bozuk/kurcalanmış/kesik paket → IllegalArgumentException. Hata olursa çıktıya o noktaya kadar yazılmış olabilir; çağıran siler. */
    fun decrypt(input: InputStream, out: OutputStream, pass: CharArray) {
        val header = ByteArray(HEADER)
        try { readFully(input, header) } catch (e: EOFException) { throw IllegalArgumentException("Şifreli Zyren paketi değil", e) }
        require(isEncrypted(header)) { "Şifreli Zyren paketi değil" }
        val bb = ByteBuffer.wrap(header, MAGIC.size, 4 + SALT + PREFIX)
        val iter = bb.int
        require(iter in 100_000..2_000_000) { "Geçersiz paket başlığı" }
        val salt = ByteArray(SALT); bb.get(salt)
        val prefix = ByteArray(PREFIX); bb.get(prefix)
        val key = deriveKey(pass, salt, iter)
        var counter = 0L
        var len = readLen(input)
        require(len >= 0) { "Boş veya bozuk paket" }
        while (true) {
            require(len in TAG..(CHUNK + TAG)) { "Bozuk paket (parça boyutu)" }
            val ct = ByteArray(len)
            try { readFully(input, ct) } catch (e: EOFException) { throw IllegalArgumentException("Kesik paket", e) }
            val nextLen = readLen(input)
            val last = nextLen < 0
            val plain = try { cipher(Cipher.DECRYPT_MODE, key, prefix, header, counter, last).doFinal(ct) }
            catch (e: GeneralSecurityException) { throw IllegalArgumentException("Yanlış parola veya paket bozuk/değiştirilmiş/kesik", e) }
            out.write(plain)
            if (last) break
            len = nextLen; counter++
        }
        out.flush()
    }
}

/** Yazılan bayt sayısını sınırlar (şifre çözülürken zip bombası benzeri şişmeye karşı). */
class LimitedOutputStream(private val o: OutputStream, private val max: Long) : OutputStream() {
    private var n = 0L
    override fun write(b: Int) { count(1); o.write(b) }
    override fun write(b: ByteArray, off: Int, len: Int) { count(len.toLong()); o.write(b, off, len) }
    override fun flush() = o.flush()
    private fun count(k: Long) { n += k; require(n <= max) { "Çözülen veri boyut sınırını aştı" } }
}
