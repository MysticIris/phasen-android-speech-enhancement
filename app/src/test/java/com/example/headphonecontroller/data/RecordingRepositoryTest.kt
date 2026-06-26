package com.example.headphonecontroller.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class RecordingRepositoryTest {

    @Test
    fun listRecordings_returnsM4aFiles_sortedByTimestampDesc() {
        val dir = Files.createTempDirectory("recordings-test").toFile()
        val oldFile = dir.resolve("recording_old.m4a").apply {
            writeText("old")
            setLastModified(1_000L)
        }
        val newFile = dir.resolve("recording_new.m4a").apply {
            writeText("new")
            setLastModified(2_000L)
        }
        dir.resolve("ignore.txt").writeText("x")

        val repository = RecordingRepository(dir)

        val items = repository.listRecordings()

        assertEquals(2, items.size)
        assertEquals(newFile.name, items[0].fileName)
        assertEquals(oldFile.name, items[1].fileName)
        assertEquals(newFile.length(), items[0].sizeBytes)
        assertEquals(2_000L, items[0].timestamp)
    }

    @Test
    fun delete_removesRecordingFile() {
        val dir = Files.createTempDirectory("recordings-delete-test").toFile()
        val file = dir.resolve("recording.m4a").apply {
            writeText("data")
            setLastModified(3_000L)
        }
        val repository = RecordingRepository(dir)
        val item = repository.listRecordings().first()

        val deleted = repository.delete(item)

        assertTrue(deleted)
        assertTrue(!file.exists())
    }
}
