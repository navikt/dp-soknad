package no.nav.dagpenger.soknad

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import no.nav.dagpenger.soknad.utils.serder.objectMapper
import java.net.InetAddress

internal class LeaderElection(
    private val electorPath: String = Configuration.leaderElectorPath,
    private val httpClient: HttpClient = HttpClient(),
    private val thisPod: String? = InetAddress.getLocalHost().hostName
) {

    fun isLeader(): Boolean {
        val leader = runBlocking {
            httpClient.get("http://$electorPath/").bodyAsText().let(objectMapper::readTree).get("name").asText()
        }
        val isLeader = thisPod == leader
        logger.info("Current pod: $thisPod. Leader: $leader. Current pod is leader: $isLeader")
        return isLeader
    }

    companion object {
        private val logger = KotlinLogging.logger { }
    }
}
