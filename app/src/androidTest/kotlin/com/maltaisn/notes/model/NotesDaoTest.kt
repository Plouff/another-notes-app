/*
 * Copyright 2020 Nicolas Maltais
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.maltaisn.notes.model

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.maltaisn.notes.atestNote
import com.maltaisn.notes.model.converter.DateTimeConverter
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull


@RunWith(AndroidJUnit4::class)
class NotesDaoTest {

    private lateinit var database: NotesDatabase
    private lateinit var notesDao: NotesDao

    @Before
    fun createDatabase() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, NotesDatabase::class.java).build()
        notesDao = database.notesDao()
    }

    @After
    fun closeDatabase() {
        database.close()
    }

    @Test
    fun readWriteTests() = runBlocking {
        val note = atestNote(id = 1, uuid = "1", title = "note")

        val id = notesDao.insert(note)
        assertEquals(note, notesDao.getById(id))

        val updatedNote = atestNote(id = 1, uuid = "1", title = "updated note")
        notesDao.update(updatedNote)
        assertEquals(updatedNote, notesDao.getById(id))

        notesDao.delete(updatedNote)
        assertNull(notesDao.getById(id))
        assertNull(notesDao.getIdByUuid("1"))
    }

    @Test
    fun getIdByUuidTest() = runBlocking {
        val note = atestNote(id = 1, uuid = "1")

        notesDao.insert(note)
        assertEquals(1, notesDao.getIdByUuid("1"))
        assertNull(notesDao.getIdByUuid("0"))
    }

    @Test
    fun getNotesByStatusTest() = runBlocking {
        val time = DateTimeConverter.toDate("2020-01-01T00:00:00.000Z")
        val activeNotes = mutableListOf<Note>()
        var id = 1L
        repeat(5) {
            for (status in NoteStatus.values()) {
                val note = atestNote(id = id, status = status, added = time, modified = time)
                notesDao.insert(note)
                if (status == NoteStatus.ACTIVE) {
                    activeNotes += note
                }
                id++
            }
        }

        val noteFlow = notesDao.getByStatus(NoteStatus.ACTIVE)
        assertEquals(activeNotes, noteFlow.first())

        // Delete any note to see if flow is updated.
        notesDao.delete(activeNotes.removeAt(activeNotes.indices.random()))
        assertEquals(activeNotes, noteFlow.first())
    }

    @Test
    fun getByStatusAndDateTest() = runBlocking {
        val dates = listOf(
                "2019-01-01T00:00:00.000Z",
                "2019-05-01T00:00:00.000Z",
                "2019-12-31T12:34:56.789Z",
                "2019-12-31T23:59:59.999Z",
                "2020-01-01T00:00:00.000Z",
                "2020-01-02T00:00:00.000Z")
        val notes = dates.mapIndexed { i, date ->
            atestNote(id = i + 1L, modified = DateTimeConverter.toDate(date),
                    status = NoteStatus.TRASHED)
        }
        notesDao.insertAll(notes)

        val queryNotes = notesDao.getByStatusAndDate(NoteStatus.TRASHED,
                DateTimeConverter.toDate("2020-01-01T00:00:00.000Z"))
        assertEquals(notes.subList(0, 4).toSet(), queryNotes.toSet())
    }

    @Test
    fun getChangedTest() = runBlocking {
        val note = atestNote(id = 1, changed = true)
        notesDao.insertAll(listOf(note, atestNote(id = 2, changed = false)))

        assertEquals(listOf(note), notesDao.getChanged())
    }

    @Test
    fun deleteByUuidTest() = runBlocking {
        notesDao.insertAll(listOf(
                atestNote(id = 1, uuid = "0"),
                atestNote(id = 2, uuid = "1"),
                atestNote(id = 3, uuid = "2")))

        notesDao.deleteByUuid(listOf("0", "2"))

        assertNull(notesDao.getById(1))
        assertNotNull(notesDao.getById(2))
        assertNull(notesDao.getById(3))
    }

    @Test
    fun resetChangedFlagTest() = runBlocking {
        notesDao.insertAll(listOf(
                atestNote(id = 1, changed = true),
                atestNote(id = 2, changed = false)))

        notesDao.setChangedFlag(false)
        assertFalse(notesDao.getById(1)!!.changed)
        assertFalse(notesDao.getById(2)!!.changed)
    }

    @Test
    fun searchNotesTest() = runBlocking {
        val note0 = atestNote(id = 1, title = "note", content = "content")
        notesDao.insert(note0)
        notesDao.insert(atestNote(id = 2, title = "title", content = "foo"))
        notesDao.insert(atestNote(id = 3, title = "my note", content = "bar"))

        val noteFlow = notesDao.search("content")
        assertEquals(listOf(note0), noteFlow.first())

        val note1 = atestNote(id = 4, title = "note copy", content = "content copy")
        val note2 = atestNote(id = 5, title = "archived", content = "archived content", status = NoteStatus.ARCHIVED)
        notesDao.insert(note1)
        notesDao.insert(note2)
        assertEquals(listOf(note1, note0, note2), noteFlow.first())
    }

}
