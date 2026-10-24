package com.magic.calculator.feature.dialer.domain.usecase

import com.magic.calculator.feature.dialer.domain.model.ContactEntry
import com.magic.calculator.feature.dialer.domain.repository.DialerRepository

class GetContactsUseCase(
    private val repository: DialerRepository
) {
    suspend operator fun invoke(): List<ContactEntry> = repository.getContacts()
}
