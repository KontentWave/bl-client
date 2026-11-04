package com.example.myapplication.data

import com.example.myapplication.security.CanonicalPayloadFactory
import com.example.myapplication.security.EphemeralSignedRequestFactory
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test

class BackendLifecycleInteropTest {
    @Test fun actualPhpLifecyclePayloadAndVerifierAcceptJavaEcSignatures() {
        val backend = System.getenv("BLACKLIST_LIFECYCLE_BACKEND")
        assumeTrue("Explicit local WSL checkout required; no hosted requests", !backend.isNullOrBlank())
        val signer = EphemeralSignedRequestFactory()
        val requestId = "22222222-2222-4222-8222-222222222222"
        val enrollment = "11111111-1111-4111-8111-111111111111"
        val vectors = JsonArray()
        for ((id, due) in listOf(null to null, enrollment to null, enrollment to "2030-01-02T00:00:00+00:00")) {
            val signed = signer.createLifecycleRequest(requestId, id, due)
            vectors.add(JsonObject().apply {
                addProperty("operation", if (id == null) "auth.status" else "auth.renew")
                addProperty("requestid", requestId)
                addProperty("enrollmentid", id)
                addProperty("nextrenewalat", due)
                addProperty("publickey", signed.publicKey)
                addProperty("signature", signed.signature)
                addProperty("expected", if (id == null) CanonicalPayloadFactory.createLifecycleStatus(requestId, signed.publicKey)
                    else CanonicalPayloadFactory.createRenewal(id, requestId, due, signed.publicKey))
            })
        }
        val php = """
            require 'vendor/autoload.php';
            ${'$'}service = new App\Services\DeviceSignatureService;
            ${'$'}vectors = json_decode(stream_get_contents(STDIN), true, 512, JSON_THROW_ON_ERROR);
            ${'$'}results = [];
            foreach (${'$'}vectors as ${'$'}vector) {
                ${'$'}data = ['request_id' => ${'$'}vector['requestid'], 'public_key' => ${'$'}vector['publickey'],
                    'signature' => ${'$'}vector['signature']];
                if (${'$'}vector['operation'] === 'auth.renew') {
                    ${'$'}data['enrollment_id'] = ${'$'}vector['enrollmentid'];
                    ${'$'}data['next_renewal_at'] = ${'$'}vector['nextrenewalat'];
                }
                ${'$'}results[] = [
                    'payload' => ${'$'}service->lifecyclePayload(${'$'}vector['operation'], ${'$'}data),
                    'verified' => ${'$'}service->verifyLifecycle(${'$'}vector['operation'], ${'$'}data),
                ];
            }
            echo json_encode(${'$'}results, JSON_THROW_ON_ERROR);
        """.trimIndent()
        val process = ProcessBuilder("wsl.exe", "-d", "Ubuntu-22.04", "--cd", backend!!, "--exec", "php", "-r", php).start()
        try {
            process.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(vectors.toString()) }
            assertTrue("Local PHP verifier exceeded 20 seconds", process.waitFor(20, TimeUnit.SECONDS))
            val output = process.inputStream.bufferedReader().readText()
            val errors = process.errorStream.bufferedReader().readText()
            assertEquals("PHP verifier failed: $errors", 0, process.exitValue())
            val results = JsonParser.parseString(output).asJsonArray
            assertEquals(vectors.size(), results.size())
            for (index in 0 until vectors.size()) {
                assertEquals(vectors[index].asJsonObject.get("expected").asString,
                    results[index].asJsonObject.get("payload").asString)
                assertTrue(results[index].asJsonObject.get("verified").asBoolean)
            }
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }
}
