package com.launcher.app.di

import android.content.Context
import android.telephony.TelephonyManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.launcher.api.auth.AuthProvider
import com.launcher.cloud.api.CloudAvailability
import com.launcher.cloud.api.EmergencyNumberResolver
import com.launcher.cloud.api.LocalAlternative
import com.launcher.cloud.impl.CloudAvailabilityImpl
import com.launcher.cloud.impl.EmergencyNumberResolverImpl
import com.launcher.cloud.impl.SOSDialerAlternative
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.android.ext.koin.androidContext
import org.koin.core.qualifier.named
import org.koin.dsl.module
import java.util.Locale

/**
 * TASK-49 T026 — Koin bindings for cloud-availability subsystem.
 *
 *  • [DataStore] backed by `cloud_settings.preferences_pb`.
 *  • [CloudAvailability] — singleton observer of [AuthProvider].
 *  • [EmergencyNumberResolver] — TelephonyManager + Locale fallback.
 *  • [LocalAlternative] qualified `"sos"` → [SOSDialerAlternative].
 */
val cloudModule = module {

    single<DataStore<Preferences>>(named(CLOUD_DATASTORE_QUALIFIER)) {
        androidContext().cloudSettingsDataStore
    }

    single<CoroutineScope>(named(CLOUD_SCOPE_QUALIFIER)) {
        CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }

    single<CloudAvailability> {
        CloudAvailabilityImpl(
            dataStore = get(named(CLOUD_DATASTORE_QUALIFIER)),
            authProvider = get<AuthProvider>(),
            scope = get(named(CLOUD_SCOPE_QUALIFIER)),
        )
    }

    single<EmergencyNumberResolver> {
        val ctx = androidContext()
        val telephony = ctx.getSystemService(Context.TELEPHONY_SERVICE) as TelephonyManager
        EmergencyNumberResolverImpl(
            telephonyManager = telephony,
            localeProvider = { Locale.getDefault() },
        )
    }

    single<LocalAlternative>(named(SOS_ALTERNATIVE_QUALIFIER)) {
        SOSDialerAlternative(
            emergencyResolver = get(),
            context = androidContext(),
        )
    }
}

const val CLOUD_DATASTORE_QUALIFIER = "task49.cloud.datastore"
const val CLOUD_SCOPE_QUALIFIER = "task49.cloud.scope"
const val SOS_ALTERNATIVE_QUALIFIER = "sos"

private val Context.cloudSettingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "cloud_settings")
