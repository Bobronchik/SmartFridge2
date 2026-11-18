package com.example.smartfridge.camera

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

suspend fun discoverCamera(
    context: Context
): String? {

    return discoverService(
        context,
        "_smartfridge._tcp."
    ) ?: discoverService(
        context,
        "_http._tcp."
    )
}

private suspend fun discoverService(
    context: Context,
    serviceType: String
): String? {

    return withTimeoutOrNull(6000) {

        suspendCancellableCoroutine { continuation ->

            val nsd =
                context.getSystemService(
                    Context.NSD_SERVICE
                ) as NsdManager

            lateinit var listener:
                    NsdManager.DiscoveryListener

            var finished = false

            fun stop() {

                try {
                    nsd.stopServiceDiscovery(
                        listener
                    )
                } catch (_: Exception) {
                }
            }

            listener =
                object :
                    NsdManager.DiscoveryListener {

                    override fun onDiscoveryStarted(
                        serviceType: String
                    ) {
                    }

                    override fun onServiceFound(
                        serviceInfo: NsdServiceInfo
                    ) {

                        if (
                            finished ||
                            !continuation.isActive
                        ) {
                            return
                        }

                        nsd.resolveService(

                            serviceInfo,

                            object :
                                NsdManager.ResolveListener {

                                override fun onResolveFailed(
                                    serviceInfo: NsdServiceInfo,
                                    errorCode: Int
                                ) {
                                }

                                override fun onServiceResolved(
                                    info: NsdServiceInfo
                                ) {

                                    val ip =
                                        info.host
                                            ?.hostAddress

                                    if (
                                        ip != null &&
                                        continuation.isActive &&
                                        !finished
                                    ) {

                                        finished = true

                                        stop()

                                        continuation.resume(
                                            ip
                                        )
                                    }
                                }
                            }
                        )
                    }

                    override fun onServiceLost(
                        serviceInfo: NsdServiceInfo
                    ) {
                    }

                    override fun onDiscoveryStopped(
                        serviceType: String
                    ) {
                    }

                    override fun onStartDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int
                    ) {

                        stop()

                        if (
                            continuation.isActive
                        ) {

                            continuation.resume(null)
                        }
                    }

                    override fun onStopDiscoveryFailed(
                        serviceType: String,
                        errorCode: Int
                    ) {
                    }
                }

            try {

                nsd.discoverServices(
                    serviceType,
                    NsdManager.PROTOCOL_DNS_SD,
                    listener
                )

            } catch (_: Exception) {

                if (
                    continuation.isActive
                ) {
                    continuation.resume(null)
                }
            }

            continuation.invokeOnCancellation {
                stop()
            }
        }
    }
}
