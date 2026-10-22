package org.fossify.musicplayer

import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.fossify.musicplayer.extensions.getTrackCoverArt
import org.fossify.musicplayer.models.Track
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class TrackArtworkTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private fun track(path: String) = Track(0, 0, "Fixture", "Penguin", path, 8, "Shared", "", "content://media/external/audio/albumart/508742118883406357", 1, null, null, "Music", 0, 0, 0, 0, 0, 0)
    @Test
    fun fileSpecificArtOverridesAlbumAndOriginalFallbackRemains() {
        check(Build.HARDWARE == "ranchu")
        val fixtures = File(context.cacheDir, "artwork-regression").apply { mkdirs() }
        try {
            for ((name, blue) in listOf("01-red.mp3" to false, "02-blue.mp3" to true, "red.opus" to false, "blue.opus" to true)) {
                val file = File(fixtures, name)
                androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { input ->
                    file.outputStream().use { input.copyTo(it) }
                }
                val original = MessageDigest.getInstance("SHA-256").digest(file.readBytes())
                val path = file.path
                val latch = CountDownLatch(1)
                var result: Any? = null
                context.getTrackCoverArt(track(path)) { result = it; latch.countDown() }
                assertTrue(latch.await(10, TimeUnit.SECONDS))
                val bitmap = result as android.graphics.Bitmap
                val pixel = bitmap.getPixel(10, 10)
                assertTrue(if (blue) android.graphics.Color.blue(pixel) > 200 else android.graphics.Color.red(pixel) > 200)
                assertArrayEquals(original, MessageDigest.getInstance("SHA-256").digest(file.readBytes()))
            }
            val missing = track("/nonexistent")
            val latch = CountDownLatch(1)
            var fallback: Any? = null
            context.getTrackCoverArt(missing) { fallback = it; latch.countDown() }
            assertTrue(latch.await(10, TimeUnit.SECONDS))
            assertEquals(missing.coverArt, fallback)
        } finally { fixtures.deleteRecursively() }
    }
    @Test
    fun malformedOpusMetadataFailsClosed() {
        for (bytes in listOf(byteArrayOf(), ByteArray(16), byteArrayOf(79, 112, 117, 115, 84, 97, 103, 115, -1, -1, -1, 127, 0, 0, 0, 0))) {
            assertNull(org.fossify.musicplayer.helpers.OpusPicture.fromTags(bytes))
        }
    }
}
