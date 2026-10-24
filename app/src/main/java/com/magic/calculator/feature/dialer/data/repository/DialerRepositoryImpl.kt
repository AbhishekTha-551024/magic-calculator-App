package com.magic.calculator.feature.dialer.data.repository

import com.magic.calculator.feature.dialer.data.datasource.DialerLocalDataSource
import com.magic.calculator.feature.dialer.domain.model.CallLogEntry
import com.magic.calculator.feature.dialer.domain.model.ContactEntry
import com.magic.calculator.feature.dialer.domain.repository.DialerRepository

class DialerRepositoryImpl(
    private val localDataSource: DialerLocalDataSource
) : DialerRepository {

    override suspend fun getCallLogs(): List<CallLogEntry> {
        return localDataSource.getCallLogs()
    }

    override suspend fun getContacts(): List<ContactEntry> {
        return localDataSource.getContacts()
    }
}
