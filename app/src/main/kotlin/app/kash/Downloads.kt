package app.kash

import java.io.IOException
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A body bigger than what [download] was allowed. */
class TooBigException(message: String) : IOException(message)

/**
 * What [request] answers, its body of at most [maxBytes], all of it [within] that time: the request's own timeout covers the wait for the
 * headers only, and a server that sent them then stalled kept the reading (a plugin's installation, the hourly watch of the addresses) for good.
 * Past the time, the exchange is cancelled. **Blocks**: never on Swing's thread.
 */
fun HttpClient.download(request: HttpRequest, maxBytes: Long, within: Duration): HttpResponse<ByteArray> {
    val answer = sendAsync(request, HttpResponse.BodyHandlers.limiting(HttpResponse.BodyHandlers.ofByteArray(), maxBytes))
    return try {
        answer.get(within.toMillis(), TimeUnit.MILLISECONDS)
    } catch (e: TimeoutException) {
        answer.cancel(true)
        throw IOException("${request.uri().host} did not answer in ${within.toSeconds()} s")
    } catch (e: ExecutionException) {
        val cause = e.cause
        // BodyHandlers.limiting's: "body exceeds capacity: <maxBytes>".
        if (cause is IOException && cause.message.orEmpty().startsWith("body exceeds capacity")) throw TooBigException(cause.message.orEmpty())
        throw cause as? IOException ?: IOException(cause)
    }
}
