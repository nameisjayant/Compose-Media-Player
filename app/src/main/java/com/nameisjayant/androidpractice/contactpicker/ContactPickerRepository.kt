package com.nameisjayant.androidpractice.contactpicker

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsPickerSessionContract
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Contact(
    val lookupKey: String, val name: String, val emails: List<String>, val phones: List<String>
)

object ContactPicker {

    const val MAX_SELECTION = 5

    val isSystemPickerAvailable: Boolean
        get() = Build.VERSION.SDK_INT >= 37

    // Only ask for the data we actually show — the picker filters contacts by these MIME types.
    private val requestedFields = arrayListOf(
        Email.CONTENT_ITEM_TYPE,
        Phone.CONTENT_ITEM_TYPE,
    )

    @RequiresApi(37)
    fun singleContactIntent(): Intent =
        Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS).apply {
            putExtra(Intent.EXTRA_USE_SYSTEM_CONTACTS_PICKER, true)
            putStringArrayListExtra(
                ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                requestedFields
            )
        }

    @RequiresApi(37)
    fun multipleContactsIntent(limit: Int = MAX_SELECTION): Intent =
        Intent(ContactsPickerSessionContract.ACTION_PICK_CONTACTS).apply {
            putExtra(Intent.EXTRA_USE_SYSTEM_CONTACTS_PICKER, true)
            putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            putExtra(ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_SELECTION_LIMIT, limit)
            putStringArrayListExtra(
                ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_REQUESTED_DATA_FIELDS,
                requestedFields
            )
            // false = a contact qualifies if it has ANY of the requested fields
            putExtra(ContactsPickerSessionContract.EXTRA_PICK_CONTACTS_MATCH_ALL_DATA_FIELDS, false)
        }

    // Pre-Android 17 fallback: the legacy picker grants one-shot access to a single phone row.
    fun legacyPhoneIntent(): Intent = Intent(Intent.ACTION_PICK).setType(Phone.CONTENT_TYPE)

    /**
     * Reads the session URI returned by the Android 17 picker. Access is temporary,
     * so callers should persist anything they need right away.
     */
    suspend fun readSession(context: Context, sessionUri: Uri): List<Contact> =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                ContactsContract.Contacts.LOOKUP_KEY,
                ContactsContract.Contacts.DISPLAY_NAME_PRIMARY,
                ContactsContract.Data.MIMETYPE,
                ContactsContract.Data.DATA1,
            )
            val contacts = linkedMapOf<String, Contact>()

            context.contentResolver.query(sessionUri, projection, null, null, null)?.use { cursor ->
                val lookupKeyIdx = cursor.getColumnIndex(ContactsContract.Contacts.LOOKUP_KEY)
                val nameIdx = cursor.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME_PRIMARY)
                val mimeTypeIdx = cursor.getColumnIndex(ContactsContract.Data.MIMETYPE)
                val data1Idx = cursor.getColumnIndex(ContactsContract.Data.DATA1)

                // One row per data item — group rows back into contacts by lookup key.
                while (cursor.moveToNext()) {
                    val lookupKey = cursor.getString(lookupKeyIdx) ?: continue
                    val mimeType = cursor.getString(mimeTypeIdx)
                    val data1 = cursor.getString(data1Idx).orEmpty()
                    val existing = contacts[lookupKey] ?: Contact(
                        lookupKey = lookupKey,
                        name = cursor.getString(nameIdx).orEmpty(),
                        emails = emptyList(),
                        phones = emptyList()
                    )
                    contacts[lookupKey] = when (mimeType) {
                        Email.CONTENT_ITEM_TYPE -> existing.copy(emails = existing.emails + data1)
                        Phone.CONTENT_ITEM_TYPE -> existing.copy(phones = existing.phones + data1)
                        else -> existing
                    }
                }
            }
            contacts.values.toList()
        }

    suspend fun readLegacyPhone(context: Context, phoneUri: Uri): List<Contact> =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(Phone.LOOKUP_KEY, Phone.DISPLAY_NAME, Phone.NUMBER)
            context.contentResolver.query(phoneUri, projection, null, null, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use emptyList()
                listOf(
                    Contact(
                        lookupKey = cursor.getString(0).orEmpty(),
                        name = cursor.getString(1).orEmpty(),
                        emails = emptyList(),
                        phones = listOfNotNull(cursor.getString(2))
                    )
                )
            } ?: emptyList()
        }
}
