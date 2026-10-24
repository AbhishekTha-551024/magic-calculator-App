package com.magic.calculator.feature.dialer.data.datasource

import android.content.Context
import android.provider.CallLog
import android.provider.ContactsContract
import com.magic.calculator.feature.dialer.domain.model.CallLogEntry
import com.magic.calculator.feature.dialer.domain.model.ContactEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

interface DialerLocalDataSource {
    suspend fun getCallLogs(): List<CallLogEntry>
    suspend fun getContacts(): List<ContactEntry>
}

class ContentProviderDialerLocalDataSource(
    private val context: Context
) : DialerLocalDataSource {

    override suspend fun getCallLogs(): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<CallLogEntry>()
        val dateFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        try {
            val cursor = context.contentResolver.query(
                CallLog.Calls.CONTENT_URI, null, null, null,
                CallLog.Calls.DATE + " DESC"
            )
            cursor?.use {
                val numIdx = it.getColumnIndex(CallLog.Calls.NUMBER)
                val typeIdx = it.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = it.getColumnIndex(CallLog.Calls.DATE)
                val nameIdx = it.getColumnIndex(CallLog.Calls.CACHED_NAME)

                var count = 0
                while (it.moveToNext() && count < 50) {
                    val type = it.getInt(typeIdx)
                    val typeStr = if (type == CallLog.Calls.MISSED_TYPE) "Missed" else "Incoming"
                    list.add(
                        CallLogEntry(
                            name = it.getString(nameIdx) ?: "",
                            number = it.getString(numIdx) ?: "",
                            type = typeStr,
                            date = dateFormat.format(Date(it.getLong(dateIdx)))
                        )
                    )
                    count++
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        if (list.isEmpty()) {
            list.add(CallLogEntry("Mom", "+91XXXXXXXXXX", "Incoming", "20:39"))
            list.add(CallLogEntry("Friend", "+91XXXXXXXXXX", "Missed", "19:51"))
            list.add(CallLogEntry("Boss", "+91XXXXXXXXXX", "Incoming", "18:12"))
            list.add(CallLogEntry("Sister", "+91XXXXXXXXXX", "Incoming", "14:32"))
            list.add(CallLogEntry("Uncle", "+91XXXXXXXXXX", "Missed", "11:20"))
        }

        list
    }

    override suspend fun getContacts(): List<ContactEntry> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ContactEntry>()
        val injectedList = listOf(
            ContactEntry("Mom", "+91XXXXXXXXXX", "Mobile Telephony"),
            ContactEntry("Friend", "+91XXXXXXXXXX", "Mobile Telephony"),
            ContactEntry("Boss", "+91XXXXXXXXXX", "Mobile Telephony"),
            ContactEntry("Sister", "+91XXXXXXXXXX", "Mobile Telephony"),
            ContactEntry("Uncle", "+91XXXXXXXXXX", "Mobile Telephony")
        )

        try {
            val cursor = context.contentResolver.query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI, null, null, null,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
            )
            cursor?.use {
                val nameIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                val numIdx = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                while (it.moveToNext()) {
                    val name = it.getString(nameIdx) ?: ""
                    val number = it.getString(numIdx) ?: "+91XXXXXXXXXX"
                    if (name.isNotEmpty()) {
                        list.add(ContactEntry(name, number, "Mobile Telephony"))
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        val existingNames = list.map { it.name.lowercase().trim() }.toSet()
        injectedList.forEach {
            if (!existingNames.contains(it.name.lowercase().trim())) {
                list.add(it)
            }
        }
        list.sortBy { it.name.lowercase() }

        list
    }
}
