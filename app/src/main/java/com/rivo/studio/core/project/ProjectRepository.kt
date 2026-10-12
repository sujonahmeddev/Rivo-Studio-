package com.rivo.studio.core.project

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import com.rivo.studio.core.data.StudioDatabase
import com.rivo.studio.core.model.DecodedText
import com.rivo.studio.core.model.ProjectNode
import com.rivo.studio.core.model.ProjectRecord
import com.rivo.studio.core.model.RecentFileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/** Project engine. All in-app edits are confined to app-private project roots. */
class ProjectRepository(context: Context, private val database: StudioDatabase) {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val projectDirectory = File(appContext.filesDir, "projects").apply { mkdirs() }

    fun observeProjects(): Flow<List<ProjectRecord>> = database.projects().observeProjects()
    fun observeRecentFiles(projectId: String): Flow<List<RecentFileRecord>> =
        database.recentFiles().observeRecentFiles(projectId)

    suspend fun project(id: String): ProjectRecord? = withContext(Dispatchers.IO) {
        database.projects().getProject(id)
    }

    suspend fun createProject(rawName: String): ProjectRecord = withContext(Dispatchers.IO) {
        val name = PathPolicy.validateFileName(rawName)
        val id = UUID.randomUUID().toString()
        val directory = File(projectDirectory, id)
        check(directory.mkdirs()) { "Could not create this project on device storage." }
        val now = System.currentTimeMillis()
        ProjectRecord(id = id, name = name, createdAt = now, updatedAt = now).also {
            database.projects().upsert(it)
        }
    }

    suspend fun renameProject(projectId: String, rawName: String) = withContext(Dispatchers.IO) {
        val name = PathPolicy.validateFileName(rawName)
        requireProjectRoot(projectId)
        database.projects().rename(projectId, name, System.currentTimeMillis())
    }

    suspend fun toggleFavorite(projectId: String, favorite: Boolean) = withContext(Dispatchers.IO) {
        requireProjectRoot(projectId)
        database.projects().setFavorite(projectId, favorite, System.currentTimeMillis())
    }

    suspend fun deleteProject(projectId: String) = withContext(Dispatchers.IO) {
        val root = requireProjectRoot(projectId)
        check(root.deleteRecursively()) { "Could not remove the project from device storage." }
        database.projects().delete(projectId)
        database.recentFiles().deleteForProject(projectId)
    }

    suspend fun listTree(projectId: String): List<ProjectNode> = withContext(Dispatchers.IO) {
        val root = requireProjectRoot(projectId)
        val result = mutableListOf<ProjectNode>()
        fun walk(directory: File, relative: String, depth: Int) {
            val children = directory.listFiles()?.sortedWith(compareBy<File>({ !it.isDirectory }, { it.name.lowercase() }))
                ?: return
            for (child in children) {
                if (result.size >= MAX_PROJECT_ENTRIES) throw IllegalStateException("This project has more than $MAX_PROJECT_ENTRIES entries; narrow the project before browsing it.")
                val canonical = child.canonicalFile
                if (!canonical.path.startsWith(root.canonicalPath + File.separator)) continue
                val path = if (relative.isEmpty()) child.name else "$relative/${child.name}"
                result += ProjectNode(path, child.name, child.isDirectory, if (child.isFile) child.length() else 0L, depth)
                if (child.isDirectory) walk(child, path, depth + 1)
            }
        }
        walk(root, "", 0)
        result
    }

    suspend fun createFile(projectId: String, relativePath: String) = withContext(Dispatchers.IO) {
        val target = resolve(projectId, relativePath)
        check(!target.exists()) { "A file or folder with that name already exists." }
        check(target.parentFile?.isDirectory == true) { "Create the parent folder before creating this file." }
        check(target.createNewFile()) { "Could not create the file." }
        touchProject(projectId)
    }

    suspend fun createDirectory(projectId: String, relativePath: String) = withContext(Dispatchers.IO) {
        val target = resolve(projectId, relativePath)
        check(!target.exists()) { "A file or folder with that name already exists." }
        check(target.parentFile?.isDirectory == true) { "Create the parent folder before creating this folder." }
        check(target.mkdir()) { "Could not create the folder." }
        touchProject(projectId)
    }

    suspend fun readText(projectId: String, relativePath: String): DecodedText = withContext(Dispatchers.IO) {
        val file = resolve(projectId, relativePath)
        check(file.isFile) { "The selected file is missing or is not a regular file." }
        check(file.length() <= MAX_EDITABLE_FILE_BYTES) {
            "This file is ${file.length() / 1024} KB. The editor currently opens files up to ${MAX_EDITABLE_FILE_BYTES / 1024} KB."
        }
        val decoded = TextEncoding.decode(file.readBytes())
        DecodedText(decoded.first, decoded.second.first.charset.name(), decoded.second.second)
    }

