package com.magic.calculator.feature.auth.domain.model

/**
 * Domain model representing an authenticated user, decoupled from any specific SDK.
 */
data class User(
    val uid: String,
    val email: String? = null,
    val displayName: String? = null,
    val phoneNumber: String? = null,
    val photoUrl: String? = null
)
