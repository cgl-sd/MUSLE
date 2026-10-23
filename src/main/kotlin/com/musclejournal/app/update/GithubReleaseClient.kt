package com.musclejournal.app.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

internal interface ReleaseSource {
    suspend fun latest(): UpdateRelease?
    suspend fun checksum(release: UpdateRelease): String
}

internal class GithubReleaseClient : ReleaseSource {
    override suspend fun latest(): UpdateRelease? {
        val body = request(LATEST_RELEASE_URL, 1024 * 1024L, noReleaseOn404 = true) ?: return null
        val json = JSONObject(body)
        val assets = json.getJSONArray("assets")
        val metadata = ReleaseMetadata(
            tag = json.requiredString("tag_name"),
            draft = json.get("draft") as? Boolean ?: error("发布 draft 字段格式无效"),
            prerelease = json.get("prerelease") as? Boolean ?: error("发布 prerelease 字段格式无效"),
            assets = List(assets.length()) { index ->
                val asset = assets.getJSONObject(index)
                val size = asset.get("size")
                require(size is Long || size is Int) { "发布文件大小字段格式无效" }
                ReleaseAsset(
                    asset.requiredString("name"),
                    asset.requiredString("browser_download_url"),
                    (size as Number).toLong(),
                )
            },
        )
        return UpdatePolicy.release(metadata)
    }

    override suspend fun checksum(release: UpdateRelease): String {
        require(UpdatePolicy.validAssetUrl(release.checksum.url, release.tag, release.checksum.name)) {
            "校验文件地址无效"
        }
        val text = request(release.checksum.url, 4096, noReleaseOn404 = false)
            ?: error("校验文件不存在")
        return UpdatePolicy.checksum(text, release.apk.name)
    }

    private fun JSONObject.requiredString(key: String): String =
        get(key) as? String ?: error("发布 $key 字段格式无效")

    private suspend fun request(url: String, limit: Long, noReleaseOn404: Boolean): String? =
        withContext(Dispatchers.IO) {
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                useCaches = false
                requestMethod = "GET"
                setRequestProperty(
                    "Accept",
                    if (noReleaseOn404) "application/vnd.github+json" else "application/octet-stream",
                )
                setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
                setRequestProperty("User-Agent", "YangJiBao-Android-Updater")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Accept-Encoding", "identity")
            }
            try {
                val code = connection.responseCode
                if (code == HttpURLConnection.HTTP_NOT_FOUND && noReleaseOn404) return@withContext null
                if (code !in 200..299) throw IOException("GitHub 请求失败（HTTP $code）")
                require(connection.url.protocol == "https") { "GitHub 请求不是 HTTPS" }
                val declaredLength = connection.contentLengthLong
                if (declaredLength > limit) throw IOException("GitHub 返回内容过大")
                val output = ByteArrayOutputStream(minOf(declaredLength.takeIf { it > 0 } ?: 8192L, 8192L).toInt())
                connection.inputStream.buffered().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        if (output.size().toLong() + count > limit) throw IOException("GitHub 返回内容过大")
                        output.write(buffer, 0, count)
                    }
                }
                output.toString(Charsets.UTF_8.name())
            } finally {
                connection.disconnect()
            }
        }
}
