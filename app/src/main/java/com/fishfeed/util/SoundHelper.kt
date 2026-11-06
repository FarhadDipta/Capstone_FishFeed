package com.fishfeed.util

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import android.media.Ringtone
import android.media.RingtoneManager
import android.net.Uri
import android.speech.tts.TextToSpeech
import java.util.Locale

/** Helper untuk memutar suara pengingat, variasi pengumuman suara TTS, nada sistem, dan nada custom. */
object SoundHelper {
    const val SOUND_VOICE_1 = "VOICE_ANNOUNCEMENT"  // Standar
    const val SOUND_VOICE_2 = "VOICE_MOTIVATION"    // Motivasi
    const val SOUND_VOICE_3 = "VOICE_FORMAL"        // Formal
    const val SOUND_VOICE_4 = "VOICE_FRIENDLY"      // Ramah
    const val SOUND_VOICE_5 = "VOICE_STORE"         // Toko/Speaker
    const val SOUND_VOICE_6 = "VOICE_HARVEST"       // Panen
    const val SOUND_VOICE_7 = "VOICE_MORNING"       // Pagi
    const val SOUND_VOICE_8 = "VOICE_NEXT_SCHEDULE" // Jadwal
    const val SOUND_VOICE_9 = "VOICE_GENTLE"        // Halus
    const val SOUND_VOICE_10 = "VOICE_SPIRIT"       // Semangat
    const val SOUND_VOICE_11 = "VOICE_SHORT"        // Singkat
    const val SOUND_VOICE_12 = "VOICE_DISCIPLINE"   // Disiplin

    const val SOUND_TYPE_DEFAULT = "DEFAULT"        // Alarm utama HP
    const val SOUND_TYPE_NOTIFICATION = "NOTIFICATION" // Notifikasi pesan
    const val SOUND_TYPE_RINGTONE = "RINGTONE"      // Nada dering telepon

    private var tts: TextToSpeech? = null
    private var activeRingtone: Ringtone? = null
    private var currentAsAlarm = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val stopHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val stopRunnable = Runnable { stopSound() }

