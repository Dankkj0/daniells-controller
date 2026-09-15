package com.daniell.controller

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo

/**
 * Discovers and advertises daniell's controller receivers on the local Wi-Fi
 * using Android Network Service Discovery (mDNS/DNS-SD).
 */
class TvDiscovery(context: Context) {
    companion object {
        const val SERVICE_TYPE = "_daniells-controller._udp."
        const val SERVICE_NAME = "daniell's controller"
        const val DEFAULT_PORT = 4242
    }

    data class Tv(val name: String, val host: String, val port: Int)

    interface Listener {
        fun onTvFound(tv: Tv)
        fun onTvLost(name: String)
        fun onError(message: String)
    }

    private val nsd = context.applicationContext.getSystemService(Context.NSD_SERVICE) as NsdManager
    private var discoveryListener: NsdManager.DiscoveryListener? = null
    private var registrationListener: NsdManager.RegistrationListener? = null

    fun startDiscovery(listener: Listener) {
        stopDiscovery()
        val dl = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                if (serviceInfo.serviceType != SERVICE_TYPE) return
                nsd.resolveService(serviceInfo, object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) {
                        // A service can disappear while it is being resolved. Ignore it.
                    }

                    override fun onServiceResolved(info: NsdServiceInfo) {
                        val host = info.host?.hostAddress ?: return
                        val port = if (info.port > 0) info.port else DEFAULT_PORT
                        listener.onTvFound(Tv(info.serviceName, host, port))
                    }
                })
            }
            override fun onServiceLost(serviceInfo: NsdServiceInfo) {
                listener.onTvLost(serviceInfo.serviceName)
            }
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
                try { nsd.stopServiceDiscovery(this) } catch (_: Exception) { }
                listener.onError("Não foi possível procurar TVs (erro $errorCode).")
            }
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {
                listener.onError("Erro ao parar a descoberta (erro $errorCode).")
            }
        }
        discoveryListener = dl
        try {
            nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, dl)
        } catch (e: Exception) {
            discoveryListener = null
            listener.onError("Descoberta indisponível: ${e.message ?: "erro desconhecido"}")
        }
    }

    fun stopDiscovery() {
        discoveryListener?.let {
            try { nsd.stopServiceDiscovery(it) } catch (_: Exception) { }
        }
        discoveryListener = null
    }

    fun registerReceiver(port: Int, onReady: (String) -> Unit, onError: (String) -> Unit) {
        unregisterReceiver()
        val info = NsdServiceInfo().apply {
            serviceName = SERVICE_NAME
            serviceType = SERVICE_TYPE
            this.port = port
        }
        val rl = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(serviceInfo: NsdServiceInfo) {
                registrationListener = this
                onReady(serviceInfo.serviceName)
            }
            override fun onRegistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) {
                registrationListener = null
                onError("Não foi possível anunciar a TV (erro $errorCode).")
            }
            override fun onServiceUnregistered(serviceInfo: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
        }
        registrationListener = rl
        try {
            nsd.registerService(info, NsdManager.PROTOCOL_DNS_SD, rl)
        } catch (e: Exception) {
            registrationListener = null
            onError("Não foi possível anunciar a TV: ${e.message ?: "erro desconhecido"}")
        }
    }

    fun unregisterReceiver() {
        registrationListener?.let {
            try { nsd.unregisterService(it) } catch (_: Exception) { }
        }
        registrationListener = null
    }
}
