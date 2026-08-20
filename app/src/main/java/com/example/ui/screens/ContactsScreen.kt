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
import androidx.compose.material.icons.filled.CallMade
import androidx.compose.material.icons.filled.CallMissed
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NotificationsActive
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.R
import com.example.data.CallLogEntity
import com.example.data.Contact
import com.example.service.CallNotificationListenerService
import com.example.viewmodel.ContactsViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
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
    var isNotificationServiceEnabled by remember {
        mutableStateOf(CallNotificationListenerService.isPermissionGranted(context))
    }

    LaunchedEffect(selectedTab) {
        isNotificationServiceEnabled = CallNotificationListenerService.isPermissionGranted(context)
    }

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
            viewModel.onCallInitiated(num)
            val intent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$num")).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
            pendingCallNumber = null
        }
    }

    val makeCall = { number: String, contact: Contact? ->
        if (contact != null) {
            viewModel.onCallInitiated(contact)
        } else {
            viewModel.onCallInitiated(number)
        }

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
                            if (selectedTab == 0) {
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
                            } else {
                                DropdownMenuItem(
                                    text = { Text("Vider le journal d'appels") },
                                    onClick = {
                                        showMenu = false
                                        viewModel.clearAllCallLogs()
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
                                contentDescription = "Ajouter Contact",
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
                            "Aucun contact enregistré.",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Appuyez sur le bouton bleu pour ajouter votre premier contact.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 24.dp)
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
                    items(contacts, key = { it.id }) { contact ->
                        ContactCard(
                            contact = contact, 
                            onCallClick = { makeCall(contact.phoneNumber, contact) },
                            onDeleteClick = { viewModel.deleteContact(contact.id, context) },
                            onEditClick = { onNavigateToEdit(contact) }
                        )
                    }
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
                if (!isNotificationServiceEnabled) {
                    item {
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.NotificationsActive,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(28.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        "Détection automatique des appels",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Pour afficher automatiquement la photo de ceux qui vous appellent ou des appels manqués, autorisez l'accès aux notifications.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Button(
                                    onClick = {
                                        CallNotificationListenerService.openPermissionSettings(context)
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    modifier = Modifier.align(Alignment.End)
                                ) {
                                    Text("Activer l'écoute")
                                }
                            }
                        }
                    }
                }

                if (callLogs.isEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = Icons.Default.Phone,
                                    contentDescription = null,
                                    modifier = Modifier.size(56.dp),
                                    tint = MaterialTheme.colorScheme.outline
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    "Aucun appel enregistré pour l'instant.",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "Les appels émis, reçus et manqués apparaîtront ici.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Text(
                            "Appels Récents",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    }
                    items(callLogs, key = { it.id }) { log ->
                        val matchedContact = contacts.find { it.id == log.contactId || (it.phoneNumber.isNotBlank() && it.phoneNumber == log.phoneNumber) }
                        CallLogCard(
                            log = log,
                            onCallClick = { makeCall(log.phoneNumber, matchedContact) },
                            onDeleteClick = { viewModel.deleteCallLog(log.id) }
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
            .clip(RoundedCornerShape(48.dp))
            .clickable(onClick = onCallClick),
        shape = RoundedCornerShape(48.dp),
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
                Icon(
                    painter = painterResource(id = R.drawable.img_placeholder_avatar),
                    contentDescription = contact.name,
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize()
                )
            }
            
            // Subtle dark scrim at the bottom for high contrast text
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )

            // Name label overlay at the bottom center
            Text(
                text = contact.name.ifBlank { contact.phoneNumber },
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 16.dp, vertical = 24.dp)
            )
            
            // Action buttons on the right side
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 20.dp, end = 20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Delete button
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFFE57373), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Delete, 
                        contentDescription = "Supprimer",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                // Edit button
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF4DD0E1), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Edit, 
                        contentDescription = "Modifier",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
                
                // Call button
                IconButton(
                    onClick = onCallClick,
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color(0xFF81C784), CircleShape)
                ) {
                    Icon(
                        Icons.Default.Phone, 
                        contentDescription = "Appeler",
                        tint = Color.White,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun CallLogCard(
    log: CallLogEntity,
    onCallClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val formattedDate = formatRelativeTime(log.timestamp)

    val (badgeText, badgeColor, badgeIcon) = when (log.callType) {
        3 -> Triple("Appel manqué", Color(0xFFE57373), Icons.Default.CallMissed) // Red
        1 -> Triple("Appel reçu", Color(0xFF4DD0E1), Icons.Default.CallReceived) // Cyan
        2 -> Triple("Appel émis", Color(0xFF81C784), Icons.Default.CallMade) // Green
        else -> Triple("Appel", Color(0xFF81C784), Icons.Default.Phone)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1.1f)
            .clip(RoundedCornerShape(36.dp))
            .clickable(onClick = onCallClick),
        shape = RoundedCornerShape(36.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (log.imageUri != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(log.imageUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = log.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.img_placeholder_avatar),
                    contentDescription = log.name,
                    tint = Color.Unspecified,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // Dark Scrim for readability
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .align(Alignment.BottomCenter)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f))
                        )
                    )
            )
            
            // Call Type Badge at Top-Left
            Surface(
                color = badgeColor,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .padding(16.dp)
                    .align(Alignment.TopStart)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = badgeText,
                        color = Color.White,
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            // Quick Delete Action at Top-Right
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier
                    .padding(16.dp)
                    .size(40.dp)
                    .align(Alignment.TopEnd)
                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Supprimer du journal",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }
            
            // Name and Relative Time at the bottom left
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 20.dp, end = 80.dp)
            ) {
                Text(
                    text = log.name.ifBlank { log.phoneNumber },
                    style = MaterialTheme.typography.headlineSmall.copy(
                        fontWeight = FontWeight.Bold
                    ),
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.9f)
                )
            }
            
            // Quick Call Button at the bottom right
            IconButton(
                onClick = onCallClick,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 20.dp, bottom = 20.dp)
                    .size(48.dp)
                    .background(Color(0xFF81C784), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Phone,
                    contentDescription = "Rappeler",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}

private fun formatRelativeTime(timestamp: Long): String {
    val now = Calendar.getInstance()
    val time = Calendar.getInstance().apply { timeInMillis = timestamp }

    val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    val timePart = timeFormat.format(Date(timestamp))

    return when {
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == time.get(Calendar.DAY_OF_YEAR) -> {
            "Aujourd'hui à $timePart"
        }
        now.get(Calendar.YEAR) == time.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) - time.get(Calendar.DAY_OF_YEAR) == 1 -> {
            "Hier à $timePart"
        }
        else -> {
            val dateFormat = SimpleDateFormat("dd MMM à HH:mm", Locale.getDefault())
            dateFormat.format(Date(timestamp))
        }
    }
}
