package com.m3u.dlna.control

import org.jupnp.controlpoint.SubscriptionCallback
import org.jupnp.model.gena.CancelReason
import org.jupnp.model.gena.GENASubscription
import org.jupnp.model.message.UpnpResponse
import org.jupnp.model.meta.Service
import org.jupnp.support.lastchange.LastChangeParser

internal class CastSubscriptionCallback(
    service: Service<*, *>?,
    requestedDurationSeconds: Int = 1800, // Cling default 1800
    private val lastChangeParser: LastChangeParser,
    private val callback: SubscriptionListener,
) : SubscriptionCallback(service, requestedDurationSeconds) {

    override fun failed(
        subscription: GENASubscription<*>,
        responseStatus: UpnpResponse?,
        exception: Exception?,
        defaultMsg: String?
    ) {
        executeInMainThread { callback.failed(subscription.subscriptionId) }
    }

    override fun established(subscription: GENASubscription<*>) {
        executeInMainThread { callback.established(subscription.subscriptionId) }
    }

    override fun ended(
        subscription: GENASubscription<*>,
        reason: CancelReason?,
        responseStatus: UpnpResponse?
    ) {
        executeInMainThread { callback.ended(subscription.subscriptionId) }
    }

    override fun eventsMissed(subscription: GENASubscription<*>, numberOfMissedEvents: Int) {
    }

    override fun eventReceived(subscription: GENASubscription<*>) {
        val lastChangeEventValue = subscription.currentValues["LastChange"]?.value?.toString()
        if (lastChangeEventValue.isNullOrBlank()) return
        try {
            val events =
                lastChangeParser.parse(lastChangeEventValue)?.instanceIDs?.firstOrNull()?.values
            events?.forEach { value ->
                executeInMainThread { callback.onReceived(subscription.subscriptionId, value) }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}