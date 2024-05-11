package org.cirjson.plugin.idea.schema.impl

class CirJsonComplianceCheckerOptions(val isCaseInsensitiveEnumCheck: Boolean, val isForceStrict: Boolean,
        val isReportMissingOptionalProperties: Boolean) {

    constructor(isCaseInsensitiveEnumCheck: Boolean) : this(isCaseInsensitiveEnumCheck, false)

    constructor(isCaseInsensitiveEnumCheck: Boolean, isForceStrict: Boolean) : this(isCaseInsensitiveEnumCheck,
            isForceStrict, false)

    fun withForceStrict(): CirJsonComplianceCheckerOptions {
        return CirJsonComplianceCheckerOptions(isCaseInsensitiveEnumCheck, true)
    }

    companion object {

        val RELAX_ENUM_CHECK = CirJsonComplianceCheckerOptions(isCaseInsensitiveEnumCheck = true, isForceStrict = false)

    }

}
