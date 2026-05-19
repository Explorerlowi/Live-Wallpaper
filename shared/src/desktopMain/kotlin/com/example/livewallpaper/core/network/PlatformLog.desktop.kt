package com.example.livewallpaper.core.network

actual fun platformLog(tag: String, message: String) {
    println("[$tag] $message")
}
