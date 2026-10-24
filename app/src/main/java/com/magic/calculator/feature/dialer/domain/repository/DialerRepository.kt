package com.magic.calculator.feature.dialer.domain.repository

import com.magic.calculator.feature.dialer.domain.model.CallLogEntry
import com.magic.calculator.feature.dialer.domain.model.ContactEntry

interface DialerRepository {
    suspend fun getCallLogs(): List<CallLogEntry>
    suspend fun getContacts(): List<ContactEntry>
}
