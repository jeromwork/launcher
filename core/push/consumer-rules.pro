# :core:push — consumer Proguard rules.
#
# Ktor HTTP client + kotlinx.serialization annotated DTOs (PushTriggerRequest,
# PushPayload) MUST survive R8 minification (TODO-ARCH-006). Without these
# rules R8 strips @Serializable companions → ClassNotFoundException at runtime.
#
# Spec 019 F-5c.

# kotlinx.serialization — preserve @Serializable types в family.push.*.
-keepclassmembers class family.push.** {
    *** Companion;
}
-keepclasseswithmembers class family.push.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor — engine selection + content negotiation rely on reflection.
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**
