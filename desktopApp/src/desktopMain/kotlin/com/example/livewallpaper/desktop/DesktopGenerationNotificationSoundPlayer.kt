package com.example.livewallpaper.desktop

import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.GenerationNotificationSound
import javazoom.jl.player.Player
import java.io.ByteArrayInputStream

/**
 * 播放桌面端生成完成提示音。
 *
 * 新的播放会打断上一段，避免设置里连续切换时叠在一起。
 */
internal object DesktopGenerationNotificationSoundPlayer {
    private val lock = Any()
    private var player: Player? = null

    /**
     * 播放 [sound] 对应的 mp3。找不到资源或解码失败时静默跳过。
     */
    fun play(sound: GenerationNotificationSound) {
        val resourceName = sound.resourceName()
        Thread {
            val bytes = javaClass.classLoader?.getResourceAsStream(resourceName)?.use { it.readBytes() }
                ?: return@Thread
            val nextPlayer = Player(ByteArrayInputStream(bytes))
            val previous = synchronized(lock) {
                val current = player
                player = nextPlayer
                current
            }
            previous?.close()
            runCatching { nextPlayer.play() }
            synchronized(lock) {
                if (player === nextPlayer) {
                    player = null
                }
            }
        }.apply {
            isDaemon = true
            name = "generation-notification-sound"
            start()
        }
    }
}

private fun GenerationNotificationSound.resourceName(): String = when (this) {
    GenerationNotificationSound.TONE_1 -> "sounds/gen_notify_07.mp3"
    GenerationNotificationSound.TONE_2 -> "sounds/gen_notify_017.mp3"
    GenerationNotificationSound.TONE_3 -> "sounds/gen_notify_033.mp3"
    GenerationNotificationSound.TONE_4 -> "sounds/gen_notify_051.mp3"
}
