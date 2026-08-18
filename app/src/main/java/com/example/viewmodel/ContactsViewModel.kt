package com.example.viewmodel

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.CallLogEntry
import com.example.data.Contact
import com.example.data.ContactRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class ContactsViewModel(private val repository: ContactRepository) : ViewModel() {

    val uiState: StateFlow<List<Contact>> = repository.allContacts
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _callLogs = MutableStateFlow<List<CallLogEntry>>(emptyList())
    val callLogs: StateFlow<List<CallLogEntry>> = _callLogs.asStateFlow()

    fun registerCall(contact: Contact) {
        val newEntry = CallLogEntry(
            id = java.util.UUID.randomUUID().toString(),
            contact = contact,
            timestamp = System.currentTimeMillis(),
            callType = 2
        )
        _callLogs.value = listOf(newEntry) + _callLogs.value.filterNot { it.contact.id == contact.id && System.currentTimeMillis() - it.timestamp < 1000 }
    }

    fun registerCall(phoneNumber: String) {
        viewModelScope.launch {
            val contacts = repository.allContacts.first()
            val normalizedNumber = phoneNumber.replace("\\s".toRegex(), "")
            val matchedContact = contacts.find { c -> 
                val dbNum = c.phoneNumber.replace("\\s".toRegex(), "")
                (normalizedNumber.isNotBlank() && dbNum.isNotBlank()) && (normalizedNumber.endsWith(dbNum) || dbNum.endsWith(normalizedNumber))
            } ?: Contact(name = phoneNumber, phoneNumber = phoneNumber, imageUri = null)
            registerCall(matchedContact)
        }
    }

    fun saveContact(context: Context, id: Int = 0, name: String, phoneNumber: String, imageUri: String?) {
        viewModelScope.launch {
            val internalImageUri = saveImageToInternalStorage(context, imageUri)
            repository.insert(
                Contact(
                    id = id,
                    name = name.trim(),
                    phoneNumber = phoneNumber.trim(),
                    imageUri = internalImageUri
                )
            )
        }
    }

    fun addContact(context: Context, name: String, phoneNumber: String, imageUri: String?) {
        saveContact(context = context, id = 0, name = name, phoneNumber = phoneNumber, imageUri = imageUri)
    }

    fun deleteContact(id: Int, context: Context? = null) {
        viewModelScope.launch {
            if (context != null) {
                try {
                    val contacts = repository.allContacts.first()
                    val target = contacts.find { it.id == id }
                    target?.imageUri?.let { uriStr ->
                        val uri = Uri.parse(uriStr)
                        if (uri.scheme == "file") {
                            uri.path?.let { path ->
                                val file = java.io.File(path)
                                if (file.exists() && path.contains("contact_photos")) {
                                    file.delete()
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            repository.deleteById(id)
        }
    }

    private suspend fun saveImageToInternalStorage(context: Context, sourceUriString: String?): String? {
        if (sourceUriString.isNullOrBlank()) return null
        val sourceUri = Uri.parse(sourceUriString)
        
        val photosDir = java.io.File(context.filesDir, "contact_photos")
        if (!photosDir.exists()) photosDir.mkdirs()
        
        if (sourceUri.path?.contains(photosDir.absolutePath) == true) {
            return sourceUriString
        }

        return withContext(Dispatchers.IO) {
            try {
                val bitmap = context.contentResolver.openInputStream(sourceUri)?.use { stream ->
                    android.graphics.BitmapFactory.decodeStream(stream)
                } ?: return@withContext sourceUriString

                val maxDimension = 500
                val width = bitmap.width
                val height = bitmap.height
                val scale = if (width > maxDimension || height > maxDimension) {
                    val max = Math.max(width, height)
                    maxDimension.toFloat() / max
                } else 1.0f

                val scaledBitmap = if (scale < 1.0f) {
                    android.graphics.Bitmap.createScaledBitmap(
                        bitmap,
                        (width * scale).toInt(),
                        (height * scale).toInt(),
                        true
                    )
                } else {
                    bitmap
                }

                val fileName = "photo_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.jpg"
                val destFile = java.io.File(photosDir, fileName)
                destFile.outputStream().use { out ->
                    scaledBitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
                }

                if (scaledBitmap != bitmap) {
                    scaledBitmap.recycle()
                }
                bitmap.recycle()

                Uri.fromFile(destFile).toString()
            } catch (e: Exception) {
                e.printStackTrace()
                sourceUriString
            }
        }
    }

    fun exportContactsToCsv(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contacts = repository.allContacts.first()
                val sb = StringBuilder()
                sb.append("Name,PhoneNumber,ImageUri\n")
                contacts.forEach { contact ->
                    val escapedName = escapeCsvCell(contact.name)
                    val escapedPhone = escapeCsvCell(contact.phoneNumber)
                    val escapedImg = escapeCsvCell(contact.imageUri ?: "")
                    sb.append("$escapedName,$escapedPhone,$escapedImg\n")
                }
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(sb.toString().toByteArray(Charsets.UTF_8))
                }
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "${contacts.size} contact(s) exporté(s) en CSV !", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erreur lors de l'exportation CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun importContactsFromCsv(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val lines = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readLines() } ?: emptyList()
                if (lines.isNotEmpty()) {
                    var count = 0
                    val startIndex = if (lines[0].lowercase().contains("phone")) 1 else 0
                    for (i in startIndex until lines.size) {
                        val line = lines[i].trim()
                        if (line.isNotBlank()) {
                            val tokens = parseCsvLine(line)
                            if (tokens.size >= 2) {
                                val name = tokens[0]
                                val phone = tokens[1]
                                val imgUri = if (tokens.size >= 3) tokens[2].ifBlank { null } else null
                                if (phone.isNotBlank()) {
                                    val savedImgUri = saveImageToInternalStorage(context, imgUri)
                                    repository.insert(
                                        Contact(
                                            name = name,
                                            phoneNumber = phone,
                                            imageUri = savedImgUri
                                        )
                                    )
                                    count++
                                }
                            }
                        }
                    }
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "$count contact(s) importé(s) depuis CSV !", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Erreur lors de l'importation CSV: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun escapeCsvCell(value: String): String {
        return "\"" + value.replace("\"", "\"\"") + "\""
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var cur = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            if (inQuotes) {
                if (ch == '"') {
                    if (i + 1 < line.length && line[i + 1] == '"') {
                        cur.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    cur.append(ch)
                }
            } else {
                if (ch == '"') {
                    inQuotes = true
                } else if (ch == ',' || ch == ';') {
                    result.add(cur.toString().trim())
                    cur = StringBuilder()
                } else {
                    cur.append(ch)
                }
            }
            i++
        }
        result.add(cur.toString().trim())
        return result
    }
}

class ContactsViewModelFactory(private val repository: ContactRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(ContactsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return ContactsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
