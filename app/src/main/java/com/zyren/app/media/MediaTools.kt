package com.zyren.app.media

import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import java.io.File
import java.nio.ByteBuffer

/**
 * Yeniden kodlamadan kırpma/birleştirme (MP4). Birleştirmede dosyalar aynı kodek/çözünürlükte olmalı.
 * Kırpma en yakın ÖNCEKİ anahtar kareden başlar (birkaç saniye fark olabilir).
 */
object MediaTools {
    private class Seg(val file: File, val startMs: Long, val endMs: Long)

    fun trim(input: File, output: File, startMs: Long, endMs: Long) =
        run(listOf(Seg(input, startMs, endMs)), output)

    fun merge(inputs: List<File>, output: File) {
        require(inputs.size >= 2) { "En az 2 dosya gerekli" }
        run(inputs.map { Seg(it, 0, Long.MAX_VALUE) }, output)
    }

    private fun run(segs: List<Seg>, output: File) {
        output.parentFile?.mkdirs()
        val mux = MediaMuxer(output.path, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val typeMap = HashMap<String, Int>()
        var started = false
        var offsetUs = 0L
        val buf = ByteBuffer.allocate(2 * 1024 * 1024)
        val info = MediaCodec.BufferInfo()
        try {
            for ((si, seg) in segs.withIndex()) {
                val ex = MediaExtractor()
                try {
                    ex.setDataSource(seg.file.path)
                    val local = HashMap<Int, String>()
                    for (i in 0 until ex.trackCount) {
                        val f = ex.getTrackFormat(i)
                        val mime = f.getString(MediaFormat.KEY_MIME) ?: continue
                        val type = when {
                            mime.startsWith("video/") -> "video"
                            mime.startsWith("audio/") -> "audio"
                            else -> continue
                        }
                        if (si == 0) typeMap[type] = mux.addTrack(f)
                        else if (typeMap[type] == null) throw IllegalArgumentException("Biçim uyumsuz: ${seg.file.name}")
                        ex.selectTrack(i)
                        local[i] = type
                    }
                    if (si == 0) {
                        val mmr = MediaMetadataRetriever()
                        try {
                            mmr.setDataSource(seg.file.path)
                            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()?.let { mux.setOrientationHint(it) }
                        } finally { mmr.release() }
                        mux.start(); started = true
                    }
                    ex.seekTo(seg.startMs * 1000, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
                    var base = -1L
                    var lastUs = offsetUs
                    while (true) {
                        val size = ex.readSampleData(buf, 0)
                        if (size < 0) break
                        val t = ex.sampleTime
                        if (seg.endMs != Long.MAX_VALUE && t > seg.endMs * 1000) break
                        if (base < 0) base = t
                        val pts = maxOf(0L, t - base) + offsetUs
                        val sync = (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0
                        info.set(0, size, pts, if (sync) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                        val track = typeMap[local[ex.sampleTrackIndex]]
                        if (track != null) mux.writeSampleData(track, buf, info)
                        if (pts > lastUs) lastUs = pts
                        ex.advance()
                    }
                    offsetUs = lastUs + 20_000
                } finally { ex.release() }
            }
        } finally {
            if (started) runCatching { mux.stop() }
            mux.release()
        }
    }
}
