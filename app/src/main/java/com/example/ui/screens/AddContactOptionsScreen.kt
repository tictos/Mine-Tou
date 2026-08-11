package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactOptionsScreen(
    onNavigateToNewContact: () -> Unit,
    onNavigateToContactWithDetails: (String, String) -> Unit,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { uri: Uri? ->
        if (uri != null) {
            val (name, phone) = getContactDetails(context, uri)
            onNavigateToContactWithDetails(name, phone)
        }
    }
    
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            contactPickerLauncher.launch(null)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Ajouter un profil",
                fontSize = 32.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 48.dp)
            )
            
            // Blue Card
            OptionCard(
                title = "Sélectionner un contact",
                icon = Icons.Default.Contacts,
                backgroundColor = Color(0xFF3182CE),
                onClick = {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                        contactPickerLauncher.launch(null)
                    } else {
                        permissionLauncher.launch(Manifest.permission.READ_CONTACTS)
                    }
                }
            )
            
            Spacer(modifier = Modifier.height(24.dp))
            
            // Green Card
            OptionCard(
                title = "Créer un nouveau contact",
                icon = Icons.Default.PersonAdd,
                backgroundColor = Color(0xFF38A169),
                onClick = onNavigateToNewContact
            )
        }
    }
}

@Composable
fun OptionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    backgroundColor: Color,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = title,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp
            )
        }
    }
}

private fun getContactDetails(context: Context, contactUri: Uri): Pair<String, String> {
    var name = ""
    var number = ""
    
    try {
        val cursor = context.contentResolver.query(contactUri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val nameIndex = it.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                if (nameIndex >= 0) {
                    name = it.getString(nameIndex) ?: ""
                }
                val idIndex = it.getColumnIndex(ContactsContract.Contacts._ID)
                val id = if (idIndex >= 0) it.getString(idIndex) else ""
                
                val hasPhoneIndex = it.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)
                val hasPhone = if (hasPhoneIndex >= 0) it.getInt(hasPhoneIndex) > 0 else false
                
                if (hasPhone && id.isNotEmpty()) {
                    val phonesCursor = context.contentResolver.query(
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                        null,
                        "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                        arrayOf(id),
                        null
                    )
                    phonesCursor?.use { pc ->
                        if (pc.moveToFirst()) {
                            val numberIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                            if (numberIndex >= 0) {
                                number = pc.getString(numberIndex) ?: ""
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
    
    return Pair(name, number)
}
