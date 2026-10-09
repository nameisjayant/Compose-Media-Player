package com.nameisjayant.androidpractice.contactpicker

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.nameisjayant.androidpractice.ui.theme.AndroidPracticeTheme
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.CINNAMON_BUN)
@Composable
fun ContactPickerScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var contacts by remember { mutableStateOf(emptyList<Contact>()) }

    val pickContacts = rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        val uri = result.data?.data
        if (result.resultCode != Activity.RESULT_OK || uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            contacts = if (ContactPicker.isSystemPickerAvailable) {
                ContactPicker.readSession(context, uri)
            } else {
                ContactPicker.readLegacyPhone(context, uri)
            }
        }
    }

    ContactPickerContent(
        contacts = contacts,
        systemPickerAvailable = ContactPicker.isSystemPickerAvailable,
        onPickSingle = {
            pickContacts.launch(
                if (ContactPicker.isSystemPickerAvailable) ContactPicker.singleContactIntent()
                else ContactPicker.legacyPhoneIntent()
            )
        },
        onPickMultiple = { pickContacts.launch(ContactPicker.multipleContactsIntent()) },
        modifier = modifier
    )
}

@Composable
private fun ContactPickerContent(
    contacts: List<Contact>,
    systemPickerAvailable: Boolean,
    onPickSingle: () -> Unit,
    onPickMultiple: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Contact Picker", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = if (systemPickerAvailable) "Using the Android 17 system contact picker — no READ_CONTACTS needed."
            else "Android 17 picker unavailable — falling back to the legacy single phone picker.",
            style = MaterialTheme.typography.bodyMedium
        )
        Button(onClick = onPickSingle, modifier = Modifier.fillMaxWidth()) {
            Text("Pick a contact")
        }
        if (systemPickerAvailable) {
            OutlinedButton(onClick = onPickMultiple, modifier = Modifier.fillMaxWidth()) {
                Text("Pick up to ${ContactPicker.MAX_SELECTION} contacts")
            }
        }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(contacts, key = { it.lookupKey }) { contact ->
                ContactCard(contact)
            }
        }
    }
}

@Composable
private fun ContactCard(contact: Contact) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(contact.name.ifEmpty { "(No name)" }, style = MaterialTheme.typography.titleMedium)
            contact.phones.forEach { Text("📞 $it", style = MaterialTheme.typography.bodyMedium) }
            contact.emails.forEach { Text("✉️ $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ContactPickerPreview() {
    AndroidPracticeTheme {
        ContactPickerContent(
            contacts = listOf(
                Contact("1", "Ada Lovelace", listOf("ada@example.com"), listOf("+1 555 0100")),
                Contact("2", "Alan Turing", emptyList(), listOf("+44 20 7946 0000"))
            ),
            systemPickerAvailable = true,
            onPickSingle = {},
            onPickMultiple = {}
        )
    }
}
