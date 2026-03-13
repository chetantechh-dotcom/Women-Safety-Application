package com.example.womensafetyapplication

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.os.Bundle
import android.provider.Settings
import android.telephony.SmsManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.*

class MainActivity : ComponentActivity() {

    private lateinit var sharedPreferences: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        sharedPreferences = getSharedPreferences("WomenSafetyPrefs", MODE_PRIVATE)

        val permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
                val granted = permissions.entries.all { it.value }
                if (!granted) Toast.makeText(this, "Permissions denied!", Toast.LENGTH_SHORT).show()
            }

        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.SEND_SMS,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )

        setContent { WomenSafetyAppUI() }
    }

    // ------------------- UI -------------------
    @Composable
    fun WomenSafetyAppUI() {
        var showMenu by remember { mutableStateOf(false) }
        var showContactsScreen by remember { mutableStateOf(false) }
        var showAboutScreen by remember { mutableStateOf(false) }

        Scaffold(
            topBar = { AppTopBar(onMenuClick = { showMenu = true }) },
            backgroundColor = Color.White
        ) { padding ->
            Box(modifier = Modifier.fillMaxSize().padding(padding)) {

                // ---------------- Background image + SOS ----------------
                Box(modifier = Modifier.fillMaxSize()) {
                    // Background Image with adjustable position
                    Image(
                        painter = painterResource(id = R.drawable.background_image),
                        contentDescription = "Background",
                        modifier = Modifier
                            .fillMaxSize()
                            .offset(x = 5.dp, y = (-5).dp), // adjust y offset as needed
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter // change alignment if needed
                    )

                    // SOS button center me
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        SOSButton { checkLocationSettingsAndTriggerSOS() }
                    }
                }

                // ---------------- Drawer ----------------
                if (showMenu) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clickable(
                                onClick = { showMenu = false },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() }
                            )
                    ) {
                        MenuDrawer(
                            onContactsClick = {
                                showContactsScreen = true
                                showMenu = false
                            },
                            onAboutClick = {
                                showAboutScreen = true
                                showMenu = false
                            }
                        )
                    }
                }

                // ---------------- Screens ----------------
                if (showContactsScreen) {
                    ContactsScreen(onClose = { showContactsScreen = false }, sharedPreferences)
                }

                if (showAboutScreen) {
                    AboutScreen(onClose = { showAboutScreen = false })
                }
            }
        }
    }

    // ---------------- Top Bar ----------------
    @Composable
    fun AppTopBar(onMenuClick: () -> Unit) {
        TopAppBar(
            backgroundColor = Color.White,
            elevation = 4.dp,
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            IconButton(onClick = onMenuClick, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Menu, contentDescription = "Menu")
            }

            Spacer(modifier = Modifier.width(8.dp))

            val logoResId: Int? = runCatching { R.drawable.logo }.getOrNull()
            if (logoResId != null) {
                Image(
                    painter = painterResource(id = logoResId),
                    contentDescription = "Logo",
                    modifier = Modifier.size(40.dp),
                    contentScale = ContentScale.Crop
                )
            }

            Spacer(modifier = Modifier.width(8.dp))
            Text("Women Safety App", style = MaterialTheme.typography.h6)
        }
    }

    // ---------------- SOS Button ----------------
    @Composable
    fun SOSButton(onClick: () -> Unit) {
        Box(
            modifier = Modifier
                .size(130.dp)  // thoda bada
                .shadow(6.dp, shape = CircleShape)
                .clickable { onClick() },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(listOf(Color.Red, Color(0xFFFF6666))),
                        shape = CircleShape
                    )
            )
            Text("SOS", style = MaterialTheme.typography.h5, color = Color.White)
        }
    }

    // ---------------- Drawer ----------------
    @Composable
    fun MenuDrawer(onContactsClick: () -> Unit, onAboutClick: () -> Unit) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(250.dp)
                .padding(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color(0xFF9C27B0), shape = RoundedCornerShape(50))
                    .clickable { onContactsClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Contacts", color = Color.White)
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
                    .background(Color(0xFF9C27B0), shape = RoundedCornerShape(50))
                    .clickable { onAboutClick() }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("About", color = Color.White)
            }
        }
    }

    // ---------------- Contacts Screen ----------------
    @Composable
    fun ContactsScreen(onClose: () -> Unit, sharedPreferences: SharedPreferences) {
        var showAddEditDialog by remember { mutableStateOf(false) }
        var editIndex by remember { mutableStateOf(-1) }
        val contacts = remember { mutableStateListOf<Pair<String, String>>() }

        LaunchedEffect(Unit) {
            val name = sharedPreferences.getString("name", null)
            val number = sharedPreferences.getString("number", null)
            if (!name.isNullOrBlank() && !number.isNullOrBlank()) contacts.add(name to number)
        }

        AlertDialog(
            onDismissRequest = onClose,
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Contacts")
                    IconButton(onClick = {
                        editIndex = -1
                        showAddEditDialog = true
                    }) { Icon(Icons.Default.Add, contentDescription = "Add Contact") }
                }
            },
            text = {
                Column {
                    if (contacts.isEmpty()) {
                        Text("No contacts saved.\nClick + to add your emergency contact.")
                    } else {
                        contacts.forEachIndexed { index, contact ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(contact.first)
                                    Text(contact.second, style = MaterialTheme.typography.body2)
                                }
                                IconButton(onClick = { editIndex = index; showAddEditDialog = true }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                                }
                                IconButton(onClick = {
                                    contacts.removeAt(index)
                                    sharedPreferences.edit().clear().apply()
                                }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
        )

        if (showAddEditDialog) {
            AddEditContactDialog(
                contact = if (editIndex >= 0) contacts[editIndex] else null,
                onDismiss = { showAddEditDialog = false },
                onSave = { name, number ->
                    if (editIndex >= 0) contacts[editIndex] = name to number
                    else contacts.add(name to number)
                    sharedPreferences.edit()
                        .putString("name", contacts[0].first)
                        .putString("number", contacts[0].second)
                        .apply()
                    showAddEditDialog = false
                }
            )
        }
    }

    @Composable
    fun AddEditContactDialog(contact: Pair<String, String>?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
        var name by remember { mutableStateOf(contact?.first ?: "") }
        var number by remember { mutableStateOf(contact?.second ?: "") }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text(if (contact == null) "Add Contact" else "Edit Contact") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    OutlinedTextField(value = number, onValueChange = { number = it }, label = { Text("Number") }, singleLine = true)
                }
            },
            confirmButton = { TextButton(onClick = { onSave(name.trim(), number.trim()) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }

    // ---------------- About Screen ----------------
    @Composable
    fun AboutScreen(onClose: () -> Unit) {
        AlertDialog(
            onDismissRequest = onClose,
            title = { Text("About This App") },
            text = {
                Column {
                    Text("This is a Women Safety App.\nIt allows users to send SOS messages with location to emergency contacts.")
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Developer: Chetan")
                    Text("Contact: urchetan.x@gmail.com")
                }
            },
            confirmButton = { TextButton(onClick = onClose) { Text("Close") } }
        )
    }

    // ---------------- SOS + Location ----------------
    private fun checkLocationSettingsAndTriggerSOS() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).build()
        val builder = LocationSettingsRequest.Builder()
            .addLocationRequest(locationRequest)
            .setAlwaysShow(true)

        val client = LocationServices.getSettingsClient(this)
        val task = client.checkLocationSettings(builder.build())

        task.addOnSuccessListener { triggerSOS() }
        task.addOnFailureListener { exception ->
            if (exception is ResolvableApiException) {
                try { exception.startResolutionForResult(this, 1001) }
                catch (sendEx: Exception) { sendEx.printStackTrace() }
            } else {
                Toast.makeText(this, "Please turn on Location!", Toast.LENGTH_SHORT).show()
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            }
        }
    }

    private fun triggerSOS() {
        val number = sharedPreferences.getString("number", "")
        val userName = sharedPreferences.getString("username", "User")

        if (number.isNullOrEmpty()) {
            Toast.makeText(this, "No SOS number saved!", Toast.LENGTH_SHORT).show()
            return
        }

        val fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                sendSOSMessage(userName ?: "User", number, location)
            } else {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { newLocation ->
                        if (newLocation != null) {
                            sendSOSMessage(userName ?: "User", number, newLocation)
                        } else {
                            Toast.makeText(this, "Unable to fetch location!", Toast.LENGTH_SHORT).show()
                        }
                    }
            }
        }
    }

    private fun sendSOSMessage(user: String, number: String, location: Location) {
        val msg = "SOS from $user! Location: https://maps.google.com/?q=${location.latitude},${location.longitude}"
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(number, null, msg, null, null)
            Toast.makeText(this, "SOS Sent to $number", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to send SMS", Toast.LENGTH_SHORT).show()
            e.printStackTrace()
        }
    }
}