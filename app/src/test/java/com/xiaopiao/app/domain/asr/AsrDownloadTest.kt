package com.xiaopiao.app.domain.asr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The download's file contract: a model counts as installed only when every file has exactly the expected
 * byte count, a SHA-256 is computed the same way the catalog stores it, and deleting frees the folder.
 */
class AsrDownloadTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val model = AsrModel(
        id = "tiny-test-model",
        language = AsrLanguage.ZH,
        tier = AsrTier.SMALL,
        repo = "example/does-not-exist",
        streaming = true,
        files = listOf(
            AsrFile("encoder.onnx", 4L, "9f64a747e1b97f131fabb6b447296c9b6f0201e79fb3c5356e6c77e89b6a806a"),
            AsrFile("tokens.txt", 3L, "3e23e8160039594a33894f6564e1b1348bbd7a0088d42c4acb73eeaed59c009d"),
        ),
    )

    private fun store() = AsrModelStore(tmp.newFolder("asr"))

    @Test fun installedNeedsEveryFileAtTheExactSize() {
        val store = store()
        assertFalse(store.installed(model))

        store.dir(model).mkdirs()
        store.file(model, model.files[0]).writeBytes(ByteArray(4))
        assertFalse("one file is not enough", store.installed(model))

        store.file(model, model.files[1]).writeBytes(ByteArray(2))
        assertFalse("a short file is not installed", store.installed(model))

        store.file(model, model.files[1]).writeBytes(ByteArray(3))
        assertTrue(store.installed(model))
        assertEquals(7L, store.bytesOnDisk(model))
    }

    @Test fun sha256MatchesTheKnownDigest() {
        val file = tmp.newFile("payload.bin")
        file.writeBytes("abc".toByteArray())
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            AsrDownloader.sha256(file),
        )
    }

    @Test fun deletingRemovesOnlyThisModel() {
        val store = store()
        store.dir(model).mkdirs()
        store.file(model, model.files[0]).writeBytes(ByteArray(4))
        store.file(model, model.files[1]).writeBytes(ByteArray(3))
        val other = model.copy(id = "another-model")
        store.dir(other).mkdirs()
        store.file(other, other.files[0]).writeBytes(ByteArray(4))

        assertTrue(store.delete(model))
        assertFalse(store.installed(model))
        assertFalse(store.dir(model).exists())
        assertTrue("the other model must survive", store.dir(other).exists())
    }

    @Test fun installedIdsAndTotalSizeAddUp() {
        val store = store()
        val dir = store.dir(AsrCatalog.ZH_SMALL)
        dir.mkdirs()
        // Only the tokens file exists, and that is enough to mark the model by id only when all match.
        store.file(AsrCatalog.ZH_SMALL, AsrCatalog.ZH_SMALL.tokens).writeBytes(ByteArray(48_697))
        assertTrue(store.installedIds().isEmpty())
        assertTrue(store.totalBytes() > 0L)
    }
}
