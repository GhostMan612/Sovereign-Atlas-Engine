// ============================================================
// As Above, So Below. As Within, So Without.
// The Future Dictates the Past and the Past is Always Present.
// ============================================================

package com.sovereignatlas.atlas.core

data class AtlasRejection(val category: String, val message: String)

final class AtlasContractException(val rejection: AtlasRejection) :
    IllegalArgumentException(rejection.toString())

sealed interface AtlasValidation {
    val isValid: Boolean
    val rejection: AtlasRejection?

    data object Valid : AtlasValidation {
        override val isValid = true
        override val rejection: AtlasRejection? = null
    }

    data class Invalid(override val rejection: AtlasRejection) :
        AtlasValidation {
        override val isValid = false
    }

    companion object {
        fun valid(): AtlasValidation = Valid
        fun invalid(rejection: AtlasRejection): AtlasValidation =
            Invalid(rejection)
    }
}

data class AtlasId(val value: String) {
    override fun toString() = "AtlasId($value)"
}

object AtlasIds {
    fun check(value: String): AtlasValidation {
        if (value.isEmpty()) {
            return AtlasValidation.invalid(
                AtlasRejection(
                    "INVALID_IDENTITY",
                    "Identifier must not be empty.",
                ),
            )
        }
        return AtlasValidation.valid()
    }
}
