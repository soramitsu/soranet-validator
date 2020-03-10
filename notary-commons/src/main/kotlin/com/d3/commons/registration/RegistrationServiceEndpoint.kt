/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.commons.registration

import com.d3.commons.model.NotaryException
import com.d3.commons.model.NotaryExceptionErrorCode
import com.d3.commons.model.NotaryGenericResponse
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.request.receive
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mu.KLogging

data class MappingRegistrationResponse(val responseMap: Map<*, *>) : NotaryGenericResponse()
data class MessagedRegistrationResponse(val message: String) : NotaryGenericResponse()

/**
 * Registration HTTP service
 */
class RegistrationServiceEndpoint(
    port: Int,
    private val registrationStrategy: RegistrationStrategy,
    private val domain: String?
) {
    constructor(port: Int, registrationStrategy: RegistrationStrategy) : this(
        port,
        registrationStrategy,
        null
    )

    init {
        logger.info { "Start registration server on port $port" }

        val server = embeddedServer(Netty, port = port) {
            install(CORS)
            {
                anyHost()
                allowCredentials = true
            }
            install(ContentNegotiation) {
                gson()
            }
            install(StatusPages) {
                exception<NotaryException> { cause ->
                    call.respond(NotaryGenericResponse(cause.code, cause.message ?: ""))
                }
            }
            routing {
                post("/users") {
                    val parameters = call.receiveParameters()
                    val name = parameters["name"]
                    val pubkey = parameters["pubkey"]
                    val domain = determineDomain(domain, parameters["domain"])

                    val response = invokeRegistration(name, domain, pubkey)
                    call.respond(response)
                }

                post("/users/json") {
                    val body = call.receive(UserDto::class)
                    val name = body.name
                    val pubkey = body.pubkey
                    val domain = determineDomain(domain, body.domain)

                    val response = invokeRegistration(name, domain, pubkey)
                    call.respond(response)
                }

                get("free-addresses/number") {
                    val response = onGetFreeAddressesNumber()
                    call.respond(response)
                }

                get("/actuator/health") {
                    call.respond(
                        mapOf(
                            "status" to "UP"
                        )
                    )
                }
            }
        }
        server.start(wait = false)
    }

    private fun determineDomain(vararg candidates: String?) =
        try {
            candidates.first { domainCandidate -> !domainCandidate.isNullOrBlank() }!!
        } catch (e: NoSuchElementException) {
            logger.error("All passed domains were invalid: $candidates", e)
            throw IllegalArgumentException("All passed domains were invalid: $candidates", e)
        }

    private fun invokeRegistration(
        name: String?,
        domain: String?,
        pubkey: String?
    ): MappingRegistrationResponse {
        logger.info { "Registration invoked with parameters (name = \"$name\", domain = \"$domain\", pubkey = \"$pubkey\"" }
        return onPostRegistration(name, domain, pubkey)
    }

    private fun onPostRegistration(
        name: String?,
        domain: String?,
        pubkey: String?
    ): MappingRegistrationResponse {
        var reason = ""
        if (name.isNullOrEmpty()) reason = reason.plus("Parameter \"name\" is not specified. ")
        if (domain.isNullOrEmpty()) reason = reason.plus("Parameter \"domain\" is not specified. ")
        if (pubkey == null || pubkey.length != 32) reason = reason.plus("Parameter \"pubkey\" is invalid.")

        if (reason.isNotEmpty()) {
            throw NotaryException(NotaryExceptionErrorCode.WRONG_INPUT, reason)
        }
        registrationStrategy.register(name!!, domain!!, pubkey!!).fold(
            { address ->
                logger.info {
                    "Client $name@$domain was successfully registered with address $address"
                }
                val response = mapOf("clientId" to address)
                return MappingRegistrationResponse(response)
            },
            { ex ->
                logger.error("Cannot register client $name", ex)
                throw ex
            })
    }

    private fun onGetFreeAddressesNumber(): MessagedRegistrationResponse {
        return registrationStrategy.getFreeAddressNumber()
            .fold(
                {
                    MessagedRegistrationResponse(it.toString())
                },
                {
                    throw it
                }
            )
    }

    /**
     * Logger
     */
    companion object : KLogging()
}

data class UserDto(
    val name: String,
    val pubkey: String,
    val domain: String?
)
