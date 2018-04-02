package io.ktor.client.engine.apache

import io.ktor.client.call.*
import io.ktor.client.engine.*
import io.ktor.client.request.*
import io.ktor.client.utils.*
import kotlinx.coroutines.experimental.*
import org.apache.http.impl.nio.client.*
import org.apache.http.impl.nio.reactor.*

private const val MAX_CONNECTIONS_COUNT = 1000
private const val IO_THREAD_COUNT_DEFAULT = 4

class ApacheEngine(private val config: ApacheEngineConfig) : HttpClientEngine {

    override suspend fun execute(call: HttpClientCall, data: HttpRequestData): HttpCallData {
        val request = ApacheHttpRequest(call, engine, config, dispatcher, data)
        val response = request.execute()

        return HttpCallData(request, response)
    }

    private val engine: CloseableHttpAsyncClient = prepareClient().apply { start() }
    private val dispatcher: CoroutineDispatcher = config.dispatcher ?: HTTP_CLIENT_DEFAULT_DISPATCHER

    override fun close() {
        try {
            engine.close()
        } catch (cause: Throwable) {
        }
    }

    private fun prepareClient(): CloseableHttpAsyncClient {
        val clientBuilder = HttpAsyncClients.custom()
        with(clientBuilder) {
            setThreadFactory { Thread(it, "Ktor-client-apache").apply { isDaemon = true } }
            disableAuthCaching()
            disableConnectionState()
            disableCookieManagement()
            setDefaultIOReactorConfig(IOReactorConfig.custom().apply {
                setMaxConnPerRoute(MAX_CONNECTIONS_COUNT)
                setMaxConnTotal(MAX_CONNECTIONS_COUNT)
                setIoThreadCount(IO_THREAD_COUNT_DEFAULT)
            }.build())
        }

        with(config) {
            clientBuilder.customClient()
        }

        config.sslContext?.let { clientBuilder.setSSLContext(it) }
        return clientBuilder.build()!!
    }
}
