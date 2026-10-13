package com.neon.book01.data

import androidx.activity.result.contract.ActivityResultContracts
import java.time.LocalDate

/** The picker contracts used by the backup UI, exposed for focused intent tests. */
internal object LedgerBackupDocumentPicker {
    const val MIME_TYPE = "application/vnd.book01.encrypted-backup"

    fun createContract() = ActivityResultContracts.CreateDocument(MIME_TYPE)

    fun openContract() = ActivityResultContracts.OpenDocument()

    fun openMimeTypes(): Array<String> = arrayOf(
        MIME_TYPE,
        "application/octet-stream",
        "application/*",
    )

    fun defaultFileName(date: LocalDate): String = "Book-01-backup-$date.b01"
}
