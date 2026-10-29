package no.kartverket.altinnmanagement.client.support

import no.kartverket.altinnmanagement.client.http.JavaOutgoingHttpClient
import no.kartverket.altinnmanagement.client.http.OutgoingHttpClient
import java.net.http.HttpClient
import java.time.Duration

internal val testHttpClient: OutgoingHttpClient = JavaOutgoingHttpClient(HttpClient.newHttpClient(), Duration.ofSeconds(10))
