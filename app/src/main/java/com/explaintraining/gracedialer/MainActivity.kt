package com.explaintraining.gracedialer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.ContactsContract
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<MainViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        installKioskWindowFlags()

        setContent {
            GraceDialerTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = GraceBlack,
                ) {
                    GraceDialerApp(
                        context = this,
                        viewModel = viewModel,
                        hasContactsPermission = hasContactsPermission(),
                        launchDialer = ::launchZoiperDial,
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        installKioskWindowFlags()
    }

    override fun onStart() {
        super.onStart()
        forceMaxVolume()
    }

    private fun hasContactsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun installKioskWindowFlags() {
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (
            android.view.View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or android.view.View.SYSTEM_UI_FLAG_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or android.view.View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            )
    }

    private fun launchZoiperDial(contact: GraceContact) {
        forceMaxVolume()
        val normalized = contact.phoneNumber.filter { it.isDigit() || it == '+' }
        val intents = listOf(
            Intent(Intent.ACTION_VIEW, Uri.parse("zoiper:$normalized")).setPackage("com.zoiper.android.app"),
            Intent(Intent.ACTION_VIEW, Uri.parse("zoiper:$normalized")).setPackage("com.zoiperpremium.android.app"),
            Intent(Intent.ACTION_VIEW, Uri.parse("zoiper:$normalized")),
            Intent(Intent.ACTION_VIEW, Uri.parse("tel:$normalized")),
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:$normalized")),
        )

        for (intent in intents) {
            try {
                startActivity(intent)
                return
            } catch (_: ActivityNotFoundException) {
                // Try the next dialing option.
            } catch (_: SecurityException) {
                // Try the next dialing option.
            }
        }
    }

    private fun forceMaxVolume() {
        val audioManager = getSystemService(AUDIO_SERVICE) as? AudioManager ?: return
        val streams = listOf(
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_VOICE_CALL,
            AudioManager.STREAM_RING,
            AudioManager.STREAM_NOTIFICATION,
            AudioManager.STREAM_SYSTEM,
        )

        streams.forEach { stream ->
            runCatching {
                val maxVolume = audioManager.getStreamMaxVolume(stream)
                if (maxVolume > 0) {
                    audioManager.setStreamVolume(stream, maxVolume, 0)
                }
            }
        }
    }
}

@Composable
private fun GraceDialerApp(
    context: Context,
    viewModel: MainViewModel,
    hasContactsPermission: Boolean,
    launchDialer: (GraceContact) -> Unit,
) {
    val composeContext = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    var callingContact by remember { mutableStateOf<GraceContact?>(null) }
    val contactsPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.onContactsPermissionResult(granted)
    }

    LaunchedEffect(hasContactsPermission) {
        viewModel.onContactsPermissionResult(hasContactsPermission)
    }

    LaunchedEffect(uiState.hasContactsPermission) {
        if (uiState.hasContactsPermission) {
            viewModel.loadContacts(composeContext)
        }
    }

    LaunchedEffect(callingContact) {
        val selectedContact = callingContact ?: return@LaunchedEffect
        delay(900)
        launchDialer(selectedContact)
        callingContact = null
    }

    if (callingContact != null) {
        CallingScreen(contact = callingContact!!)
        return
    }

    when {
        !uiState.hasContactsPermission -> PermissionScreen(
            onGrantPermission = {
                contactsPermissionLauncher.launch(Manifest.permission.READ_CONTACTS)
            },
        )

        uiState.contacts.isEmpty() -> EmptyContactsScreen(
            message = uiState.message,
        )

        else -> ContactsScreen(
            contacts = uiState.contacts,
            onContactTap = { contact ->
                forceMaxVolume(context)
                callingContact = contact
            },
        )
    }
}

@Composable
private fun PermissionScreen(onGrantPermission: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraceBlack)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Grace's Phone",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Allow contacts once so the app can show the six saved callers on this tablet.",
            color = Color.White,
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 640.dp),
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onGrantPermission,
            contentPadding = PaddingValues(horizontal = 40.dp, vertical = 22.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = GraceBlue,
                contentColor = GraceBlack,
            ),
        ) {
            Text(
                text = "Allow Contacts",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyContactsScreen(message: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraceBlack)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Grace's Phone",
            color = Color.White,
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = message,
            color = Color.White,
            fontSize = 22.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 720.dp),
        )
    }
}

@Composable
private fun CallingScreen(contact: GraceContact) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(GraceBlack)
            .padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContactAvatar(contact = contact)
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "CALLING",
            color = Color.White,
            fontSize = 46.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 3.sp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = contact.displayName,
            color = GraceBlue,
            fontSize = 54.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            lineHeight = 56.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = contact.prettyNumber,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 24.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun ContactsScreen(
    contacts: List<GraceContact>,
    onContactTap: (GraceContact) -> Unit,
) {
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(GraceBlack)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        val headerHeight = 64.dp
        val gridGap = 14.dp
        val availableHeight = maxHeight - headerHeight - 12.dp
        val fittedCardHeight = ((availableHeight - (gridGap * 2)) / 3).coerceAtLeast(176.dp)

        Text(
            text = "Grace's Phone",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            letterSpacing = 3.2.sp,
            modifier = Modifier
                .fillMaxWidth()
                .height(headerHeight)
                .padding(bottom = 10.dp),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            verticalArrangement = Arrangement.spacedBy(gridGap),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = headerHeight),
        ) {
            items(contacts, key = { it.id }) { contact ->
                ContactCard(
                    contact = contact,
                    cardHeight = fittedCardHeight,
                    onContactTap = onContactTap,
                )
            }
        }
   }
}

