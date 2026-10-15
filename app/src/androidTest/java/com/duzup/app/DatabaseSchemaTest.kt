package com.duzup.app

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.duzup.app.core.database.BillEntity
import com.duzup.app.core.database.OccurrenceEntity
import com.duzup.app.core.database.DuzupDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Runs on a device/emulator (`./gradlew connectedDebugAndroidTest`).
 *
 * The exported schema JSON under app/schemas is bundled as test assets, so [MigrationTestHelper] validates that
 * the database Room creates matches the schema committed for each version. When the version is bumped, add a
 * `Migration` to [DuzupDatabase.MIGRATIONS] and a `runMigrationsAndValidate(name, newVersion, true, MIGRATION)`
 * test here that seeds rows in the old version first.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseSchemaTest {
    private val dbName = "migration-test"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), DuzupDatabase::class.java)

    private lateinit var context: Context
    private var inMemory: DuzupDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
    }

    @After
    fun tearDown() {
        inMemory?.close()
    }

    @Test
    fun version1_schemaMatchesTheExportedJson() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, 1, true).close()
    }

    @Test
    fun uniqueIndex_preventsDuplicateOccurrencesForABillAndDate() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DuzupDatabase::class.java).build().also { inMemory = it }
        db.billDao().upsert(
            BillEntity(
                id = "b", title = "Rent", categoryId = "rent", notes = null, amountMinorUnits = 1000, currencyCode = "USD",
                startDueDate = "2025-01-31", dueLocalTime = "09:00", timeZoneId = "UTC", recurrenceRule = "MONTHLY",
                customInterval = null, customUnit = null, endDate = null, reminderOffsets = "10080,4320,1440,0",
                isArchived = false, createdAt = 0, updatedAt = 0, kind = "BILL", scheduled = true,
                endLocalTime = null, allDay = false, location = null, priority = 1, isPinned = false,
                includeAge = true, routineTimes = "", weekdaysMask = 0,
            ),
        )
        fun occurrence(id: String) = OccurrenceEntity(id, "b", "2025-01-31", "09:00", "PENDING", null, null, null, 0)

        val first = db.occurrenceDao().insertIgnore(listOf(occurrence("o1")))
        val duplicate = db.occurrenceDao().insertIgnore(listOf(occurrence("o2")))

        assertEquals(1, first.count { it != -1L })
        assertTrue("same bill + date is ignored", duplicate.all { it == -1L })
        assertEquals(1, db.occurrenceDao().getForBill("b").size)
    }

    @Test
    fun deletingABill_cascadesToOccurrences() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, DuzupDatabase::class.java).build().also { inMemory = it }
        db.billDao().upsert(
            BillEntity(
                "b", "Rent", "rent", null, null, "USD", "2025-01-31", "09:00", "UTC", "NONE",
                null, null, null, "", false, 0, 0, "BILL", true, null, false, null, 1, false, true, "", 0,
            ),
        )
        db.occurrenceDao().insertIgnore(listOf(OccurrenceEntity("o", "b", "2025-01-31", "09:00", "PENDING", null, null, null, 0)))

        db.billDao().delete("b")

        assertTrue(db.occurrenceDao().getAll().isEmpty())
    }
}
