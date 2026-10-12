package com.rivo.studio.core.project

import android.content.Context
import com.rivo.studio.core.data.StudioDatabase
import com.rivo.studio.core.model.RecoveryCandidate
import com.rivo.studio.core.model.SnapshotRecord
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

class LocalRecoveryStore(context: Context, private val database: StudioDatabase) {
    private val draftDirectory = File(context.noBackupFilesDir, "recovery/unsaved").apply { mkdirs() }
    private val snapshotDirectory = File(context.noBackupFilesDir, "recovery/snapshots").apply { mkdirs() }

    fun saveDraft(projectId: String, relativePath: String, text: String) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_RECOVERY_BYTES) { "Recovery copy is larger than the editor limit." }
        val file = draftFile(projectId, relativePath)
        val json = JSONObject()
            .put("projectId", projectId)
            .put("relativePath", relativePath)
            .put("text", text)
            .put("savedAt", System.currentTimeMillis())
            .toString()
        writeAtomically(file, json.toByteArray(Charsets.UTF_8))
    }

    fun getDraft(projectId: String, relativePath: String): RecoveryCandidate? {
        val file = draftFile(projectId, relativePath)
        if (!file.isFile) return null
        return runCatching {
            val json = JSONObject(file.readText(Charsets.UTF_8))
            RecoveryCandidate(
                projectId = json.getString("projectId"),
                relativePath = json.getString("relativePath"),
                text = json.getString("text"),
                savedAt = json.getLong("savedAt"),
            )
        }.getOrNull()
    }

    fun discardDraft(projectId: String, relativePath: String) {
        val file = draftFile(projectId, relativePath)
        check(!file.exists() || file.delete()) { "Could not remove the local recovery draft." }
    }

    suspend fun createSnapshot(
        projectId: String,
        relativePath: String,
        text: String,
        charsetName: String,
        hasBom: Boolean,
        reason: String,
    ): String {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_RECOVERY_BYTES) { "Snapshot exceeds the local recovery size limit." }
        val id = UUID.randomUUID().toString()
        val storedName = "$id.json"
        val file = File(snapshotDirectory, storedName)
        val body = JSONObject()
            .put("text", text)
            .put("charsetName", charsetName)
            .put("hasBom", hasBom)
        writeAtomically(file, body.toString().toByteArray(Charsets.UTF_8))
        database.snapshots().insert(
            SnapshotRecord(
                id = id,
                projectId = projectId,
                relativePath = relativePath,
                storedName = storedName,
                charsetName = charsetName,
                hasBom = hasBom,
                createdAt = System.currentTimeMillis(),
                reason = reason,
            ),
        )
        return id
    }

    suspend fun restoreSnapshot(snapshotId: String): SnapshotContent? {
        val metadata = database.snapshots().get(snapshotId) ?: return null
        val file = File(snapshotDirectory, metadata.storedName).canonicalFile
        if (!file.path.startsWith(snapshotDirectory.canonicalPath + File.separator) || !file.isFile) return null
        val body = JSONObject(file.readText(Charsets.UTF_8))
        return SnapshotContent(
            record = metadata,
            text = body.getString("text"),
            charsetName = body.optString("charsetName", metadata.charsetName),
            hasBom = body.optBoolean("hasBom", metadata.hasBom),
        )
    }

    private fun draftFile(projectId: String, relativePath: String): File =
        File(draftDirectory, "${sha256("$projectId\n$relativePath")}.json")

    private fun writeAtomically(destination: File, bytes: ByteArray) {
        destination.parentFile?.mkdirs()
        val temporary = File(destination.parentFile, "${destination.name}.tmp")
        FileOutputStream(temporary).use { stream ->
            stream.write(bytes)
            stream.fd.sync()
        }
        if (!temporary.renameTo(destination)) {
            temporary.delete()
            throw IllegalStateException("Could not save local recovery data.")
        }
    }

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private companion object {
        const val MAX_RECOVERY_BYTES = 2L * 1024 * 1024
    }
}

data class SnapshotContent(
    val record: SnapshotRecord,
    val text: String,
    val charsetName: String,
    val hasBom: Boolean,
)
