package com.neon.book01.data

import android.content.ContentResolver
import android.net.Uri

/** Encrypts/decrypts backup bytes through the URI streams returned by Android's document picker. */
internal object LedgerBackupDocumentTransfer {
    fun writeEncryptedSnapshot(
        resolver: ContentResolver,
        destination: Uri,
        snapshot: LedgerBackupSnapshot,
        passphrase: CharArray,
    ) {
        val encrypted = LedgerBackupCodec.encrypt(snapshot, passphrase)
        try {
            val output = resolver.openOutputStream(destination, "w")
                ?: throw IllegalStateException("No se pudo abrir la ubicación elegida.")
            output.use { it.write(encrypted) }
        } finally {
            encrypted.fill(0)
        }
    }

    fun readEncryptedFile(resolver: ContentResolver, source: Uri): ByteArray {
        val input = resolver.openInputStream(source)
            ?: throw IllegalStateException("No se pudo abrir el archivo seleccionado.")
        return input.use { LedgerBackupCodec.readBounded(it) }
    }
}
