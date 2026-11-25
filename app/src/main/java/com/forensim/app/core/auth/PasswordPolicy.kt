package com.forensim.app.core.auth

/**
 * Strong-password rules for new accounts. Mirror these in Supabase
 * (Authentication → Providers → Email → minimum length 10 and "lowercase, uppercase, digits and
 * symbols") so the server enforces them too; client checks alone can be bypassed.
 */
object PasswordPolicy {
    const val MIN_LENGTH = 10

    data class Rule(val label: String, val test: (String) -> Boolean)

    val rules = listOf(
        Rule("At least $MIN_LENGTH characters") { it.length >= MIN_LENGTH },
        Rule("An uppercase letter (A-Z)") { p -> p.any { it.isUpperCase() } },
        Rule("A lowercase letter (a-z)") { p -> p.any { it.isLowerCase() } },
        Rule("A number (0-9)") { p -> p.any { it.isDigit() } },
        Rule("A special character, e.g. # @ ! \$ % & *") { p -> p.any { !it.isLetterOrDigit() && !it.isWhitespace() } },
    )

    enum class Strength(val label: String) { EMPTY(""), WEAK("Weak"), FAIR("Fair"), GOOD("Good"), STRONG("Strong") }

    fun unmet(password: String): List<Rule> = rules.filterNot { it.test(password) }

    fun isValid(password: String): Boolean = unmet(password).isEmpty() && password.none { it.isWhitespace() }

    fun strength(password: String): Strength {
        if (password.isEmpty()) return Strength.EMPTY
        val met = rules.count { it.test(password) }
        val bonus = if (password.length >= 14) 1 else 0
        return when (met + bonus) {
            in 0..2 -> Strength.WEAK
            3 -> Strength.FAIR
            4, 5 -> if (met == rules.size) Strength.GOOD else Strength.FAIR
            else -> Strength.STRONG
        }
    }
}
