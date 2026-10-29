package com.example.livewallpaper.paint.service

import android.content.Context
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import com.example.livewallpaper.R
import com.example.livewallpaper.feature.dynamicwallpaper.domain.model.GenerationNotificationSound

/**
 * 把设置里的提示音映射到打包进应用的音频资源。
 */
internal fun GenerationNotificationSound.rawResId(): Int = when (this) {
    GenerationNotificationSound.TONE_1 -> R.raw.gen_notify_07
    GenerationNotificationSound.TONE_2 -> R.raw.gen_notify_017
    GenerationNotificationSound.TONE_3 -> R.raw.gen_notify_033
    GenerationNotificationSound.TONE_4 -> R.raw.gen_notify_051
}

/**
 * 播放生成完成提示音。设置试听和生成完成共用这一条路径。
 *
 * 生成结束的回调在后台线程，MediaPlayer 需要主线程的 Looper 才能稳定出声。
 */
internal object GenerationNotificationSoundPreview {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null

    /**
     * 播放 [sound]，并停掉上一次还没结束的播放。
     */
    fun play(context: Context, sound: GenerationNotificationSound) {
        val appContext = context.applicationContext
        if (Looper.myLooper() == Looper.getMainLooper()) {
            start(appContext, sound)
        } else {
            mainHandler.post { start(appContext, sound) }
        }
    }

    private fun start(context: Context, sound: GenerationNotificationSound) {
        player?.release()
        val created = MediaPlayer.create(context, sound.rawResId()) ?: return
        player = created
        created.setOnCompletionListener { completed ->
            completed.release()
            if (player === completed) {
                player = null
            }
        }
        created.start()
    }
}