    suspend fun writeText(
        projectId: String,
        relativePath: String,
        text: String,
        charsetName: String,
        hasBom: Boolean,
    ) = withContext(Dispatchers.IO) {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_EDITABLE_FILE_BYTES) {
            "The edited file exceeds the ${MAX_EDITABLE_FILE_BYTES / 1024} KB editor limit."
        }
        val target = resolve(projectId, relativePath)
        check(target.isFile) { "The file no longer exists in this project." }
        val encoding = TextEncoding.fromName(charsetName)
        val bytes = TextEncoding.encode(text, encoding, hasBom)
        require(bytes.size <= MAX_EDITABLE_FILE_BYTES) { "The encoded file exceeds the 2 MB editor limit." }
        val temp = File(target.parentFile, "${target.name}.rivo-tmp")
        temp.outputStream().use { stream ->
            stream.write(bytes)
            stream.flush()
        }
        if (!temp.renameTo(target)) {
            temp.delete()
            throw IllegalStateException("Could not save the file. Check available device storage and permissions.")
        }
        database.recentFiles().upsert(RecentFileRecord(projectId, relativePath, System.currentTimeMillis()))
        touchProject(projectId)
    }

    suspend fun recordOpened(projectId: String, relativePath: String) = withContext(Dispatchers.IO) {
        resolve(projectId, relativePath)
        database.recentFiles().upsert(RecentFileRecord(projectId, relativePath, System.currentTimeMillis()))
    }

    suspend fun renamePath(projectId: String, sourcePath: String, newName: String) = withContext(Dispatchers.IO) {
        val source = resolve(projectId, sourcePath)
        check(source.exists()) { "The selected item no longer exists." }
        val projectRoot = requireProjectRoot(projectId)
        check(source != projectRoot) { "The project root cannot be renamed as a file operation." }
        val name = PathPolicy.validateFileName(newName)
        if (name == source.name) return@withContext source.relativeTo(projectRoot).invariantSeparatorsPath
        val sourceRelative = source.relativeTo(projectRoot).invariantSeparatorsPath
        val wasDirectory = source.isDirectory
        val target = File(source.parentFile, name)
        check(!target.exists()) { "An item with that name already exists." }
        check(source.renameTo(target)) { "Could not rename this item." }
        val targetRelative = target.relativeTo(requireProjectRoot(projectId)).invariantSeparatorsPath
        touchProject(projectId)
        retargetRecentFiles(projectId, sourceRelative, targetRelative, wasDirectory)
        targetRelative
    }

    suspend fun copyPath(projectId: String, sourcePath: String, targetPath: String) = withContext(Dispatchers.IO) {
        val source = resolve(projectId, sourcePath)
        val target = resolve(projectId, targetPath)
        check(source.exists()) { "The selected item no longer exists." }
        val projectRoot = requireProjectRoot(projectId)
        check(source != projectRoot) { "The project root cannot be copied as a file operation." }
        val sourceRelative = source.relativeTo(projectRoot).invariantSeparatorsPath
        val targetRelative = target.relativeTo(projectRoot).invariantSeparatorsPath
        check(targetRelative != sourceRelative && !targetRelative.startsWith("$sourceRelative/")) {
            "A folder cannot be copied into itself. Choose a destination outside the source folder."
        }
        check(!target.exists()) { "The destination already exists." }
        check(target.parentFile?.isDirectory == true) { "The destination folder does not exist." }
        source.copyRecursively(target, overwrite = false)
        touchProject(projectId)
    }

    suspend fun movePath(projectId: String, sourcePath: String, targetPath: String) = withContext(Dispatchers.IO) {
        val source = resolve(projectId, sourcePath)
        val target = resolve(projectId, targetPath)
        check(source.exists()) { "The selected item no longer exists." }
        val projectRoot = requireProjectRoot(projectId)
        check(source != projectRoot) { "The project root cannot be moved as a file operation." }
        val sourceRelative = source.relativeTo(projectRoot).invariantSeparatorsPath
        val targetRelative = target.relativeTo(projectRoot).invariantSeparatorsPath
        if (sourceRelative == targetRelative) return@withContext
        check(!targetRelative.startsWith("$sourceRelative/")) {
            "A folder cannot be moved inside itself. Choose a destination outside the source folder."
        }
        val wasDirectory = source.isDirectory
        check(!target.exists()) { "The destination already exists." }
        check(target.parentFile?.isDirectory == true) { "The destination folder does not exist." }
        check(source.renameTo(target)) { "Could not move this item." }
        touchProject(projectId)
        retargetRecentFiles(projectId, sourceRelative, targetRelative, wasDirectory)
    }

    suspend fun deletePath(projectId: String, relativePath: String) = withContext(Dispatchers.IO) {
        val target = resolve(projectId, relativePath)
        check(target != requireProjectRoot(projectId)) { "The project root cannot be removed as a file operation." }
        check(target.exists()) { "The selected item no longer exists." }
        val wasDirectory = target.isDirectory
        check(target.deleteRecursively()) { "Could not delete this item." }
        touchProject(projectId)
        deleteRecentFiles(projectId, relativePath, wasDirectory)
    }

    suspend fun searchFiles(projectId: String, query: String, limit: Int = 100): List<Pair<String, Int>> = withContext(Dispatchers.IO) {
        require(query.isNotBlank()) { "Enter text to search for." }
        val root = requireProjectRoot(projectId)
        val results = mutableListOf<Pair<String, Int>>()
        listTree(projectId).filter { !it.isDirectory && it.sizeBytes <= MAX_EDITABLE_FILE_BYTES }.forEach { node ->
            if (results.size >= limit) return@forEach
            val file = resolve(projectId, node.relativePath)
            val bytes = file.readBytes()
            if (bytes.any { it == 0.toByte() }) return@forEach
            val lines = runCatching { bytes.toString(Charsets.UTF_8).lineSequence().toList() }.getOrDefault(emptyList())
            lines.forEachIndexed { index, line ->
                if (results.size < limit && line.contains(query, ignoreCase = true)) results += node.relativePath to (index + 1)
            }
        }
        check(root.exists()) { "The project folder is unavailable." }
        results
    }

    suspend fun importDocumentAsProject(uri: Uri, requestedName: String? = null): ProjectRecord = withContext(Dispatchers.IO) {
        val displayName = requestedName?.takeIf(String::isNotBlank) ?: queryDisplayName(uri) ?: "Imported project"
        val projectName = displayName.substringBeforeLast('.', displayName).ifBlank { "Imported project" }
        val record = createProject(projectName)
        try {
            val targetName = PathPolicy.validateFileName(displayName)
            copyUriToFile(uri, resolve(record.id, targetName), MAX_IMPORT_BYTES)
            record
        } catch (error: Exception) {
            runCatching { deleteProject(record.id) }
            throw error
        }
    }

    suspend fun importDocument(projectId: String, uri: Uri, requestedName: String? = null): String = withContext(Dispatchers.IO) {
        val name = PathPolicy.validateFileName(requestedName?.takeIf(String::isNotBlank) ?: queryDisplayName(uri) ?: "imported.txt")
        val target = resolve(projectId, name)
        check(!target.exists()) { "A file named '$name' already exists in this project." }
        copyUriToFile(uri, target, MAX_IMPORT_BYTES)
        touchProject(projectId)
        name
    }

    suspend fun importDocumentTree(treeUri: Uri, requestedName: String? = null): ProjectRecord = withContext(Dispatchers.IO) {
        val source = DocumentFile.fromTreeUri(appContext, treeUri)
            ?: throw IllegalArgumentException("Android could not open the selected folder.")
        val projectName = requestedName?.takeIf(String::isNotBlank)
            ?: source.name?.takeIf(String::isNotBlank)
            ?: "Imported project"
        val record = createProject(projectName)
        try {
            var copiedBytes = 0L
            var copiedEntries = 0
            fun copyChildren(directory: DocumentFile, destination: File) {
                val children = directory.listFiles()
                for (child in children) {
                    copiedEntries++
                    check(copiedEntries <= MAX_PROJECT_ENTRIES) { "The selected folder has more than $MAX_PROJECT_ENTRIES items." }
                    val name = PathPolicy.validateFileName(child.name ?: "imported-item-$copiedEntries")
                    val target = File(destination, name)
                    if (child.isDirectory) {
                        check(target.mkdir()) { "Could not create imported folder '$name'." }
                        copyChildren(child, target)
                    } else {
                        check(child.isFile) { "The selected item '$name' is not a readable file." }
                        val input = resolver.openInputStream(child.uri)
                            ?: throw IllegalStateException("Android could not read '$name'.")
                        input.use { sourceStream ->
                            target.outputStream().use { output ->
                                val count = copyLimited(sourceStream, output, MAX_IMPORT_BYTES - copiedBytes)
                                copiedBytes += count
                            }
                        }
                    }
                }
            }
            copyChildren(source, requireProjectRoot(record.id))
            record
        } catch (error: Exception) {
            runCatching { deleteProject(record.id) }
            throw error
        }
    }

    suspend fun importZip(uri: Uri, projectName: String): ProjectRecord = withContext(Dispatchers.IO) {
        val record = createProject(projectName)
        try {
            val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Android could not open this ZIP archive.")
            val root = requireProjectRoot(record.id)
            var totalBytes = 0L
            var entryCount = 0
            ZipInputStream(BufferedInputStream(input)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    check(entryCount <= MAX_PROJECT_ENTRIES) { "This archive contains too many entries." }
                    val relative = PathPolicy.safeArchivePath(entry.name)
                    val target = resolve(record.id, relative)
                    if (entry.isDirectory) {
                        check(target == root || target.mkdirs() || target.isDirectory) { "Could not create archive folder." }
                    } else {
                        val parent = target.parentFile
                        check(parent == null || parent.isDirectory || parent.mkdirs()) { "Could not create archive folder." }
                        check(!target.exists()) { "The archive contains duplicate path '$relative'." }
                        target.outputStream().use { output ->
                            val count = copyLimited(zip, output, MAX_IMPORT_BYTES - totalBytes)
                            totalBytes += count
                        }
                    }
                    zip.closeEntry()
                }
            }
            record
        } catch (error: Exception) {
            runCatching { deleteProject(record.id) }
            throw error
        }
    }

    suspend fun exportZip(projectId: String, destination: Uri) = withContext(Dispatchers.IO) {
        val root = requireProjectRoot(projectId)
        val output = resolver.openOutputStream(destination, "w")
            ?: throw IllegalStateException("Android could not create the ZIP file at the selected destination.")
        ZipOutputStream(BufferedOutputStream(output)).use { zip ->
            val entries = listTree(projectId)
            entries.forEach { node ->
                val name = PathPolicy.normalizeRelativePath(node.relativePath) + if (node.isDirectory) "/" else ""
                zip.putNextEntry(ZipEntry(name))
                if (!node.isDirectory) root.resolve(node.relativePath).inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
        }
    }

    suspend fun resolveForExecution(projectId: String, relativePath: String): File = withContext(Dispatchers.IO) {
        resolve(projectId, relativePath)
    }

    private suspend fun retargetRecentFiles(projectId: String, oldPath: String, newPath: String, includeDescendants: Boolean) {
        val recent = database.recentFiles().filesForProject(projectId)
        recent.forEach { file ->
            val matches = file.relativePath == oldPath || (includeDescendants && file.relativePath.startsWith("$oldPath/"))
            if (matches) {
                val movedPath = newPath + file.relativePath.removePrefix(oldPath)
                database.recentFiles().deletePath(projectId, file.relativePath)
                database.recentFiles().upsert(file.copy(relativePath = movedPath))
            }
        }
    }

    private suspend fun deleteRecentFiles(projectId: String, path: String, includeDescendants: Boolean) {
        database.recentFiles().filesForProject(projectId)
            .filter { it.relativePath == path || (includeDescendants && it.relativePath.startsWith("$path/")) }
            .forEach { database.recentFiles().deletePath(projectId, it.relativePath) }
    }

    private suspend fun touchProject(projectId: String) {
        val project = database.projects().getProject(projectId) ?: throw IllegalArgumentException("Project not found.")
        database.projects().upsert(project.copy(updatedAt = System.currentTimeMillis()))
    }

    private suspend fun requireProjectRoot(projectId: String): File {
        require(database.projects().getProject(projectId) != null) { "This project is no longer available." }
        val root = File(projectDirectory, projectId).canonicalFile
        check(root.path.startsWith(projectDirectory.canonicalPath + File.separator) && root.isDirectory) {
            "The project folder is missing or inaccessible."
        }
        return root
    }

    private suspend fun resolve(projectId: String, relativePath: String): File {
        val safePath = PathPolicy.normalizeRelativePath(relativePath, allowEmpty = true)
        val root = requireProjectRoot(projectId)
        val target = if (safePath.isEmpty()) root else File(root, safePath).canonicalFile
        check(target == root || target.path.startsWith(root.path + File.separator)) {
            "The selected path is outside this project."
        }
        return target
    }

    private fun queryDisplayName(uri: Uri): String? = runCatching {
        resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull()

    private fun copyUriToFile(uri: Uri, target: File, maxBytes: Long) {
        val input = resolver.openInputStream(uri) ?: throw IllegalStateException("Android could not read the selected file.")
        input.use { stream ->
            target.outputStream().use { output ->
                copyLimited(stream, output, maxBytes)
            }
        }
    }

    private fun copyLimited(input: InputStream, output: java.io.OutputStream, maximum: Long): Long {
        require(maximum >= 0) { "The import exceeds the $MAX_IMPORT_BYTES byte limit." }
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var total = 0L
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            check(total <= maximum) { "The import exceeds the $MAX_IMPORT_BYTES byte limit." }
            output.write(buffer, 0, read)
        }
        return total
    }

    companion object {
        const val MAX_EDITABLE_FILE_BYTES = 2L * 1024 * 1024
        private const val MAX_IMPORT_BYTES = 128L * 1024 * 1024
        private const val MAX_PROJECT_ENTRIES = 10_000
    }
}
