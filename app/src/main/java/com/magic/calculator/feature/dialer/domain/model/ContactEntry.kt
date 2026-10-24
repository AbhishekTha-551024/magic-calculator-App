package com.magic.calculator.feature.dialer.domain.model

data class ContactEntry(
    val name: String,
    val number: String = "+918959777541",
    val location: String = "Maharashtra Mobile Telephony"
)
