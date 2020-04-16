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

import androidx.room.*
import com.maltaisn.notes.model.entity.Note
import com.maltaisn.notes.model.entity.NoteStatus
import kotlinx.coroutines.flow.Flow
import java.util.*


@Dao
interface NotesDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: Note): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(notes: List<Note>)

    @Update
    suspend fun update(note: Note)

    @Update
    suspend fun updateAll(notes: List<Note>)

    @Delete
    suspend fun delete(note: Note)

    @Delete
    suspend fun deleteAll(notes: List<Note>)

    /**
     * Delete notes with a UUID contained in [uuids]. Used for syncing
     * to delete local notes removed remotely.
     */
    @Query("DELETE FROM notes WHERE uuid IN (:uuids)")
    suspend fun deleteByUuid(uuids: List<String>)

    /**
     * Get all notes in database.
     * Used for exporting data.
     */
    @Query("SELECT * FROM notes")
    suspend fun getAll(): List<Note>

    /**
     * Get a note by its ID. Returns `null` if note doesn't exist.
     */
    @Query("SELECT * FROM notes WHERE id == :id")
    suspend fun getById(id: Long): Note?

    /**
     * Get a note ID from its [uuid]. Used for syncing to know which notes were added and which
     * were updated, since local database uses ID as a key but server uses UUID.
     */
    @Query("SELECT id FROM notes WHERE uuid == :uuid")
    suspend fun getIdByUuid(uuid: String): Long?

    /**
     * Get all notes with a [status], sorted by last modified date.
     * This is used to display notes for each status.
     */
    @Query("SELECT * FROM notes WHERE status == :status ORDER BY modified_date DESC")
    fun getByStatus(status: NoteStatus): Flow<List<Note>>

    /**
     * Get notes with a [status] and older than a [date][minDate].
     * Used for deleting old notes in trash after a delay.
     */
    @Query("SELECT * FROM notes WHERE status == :status AND modified_date < :minDate")
    suspend fun getByStatusAndDate(status: NoteStatus, minDate: Date): List<Note>

    /**
     * Get all notes with a changed flag set to `true`. Used for syncing.
     */
    @Query("SELECT * FROM notes WHERE changed == 1")
    suspend fun getChanged(): List<Note>

    /**
     * Reset the changed flag of all notes to [changed]. Used after syncing.
     * This is also used when user signs out of its account to make sure sync will work
     * if a different account is used afterwards.
     */
    @Query("UPDATE notes SET changed = :changed")
    suspend fun setChangedFlag(changed: Boolean)

    /**
     * Search active and archived notes for a [query] using full-text search,
     * sorted by status first then by last modified date.
     */
    @Query("""SELECT * FROM notes JOIN notes_fts ON notes_fts.rowid == notes.id
        WHERE notes_fts MATCH :query AND status != 2
        ORDER BY status ASC, modified_date DESC""")
    fun search(query: String): Flow<List<Note>>

}
