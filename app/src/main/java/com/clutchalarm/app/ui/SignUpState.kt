package com.clutchalarm.app.ui

import com.clutchalarm.app.auth.RegistrationFailure

internal fun validEmail(value: String) = value.trim().matches(Regex("[^\\s@]+@[^\\s@]+\\.[^\\s@]+"))

data class SignUpState(
    val email: String = "",
    val password: String = "",
    val confirmation: String = "",
    val busy: Boolean = false,
    val error: RegistrationFailure? = null,
) {
    val emailInvalid: Boolean get() = email.isNotEmpty() && !validEmail(email)
    val passwordInvalid: Boolean get() = password.isNotEmpty() && (password.length < 8 || password.isBlank())
    val confirmationInvalid: Boolean get() = confirmation.isNotEmpty() && confirmation != password
    val canSubmit: Boolean get() = !busy && validEmail(email) && password.length >= 8 && password.isNotBlank() && confirmation == password
}
