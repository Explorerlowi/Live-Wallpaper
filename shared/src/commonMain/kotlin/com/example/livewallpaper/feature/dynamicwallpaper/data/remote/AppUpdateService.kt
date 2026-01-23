package com.example.livewallpaper.feature.dynamicwallpaper.data.remote

import com.example.livewallpaper.feature.dynamicwallpaper.data.remote.model.PgyerResponse
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.forms.submitForm
import io.ktor.http.Parameters

class AppUpdateService(
    private val client: HttpClient
) {
    suspend fun checkUpdate(
        apiKey: String,
        appKey: String,
        buildVersion: String? = null,
        buildBuildVersion: Int? = null
    ): PgyerResponse {
        return client.submitForm(
            url = "https://api.pgyer.com/apiv2/app/check",
            formParameters = Parameters.build {
                append("_api_key", apiKey)
                append("appKey", appKey)
                if (buildVersion != null) append("buildVersion", buildVersion)
                if (buildBuildVersion != null) append("buildBuildVersion", buildBuildVersion.toString())
            }
        ).body()
    }
}
