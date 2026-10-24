package com.musclejournal.app.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdatePolicyTest {
    private val tag = "v0.3.0"
    private val apkName = "MuscleJournal-0.3.0.apk"
    private val apk = ReleaseAsset(
        apkName,
        "https://github.com/cgl-sd/MUSLE/releases/download/$tag/$apkName",
        4_000,
    )
    private val checksumAsset = ReleaseAsset("$apkName.sha256", "${apk.url}.sha256", 90)
    private val metadata = ReleaseMetadata(tag, false, false, listOf(apk, checksumAsset))
    private val expected = UpdateVersion(0, 3, 0)
    private val digest = "0123456789abcdef".repeat(4)

    @Test
    fun versionsUseAllThreeNumericSegments() {
        assertEquals(expected, UpdateVersion.parse("0.3.0"))
        assertEquals(UpdateVersion(1, 0, 0), UpdateVersion.parse("1.0.0"))
        assertTrue(UpdateVersion.parse("1.10.0")!! > UpdateVersion.parse("1.9.99")!!)
        assertTrue(UpdateVersion.parse("0.3.10")!! > expected)
        for (bad in listOf(
            "",
            "v0.3.0",
            "0.3",
            "0.03.0",
            "0.3.01",
            "0.3.0-beta",
            "0.3.0+build",
            " 0.3.0",
            "2147483648.0.0",
            "0.-1.0",
        )) {
            assertNull(bad, UpdateVersion.parse(bad))
        }
    }

    @Test
    fun onlyCompleteFormalReleasesWithExpectedAssetsAreAccepted() {
        assertEquals(expected, UpdatePolicy.release(metadata).version)
        for (bad in listOf(
            metadata.copy(draft = true),
            metadata.copy(prerelease = true),
            metadata.copy(tag = "v0.3.0-rc.1"),
            metadata.copy(assets = listOf(apk)),
            metadata.copy(assets = listOf(checksumAsset)),
            metadata.copy(assets = listOf(apk, apk, checksumAsset)),
            metadata.copy(assets = listOf(apk.copy(name = "other.apk"), checksumAsset)),
            metadata.copy(assets = listOf(apk.copy(size = 0), checksumAsset)),
            metadata.copy(assets = listOf(apk.copy(size = MAX_APK_BYTES + 1), checksumAsset)),
            metadata.copy(assets = listOf(apk, checksumAsset.copy(size = 4097))),
        )) {
            rejects { UpdatePolicy.release(bad) }
        }
    }

    @Test
    fun assetUrlsMustStayInsideThePinnedRepository() {
        assertTrue(UpdatePolicy.validAssetUrl(apk.url, tag, apkName))
        for (bad in listOf(
            apk.url.replace("https:", "http:"),
            apk.url.replace("github.com", "github.com.evil.test"),
            apk.url.replace("cgl-sd", "someone-else"),
            apk.url.replace("MUSLE/releases", "Other/releases"),
            apk.url.replace("v0.3.0", "v0.3.1"),
            apk.url.replace("github.com/", "user@github.com/"),
            apk.url.replace("github.com/", "github.com:443/"),
            "${apk.url}?download=1",
            "${apk.url}#fragment",
            apk.url.replace("MuscleJournal-0.3.0.apk", "MuscleJournal%2D0.3.0.apk"),
        )) {
            assertFalse(bad, UpdatePolicy.validAssetUrl(bad, tag, apkName))
            rejects {
                UpdatePolicy.release(metadata.copy(assets = listOf(apk.copy(url = bad), checksumAsset)))
            }
        }
        rejects {
            UpdatePolicy.release(
                metadata.copy(assets = listOf(apk, checksumAsset.copy(url = "https://evil.test/digest"))),
            )
        }
    }

    @Test
    fun checksumMustBeACompleteDigestForTheExactApk() {
        assertEquals(digest, UpdatePolicy.checksum("$digest  $apkName\n", apkName))
        assertEquals(digest, UpdatePolicy.checksum("${digest.uppercase()} *$apkName\n", apkName))
        assertEquals(digest, UpdatePolicy.checksum(digest, apkName))
        for (bad in listOf(
            "",
            digest.dropLast(1),
            "g".repeat(64),
            "$digest  other.apk",
            "$digest  $apkName\n$digest  $apkName",
        )) {
            rejects { UpdatePolicy.checksum(bad, apkName) }
        }
        assertTrue(UpdatePolicy.matchesDigest(digest, digest))
        assertFalse(UpdatePolicy.matchesDigest(digest, "f".repeat(64)))
        assertFalse(UpdatePolicy.matchesDigest("", ""))
        assertFalse(UpdatePolicy.matchesDigest(digest, digest.dropLast(1)))
    }

    @Test
    fun archiveMustMatchPackageReleaseCodeAndInstalledSigners() {
        fun validate(
            packageName: String = "com.musclejournal.app",
            version: String? = "0.3.0",
            code: Long = 4,
            installedSigners: Set<String> = setOf("installed-cert"),
            archiveSigners: Set<String> = setOf("installed-cert"),
        ) {
            UpdatePolicy.validateArchive(
                "com.musclejournal.app",
                3,
                installedSigners,
                packageName,
                version,
                code,
                archiveSigners,
                expected,
            )
        }
        validate()
        rejects { validate(packageName = "com.evil.musclejournal") }
        rejects { validate(version = "0.3.1") }
        rejects { validate(version = null) }
        rejects { validate(code = 3) }
        rejects { validate(code = 2) }
        rejects { validate(archiveSigners = setOf("different-cert")) }
        rejects { validate(archiveSigners = setOf("installed-cert", "extra-cert")) }
        rejects { validate(installedSigners = emptySet(), archiveSigners = emptySet()) }
    }

    @Test
    fun onlyVerifiedReadyStateAllowsInstallation() {
        for (phase in UpdatePhase.entries) {
            val state = UpdateState(phase, "0.2.0", UpdatePolicy.release(metadata))
            assertEquals(phase.name, phase == UpdatePhase.READY, state.canInstall)
            if (phase in listOf(
                UpdatePhase.RECOVERING,
                UpdatePhase.CHECKING,
                UpdatePhase.PREPARING,
                UpdatePhase.DOWNLOADING,
                UpdatePhase.VERIFYING,
                UpdatePhase.PERMISSION,
                UpdatePhase.INSTALLER,
                UpdatePhase.AVAILABLE,
                UpdatePhase.READY,
            )) {
                assertFalse(phase.name, state.canCheck)
            }
        }
        assertTrue(UpdateState(UpdatePhase.ERROR).canCheck)
        assertFalse(UpdateState(UpdatePhase.PERMISSION).canCancelDownload)
        assertFalse(UpdateState(UpdatePhase.INSTALLER).canCancelDownload)
        assertFalse(UpdateState(UpdatePhase.READY).canCancelDownload)
    }

    private fun rejects(block: () -> Unit) {
        try {
            block()
        } catch (_: IllegalArgumentException) {
            return
        } catch (_: IllegalStateException) {
            return
        }
        throw AssertionError("Expected malformed update to be rejected")
    }
}
