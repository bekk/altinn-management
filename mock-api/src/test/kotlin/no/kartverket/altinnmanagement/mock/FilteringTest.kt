package no.kartverket.altinnmanagement.mock

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class FilteringTest {

    @Test
    fun `every caller gets access to the nrl resources and their scope`() = mockApi {
        val responses = listOf("matrikkel/matrikkel-api", "tilgangsstyring/demo").map { app ->
            client.post("/filtering") {
                contentType(ContentType.Application.Json)
                setBody("""{"input":{"headers":{"x-forwarded-client-cert":"URI=spiffe://cluster.local/ns/$app"}}}""")
            }
        }

        for (response in responses) {
            assertEquals(HttpStatusCode.OK, response.status)
            assertEquals(ContentType.Application.Json, response.contentType()?.withoutParameters())
            assertEquals(
                Json.parseToJsonElement(
                    """{"result":{"scopes":{"exact":[],"prefix":["kartverk:nrl."]},"resources":{"exact":[],"prefix":["nrl-"]}}}""",
                ),
                response.json(),
            )
        }
    }
}
