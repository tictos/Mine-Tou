package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.viewmodel.ContactsViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddContactScreen(
    viewModel: ContactsViewModel,
    contactId: Int = 0,
    initialName: String = "",
    initialPhone: String = "",
    initialImageUri: String? = null,
    onNavigateBack: () -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var phoneNumber by remember { mutableStateOf(initialPhone) }
    var imageUri by remember { mutableStateOf<String?>(initialImageUri) }
    var showPhotoSourceDialog by remember { mutableStateOf(false) }
    var tempCameraUri by remember { mutableStateOf<Uri?>(null) }
    
    val context = LocalContext.current
    
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        uri?.let {
            try {
                val flag = Intent.FLAG_GRANT_READ_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(it, flag)
                imageUri = it.toString()
            } catch (e: Exception) {
                e.printStackTrace()
                imageUri = it.toString()
            }
        }
    }

    val takePictureLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.TakePicture()
    ) { success ->
        if (success && tempCameraUri != null) {
            imageUri = tempCameraUri.toString()
        }
    }

    val cameraPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            val uri = createTempImageUri(context)
            if (uri != null) {
                tempCameraUri = uri
                takePictureLauncher.launch(uri)
            }
        }
    }

    fun openCamera() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = createTempImageUri(context)
            if (uri != null) {
                tempCameraUri = uri
                takePictureLauncher.launch(uri)
            }
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(
                        if (contactId != 0) "Modifier le contact" else "Ajouter un contact", 
                        fontWeight = FontWeight.Bold
                    ) 
                },
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
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            
            // Photo Section
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .clickable { showPhotoSourceDialog = true },
                contentAlignment = Alignment.Center
            ) {
                if (imageUri != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(imageUri)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Photo sélectionnée",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.img_placeholder_avatar),
                            contentDescription = "Placeholder",
                            tint = Color.Unspecified,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
            
            Button(
                onClick = { showPhotoSourceDialog = true },
                modifier = Modifier.height(48.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Choisir une photo")
            }

            if (imageUri == null) {
                Text(
                    text = "* La photo est obligatoire",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Nom du contact (facultatif)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Numéro de téléphone *") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(16.dp)
            )
            
            Spacer(modifier = Modifier.weight(1f))
            
            Button(
                onClick = {
                    if (phoneNumber.isNotBlank() && imageUri != null) {
                        viewModel.saveContact(
                            context = context,
                            id = contactId,
                            name = name,
                            phoneNumber = phoneNumber,
                            imageUri = imageUri
                        )
                        onNavigateBack()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                enabled = phoneNumber.isNotBlank() && imageUri != null,
                shape = RoundedCornerShape(28.dp)
            ) {
                Text(
                    if (contactId != 0) "Mettre à jour le contact" else "Enregistrer le contact", 
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (showPhotoSourceDialog) {
        AlertDialog(
            onDismissRequest = { showPhotoSourceDialog = false },
            title = {
                Text(
                    text = "Source de la photo",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Card(
                        onClick = {
                            showPhotoSourceDialog = false
                            openCamera()
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CameraAlt,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Prendre une photo",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    Card(
                        onClick = {
                            showPhotoSourceDialog = false
                            photoPickerLauncher.launch(
                                androidx.activity.result.PickVisualMediaRequest(
                                    ActivityResultContracts.PickVisualMedia.ImageOnly
                                )
                            )
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.PhotoLibrary,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Text(
                                text = "Choisir dans la galerie",
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPhotoSourceDialog = false }) {
                    Text("Annuler")
                }
            }
        )
    }
}

private fun createTempImageUri(context: Context): Uri? {
    return try {
        val tempFile = File.createTempFile("contact_photo_", ".jpg", context.cacheDir).apply {
            createNewFile()
            deleteOnExit()
        }
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            tempFile
        )
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