@Composable
private fun ContactCard(
    contact: GraceContact,
    cardHeight: androidx.compose.ui.unit.Dp,
    onContactTap: (GraceContact) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(cardHeight)
            .clip(RoundedCornerShape(28.dp))
            .background(Color.Black)
            .border(3.dp, GraceBlue, RoundedCornerShape(28.dp))
            .clickable { onContactTap(contact) }
            .padding(horizontal = 22.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ContactAvatar(contact = contact)
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = contact.displayName,
            color = GraceBlue,
            fontSize = 44.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            lineHeight = 45.sp,
            letterSpacing = 1.2.sp,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = contact.prettyNumber,
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 17.sp,
            letterSpacing = 0.8.sp,
        )
    }
}

@Composable
private fun ContactAvatar(contact: GraceContact) {
    if (contact.photo != null) {
        Image(
            bitmap = contact.photo,
            contentDescription = contact.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .border(3.dp, GraceBlue, CircleShape),
        )
    } else {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(GraceBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = contact.initials,
                color = GraceBlack,
                fontSize = 34.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
    }
}

data class GraceContact(
    val id: Long,
    val displayName: String,
    val phoneNumber: String,
    val prettyNumber: String,
    val initials: String,
    val photo: ImageBitmap?,
)

data class GraceUiState(
    val hasContactsPermission: Boolean = false,
    val contacts: List<GraceContact> = emptyList(),
    val message: String = "Loading contacts...",
    val loadedOnce: Boolean = false,
)

class MainViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(GraceUiState())
    val uiState: StateFlow<GraceUiState> = _uiState

    fun onContactsPermissionResult(granted: Boolean) {
        _uiState.update {
            it.copy(
                hasContactsPermission = granted,
                message = if (granted) "Loading contacts..." else "Contacts access is required.",
            )
        }
    }

    fun loadContacts(context: Context) {
        if (_uiState.value.loadedOnce) return

        viewModelScope.launch {
            val contacts = withContext(Dispatchers.IO) {
                loadGraceContacts(context)
            }
            _uiState.update {
                it.copy(
                    contacts = contacts,
                    loadedOnce = true,
                    message = if (contacts.isEmpty()) {
                        "No contacts with phone numbers were found on this tablet."
                    } else {
                        it.message
                    },
                )
            }
        }
    }
}

@Composable
private fun GraceDialerTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}

private val GraceBlue = Color(0xFF2157FF)
private val GraceBlack = Color(0xFF050505)
private val PreferredContactOrder = listOf(
    "pat",
    "john",
    "lenny",
    "steven",
    "jean",
    "kyle",
)
private val DisplayNameOverrides = mapOf(
    "jean" to "JEAN",
    "lenny" to "LENNY",
)

private fun loadGraceContacts(context: Context): List<GraceContact> {
    val projection = arrayOf(
        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
        ContactsContract.CommonDataKinds.Phone.NUMBER,
        ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
    )

    val sortOrder = "${ContactsContract.CommonDataKinds.Phone.STARRED} DESC, " +
        "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} COLLATE NOCASE ASC"

    val contacts = linkedMapOf<String, GraceContact>()
    context.contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        projection,
        null,
        null,
        sortOrder,
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
        val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
        val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
        val photoIndex =
            cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI)

        while (cursor.moveToNext()) {
            val number = cursor.getString(numberIndex) ?: continue
            val normalized = number.filter { it.isDigit() || it == '+' }
            if (normalized.isBlank() || contacts.containsKey(normalized)) continue

            val displayName = cursor.getString(nameIndex) ?: "Contact"
            val contact = GraceContact(
                id = cursor.getLong(idIndex),
                displayName = displayName,
                phoneNumber = normalized,
                prettyNumber = number,
                initials = initialsFor(displayName),
                photo = loadPhoto(context, cursor.getString(photoIndex)),
            )
            contacts[normalized] = contact
        }
    }

    return contacts.values
        .sortedWith(
            compareBy<GraceContact> { contact ->
                val normalizedName = normalizeName(contact.displayName)
                PreferredContactOrder.indexOf(normalizedName).let { index ->
                    if (index == -1) Int.MAX_VALUE else index
                }
            }.thenBy { normalizeName(it.displayName) },
        )
        .take(6)
        .map { contact ->
            val normalizedName = normalizeName(contact.displayName)
            contact.copy(
                displayName = DisplayNameOverrides[normalizedName] ?: contact.displayName,
            )
        }
}

private fun loadPhoto(context: Context, photoUri: String?): ImageBitmap? {
    if (photoUri.isNullOrBlank()) return null
    return runCatching {
        context.contentResolver.openInputStream(Uri.parse(photoUri))?.use { stream ->
            BitmapFactory.decodeStream(stream)?.asImageBitmap()
        }
    }.getOrNull()
}

private fun initialsFor(name: String): String {
    val parts = name
        .trim()
        .split("\\s+".toRegex())
        .filter { it.isNotBlank() }

    return when {
        parts.isEmpty() -> "?"
        parts.size == 1 -> parts.first().take(1).uppercase()
        else -> (parts.first().take(1) + parts.last().take(1)).uppercase()
    }
}

private fun normalizeName(name: String): String {
    return name.lowercase().replace(Regex("[^a-z0-9]"), "")
}

private fun forceMaxVolume(context: Context) {
    val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    val streams = listOf(
        AudioManager.STREAM_MUSIC,
        AudioManager.STREAM_VOICE_CALL,
        AudioManager.STREAM_RING,
        AudioManager.STREAM_NOTIFICATION,
        AudioManager.STREAM_SYSTEM,
    )

    streams.forEach { stream ->
        runCatching {
            val maxVolume = audioManager.getStreamMaxVolume(stream)
            if (maxVolume > 0) {
                audioManager.setStreamVolume(stream, maxVolume, 0)
            }
        }
    }
}
