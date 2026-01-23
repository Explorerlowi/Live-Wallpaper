package com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PgyerResponse(
    val code: Int,
    val message: String,
    val data: PgyerData? = null
)

@Serializable
data class PgyerData(
    val buildHaveNewVersion: Boolean,
    val downloadURL: String? = null,
    val buildVersion: String? = null, // e.g. "1.2"
    val buildVersionNo: String? = null, // e.g. "3"
    val buildUpdateDescription: String? = null
)
