package com.magic.calculator.feature.dialer.domain.usecase

import com.magic.calculator.feature.dialer.domain.model.CallLogEntry
import com.magic.calculator.feature.dialer.domain.repository.DialerRepository

class GetCallLogsUseCase(
    private val repository: DialerRepository
) {
    suspend operator fun invoke(): List<CallLogEntry> = repository.getCallLogs()
}
