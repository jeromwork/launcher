package com.launcher.app.push

import family.keys.api.ConfigSaver
import family.keys.api.IdentityProof
import family.push.api.EventType
import family.push.api.PushHandler
import family.push.api.PushHandlerRegistry
import family.push.impl.DefaultPushHandlerRegistry
import org.koin.core.qualifier.named
import org.koin.dsl.module

/**
 * T130 (common) — Spec 019 F-5c. Bindings, общие для realBackend + mockBackend.
 *
 *  • [ConfigUpdatedHandler] — единственный handler в Phase 3. Future event types
 *    register additional handlers (один per EventType).
 *  • [PushHandlerRegistry] — Map-backed, populated с зарегистрированными
 *    handler'ами через qualified named bindings.
 *
 * Flavor-specific bindings — see `app/src/{realBackend,mockBackend}/.../F019PushBackendModule.kt`:
 *  • realBackend → HttpPushTrigger / FcmTokenPublisherImpl / PushTriggerConfigChangeNotifier.
 *  • mockBackend → NullPushTrigger / NoOpConfigChangeNotifier / NoOp publisher.
 */
val f019PushCommonModule = module {

    // ConfigUpdatedHandler — exposes как PushHandler qualified by "config-updated".
    single<PushHandler>(qualifier = named(EventType.ConfigUpdated.wireValue)) {
        ConfigUpdatedHandler(
            configSaver = get<ConfigSaver>(),
            currentUidSupplier = {
                get<IdentityProof>().currentIdentity()?.stableId?.takeIf { it.isNotEmpty() }
            },
        )
    }

    // Registry, populated с all registered PushHandler bindings.
    single<PushHandlerRegistry> {
        DefaultPushHandlerRegistry(
            initialHandlers = mapOf(
                EventType.ConfigUpdated to get<PushHandler>(
                    qualifier = named(EventType.ConfigUpdated.wireValue),
                ),
            ),
        )
    }
}
