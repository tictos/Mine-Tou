package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.Contact
import com.example.viewmodel.ContactsViewModel

import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreen(
    viewModel: ContactsViewModel,
    onNavigateToAdd: () -> Unit,
    onNavigateToEdit: (Contact) -> Unit
) {
    val contacts by viewModel.uiState.collectAsStateWithLifecycle()
    val callLogs by viewModel.callLogs.collectAsStateWithLifecycle()
    val context = LocalContext.current
    
    var selectedTab by remember { mutableStateOf(0) } // 0: Contacts, 1: Journal
    var pendingCallNumber by remember { mutableStateOf<String?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let { viewModel.exportContactsToCsv(context, it) }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importContactsFromCsv(context, it) }
    }
    
    val callPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted && pendingCallNumber != null) {
            val num = pendingCallNumber!!
            viewModel.registerCall(num)
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            pendingCallNumber = null
        }
    }

    val makeCall = { number: String ->
        viewModel.registerCall(number)
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } else {
            pendingCallNumber = number
            callPermissionLauncher.launch(Manifest.permission.CALL_PHONE)
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Text(
                        if (selectedTab == 0) "Contacts" else "Journal d'appels", 
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ) 
                },
                navigationIcon = {
                    if (selectedTab == 0) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(
                                    Icons.Default.MoreVert, 
                                    contentDescription = "Menu",
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Exporter les contacts (CSV)") },
                                    onClick = {
                                        showMenu = false
                                        exportLauncher.launch("contacts_backup.csv")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Importer des contacts (CSV)") },
                                    onClick = {
                                        showMenu = false
                                        importLauncher.launch(arrayOf("text/comma-separated-values", "text/csv", "*/*"))
                                    }
                                )
                            }
                        }
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(
                            onClick = onNavigateToAdd,
                            modifier = Modifier
                                .padding(end = 8.dp)
                                .size(48.dp)
                                .background(Color(0xFF4A90E2), CircleShape)
                        ) {
                            Icon(
                                Icons.Default.PersonAdd, 
                                contentDescription = "Add Contact",
                                tint = Color.White
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                contentColor = MaterialTheme.colorScheme.onBackground
            ) {
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Person, contentDescription = "Contacts") },
                    label = { Text("Contacts") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Default.Phone, contentDescription = "Journal") }, 
                    label = { Text("Journal") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { padding ->
        if (selectedTab == 0) {
            if (contacts.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Phone,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "No contacts yet.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Tap the person icon at the top right to add one.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    items(contacts) { contact ->
                        ContactCard(
                            contact = contact, 
                            onCallClick = { makeCall(contact.phoneNumber) },
                            onDeleteClick = { viewModel.deleteContact(contact.id, context) },
                            onEditClick = { onNavigateToEdit(contact) }
                        )
                    }
                }
            }
        } else {
            if (callLogs.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    Text("Aucun journal d'appels récent.")
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                ) {
                    item {
                        Text(
                            "Aujourd'hui",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    items(callLogs) { log ->
                        CallLogCard(
                            log = log,
                            onCallClick = { makeCall(log.contact.phoneNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ContactCard(
    contact: Contact, 
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onEditClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(0.8f) // Tall rectangular cards
            .clip(RoundedCornerShape(64.dp)),
        shape = RoundedCornerShape(64.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (contact.imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(contact.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = contact.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback to placeholder
                Icon(
                    painter = painterResource(id = R.drawable.img_placeholder_avatar),
                    contentDescription = contact.name,
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Name label overlay at the bottom center
            Text(
                text = contact.name.ifBlank { contact.phoneNumber },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 36.sp
                ),
                color = Color.Cyan,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
            )
            
            // Action buttons on the right side
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 24.dp, end = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Delete button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFFE57373), CircleShape) // Red
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Delete",
                        tint = Color.Black,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Edit button
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF4DD0E1), CircleShape) // Cyan/Light Blue
                ) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Edit",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
                
                // Call button
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .size(56.dp)
                        .background(Color(0xFF81C784), CircleShape) // Green
                ) {
                    Icon(
                        Icons.Default.Phone, 
                        contentDescription = "Call",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CallLogCard(
    log: com.example.data.CallLogEntry,
    onCallClick: () -> Unit
) {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timeString = formatter.format(Date(log.timestamp))

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f) // Slightly more square than the contact card
            .clip(RoundedCornerShape(32.dp))
            .clickable(onClick = onCallClick),
        shape = RoundedCornerShape(32.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (log.contact.imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(log.contact.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = log.contact.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                // Fallback
                Icon(
                    painter = painterResource(id = R.drawable.img_placeholder_avatar),
                    contentDescription = log.contact.name,
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Name and time label overlay at the bottom left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 24.dp, bottom = 24.dp)
            ) {
                Text(
                    text = log.contact.name.ifBlank { log.contact.phoneNumber },
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White
                )
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            
            // Call type icon overlay at the bottom right
            val (icon, color) = when (log.callType) {
                2 -> Pair(Icons.Default.CallMade, Color(0xFF81C784)) // Outgoing: Green
                1 -> Pair(Icons.Default.CallReceived, Color(0xFF4DD0E1)) // Incoming: Cyan
                3 -> Pair(Icons.Default.CallMissed, Color(0xFFE57373)) // Missed: Red
                else -> Pair(Icons.Default.CallMade, Color(0xFF81C784))
            }
            
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 24.dp, bottom = 24.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Call Type",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