    /** Memutar suara uji coba (otomatis mati setelah [durationMs] ms jika tidak dihentikan). */
    fun playTestSound(context: Context, uriString: String?, durationMs: Long = 4_000L, asAlarm: Boolean = false) {
        currentAsAlarm = asAlarm
        stopSound()
        stopHandler.removeCallbacks(stopRunnable)
        when (uriString) {
            SOUND_VOICE_1 -> speakVoiceAnnouncement(context, "Waktunya memberi pakan lele!", asAlarm)
            SOUND_VOICE_2 -> speakVoiceAnnouncement(context, "Pakan tepat, lele sehat, hasil maksimal!", asAlarm)
            SOUND_VOICE_3 -> speakVoiceAnnouncement(context, "Perhatian! Waktunya memberi pakan kolam!", asAlarm)
            SOUND_VOICE_4 -> speakVoiceAnnouncement(context, "Halo Peternak, lele di kolam sudah lapar!", asAlarm)
            SOUND_VOICE_5 -> speakVoiceAnnouncement(context, "Pengumuman: Segera berikan pakan ikan!", asAlarm)
            SOUND_VOICE_6 -> speakVoiceAnnouncement(context, "Kasih pakan tepat waktu biar lele cepat panen!", asAlarm)
            SOUND_VOICE_7 -> speakVoiceAnnouncement(context, "Selamat pagi! Jangan lupa kasih pakan lele hari ini.", asAlarm)
            SOUND_VOICE_8 -> speakVoiceAnnouncement(context, "Waktunya jadwal pakan berikutnya untuk lelemu.", asAlarm)
            SOUND_VOICE_9 -> speakVoiceAnnouncement(context, "Permisi, lele di kolam sedang menunggu makan.", asAlarm)
            SOUND_VOICE_10 -> speakVoiceAnnouncement(context, "Semangat peternak! Waktunya kasih makan lele kesayangan.", asAlarm)
            SOUND_VOICE_11 -> speakVoiceAnnouncement(context, "Waktunya pakan ikan lele!", asAlarm)
            SOUND_VOICE_12 -> speakVoiceAnnouncement(context, "Jaga kedisiplinan pakan agar pertumbuhan lele optimal!", asAlarm)
            SOUND_TYPE_NOTIFICATION -> playSystemSound(context, RingtoneManager.TYPE_NOTIFICATION, asAlarm)
            SOUND_TYPE_RINGTONE -> playSystemSound(context, RingtoneManager.TYPE_RINGTONE, asAlarm)
            null, "", SOUND_TYPE_DEFAULT -> playSystemSound(context, RingtoneManager.TYPE_ALARM, asAlarm)
            else -> {
                val uri = try { Uri.parse(uriString) } catch (_: Exception) { null }
                playRingtoneUri(context, uri ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), asAlarm)
            }
        }
        // Alarm sungguhan berbunyi lebih lama (60 detik) sampai dikonfirmasi / dijeda; uji coba hanya 4 detik
        val dur = if (asAlarm && durationMs == 4_000L) 60_000L else durationMs
        stopHandler.postDelayed(stopRunnable, dur)
    }

    /** Membaca seluruh nada dering / alarm bawaan yang ada di perangkat HP. */
    fun getSystemRingtones(context: Context): List<Pair<String, String>> {
        val list = mutableListOf<Pair<String, String>>()
        try {
            val manager = RingtoneManager(context).apply {
                setType(RingtoneManager.TYPE_ALL)
            }
            val cursor = manager.cursor
            while (cursor.moveToNext()) {
                val title = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX)
                val uri = manager.getRingtoneUri(cursor.position)?.toString()
                if (!title.isNullOrBlank() && uri != null) {
                    list.add("Nada HP: $title" to uri)
                }
            }
        } catch (_: Exception) {}
        return list
    }

    /** Mendapatkan nama/label suara yang ramah dibaca pengguna. */
    fun getSoundLabel(context: Context, soundUri: String): String = when (soundUri) {
        SOUND_VOICE_1 -> "Pengumuman 1: \"Waktunya memberi pakan lele!\""
        SOUND_VOICE_2 -> "Pengumuman 2: \"Pakan tepat, lele sehat, hasil maksimal!\""
        SOUND_VOICE_3 -> "Pengumuman 3: \"Perhatian! Waktunya memberi pakan kolam!\""
        SOUND_VOICE_4 -> "Pengumuman 4: \"Halo Peternak, lele di kolam sudah lapar!\""
        SOUND_VOICE_5 -> "Pengumuman 5: \"Pengumuman: Segera berikan pakan ikan!\""
        SOUND_VOICE_6 -> "Pengumuman 6: \"Kasih pakan tepat waktu biar lele cepat panen!\""
        SOUND_VOICE_7 -> "Pengumuman 7: \"Selamat pagi! Jangan lupa kasih pakan lele.\""
        SOUND_VOICE_8 -> "Pengumuman 8: \"Waktunya jadwal pakan berikutnya.\""
        SOUND_VOICE_9 -> "Pengumuman 9: \"Permisi, lele di kolam sedang menunggu makan.\""
        SOUND_VOICE_10 -> "Pengumuman 10: \"Semangat peternak! Waktunya kasih makan lele.\""
        SOUND_VOICE_11 -> "Pengumuman 11: \"Waktunya pakan ikan lele!\""
        SOUND_VOICE_12 -> "Pengumuman 12: \"Jaga kedisiplinan pakan agar lele optimal.\""
        SOUND_TYPE_DEFAULT -> "Nada Alarm Bawaan HP"
        SOUND_TYPE_NOTIFICATION -> "Nada Notifikasi Pesan"
        SOUND_TYPE_RINGTONE -> "Nada Dering Panggilan"
        else -> {
            try {
                val uri = Uri.parse(soundUri)
                val ringtone = RingtoneManager.getRingtone(context, uri)
                val title = ringtone?.getTitle(context)
                if (!title.isNullOrBlank()) "Audio Custom: $title" else "Audio Custom dari Perangkat"
            } catch (_: Exception) {
                "Audio Custom dari Perangkat"
            }
        }
    }

    private fun playSystemSound(context: Context, type: Int, asAlarm: Boolean) {
        val uri = RingtoneManager.getDefaultUri(type)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
        playRingtoneUri(context, uri, asAlarm)
    }

    private fun toast(context: Context, msg: String) {
        mainHandler.post { Toast.makeText(context.applicationContext, msg, Toast.LENGTH_LONG).show() }
    }

    /** Membunyikan pengumuman suara lewat Text-to-Speech. Bila mesin suara tidak ada, otomatis memakai nada alarm. */
    private fun speakVoiceAnnouncement(context: Context, text: String, asAlarm: Boolean) {
        val app = context.applicationContext
        mainHandler.post {
            try {
                var engine: TextToSpeech? = null
                engine = TextToSpeech(app) { status ->
                    val e = engine
                    if (e == null || tts !== e) return@TextToSpeech // sudah dihentikan / diganti
                    if (status != TextToSpeech.SUCCESS) {
                        toast(app, "Mesin suara (Text-to-Speech) tidak tersedia di perangkat ini. Memakai nada alarm sebagai pengganti.")
                        fallbackBeep(app, asAlarm)
                        return@TextToSpeech
                    }
                    val result = e.setLanguage(Locale("id", "ID"))
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        e.language = Locale.getDefault()
                        toast(app, "Suara bahasa Indonesia belum terpasang di HP ini, memakai suara bawaan.")
                    }
                    e.setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(if (asAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    if (asAlarm) {
                        // Ulangi pengumuman setiap beberapa detik sampai alarm dikonfirmasi / dijeda
                        e.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                            override fun onStart(utteranceId: String?) {}
                            override fun onError(utteranceId: String?) {}
                            override fun onDone(utteranceId: String?) {
                                mainHandler.postDelayed({
                                    if (tts === e) e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VOICE_ALARM")
                                }, 2_500L)
                            }
                        })
                    }
                    e.setPitch(1.0f)
                    e.setSpeechRate(0.95f)
                    val code = e.speak(text, TextToSpeech.QUEUE_FLUSH, null, "VOICE_ALARM")
                    if (code != TextToSpeech.SUCCESS) {
                        toast(app, "Suara pengumuman gagal diputar. Memakai nada alarm sebagai pengganti.")
                        fallbackBeep(app, asAlarm)
                    }
                }
                tts = engine
            } catch (_: Exception) {
                fallbackBeep(app, asAlarm)
            }
        }
    }

    private fun fallbackBeep(context: Context, asAlarm: Boolean) {
        playRingtoneUri(context, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM), asAlarm)
    }

    private fun playRingtoneUri(context: Context, uri: Uri?, asAlarm: Boolean = currentAsAlarm) {
        mainHandler.post {
            try {
                val target = uri
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val ringtone = RingtoneManager.getRingtone(context.applicationContext, target)
                if (ringtone == null) {
                    toast(context, "Nada tidak ditemukan di perangkat ini.")
                    return@post
                }
                ringtone.audioAttributes = AudioAttributes.Builder()
                    .setUsage(if (asAlarm) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                activeRingtone?.stop()
                activeRingtone = ringtone
                if (asAlarm && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) ringtone.isLooping = true
                ringtone.play()
            } catch (_: Exception) {
                toast(context, "Nada gagal diputar.")
            }
        }
    }

    fun stopSound() {
        try {
            stopHandler.removeCallbacks(stopRunnable)
            activeRingtone?.stop()
            activeRingtone = null
            tts?.stop()
            tts?.shutdown()
            tts = null
        } catch (_: Exception) {
            // Abaikan error saat menghentikan suara
        }
    }
}
