/*
 * Copyright D3 Ledger, Inc. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0
 */

package com.d3.commons.registration

import com.d3.commons.model.NotaryException
import com.d3.commons.model.NotaryExceptionErrorCode
import com.d3.commons.model.NotaryGenericResponse
import com.d3.commons.model.OldNotaryException
import com.d3.commons.util.GsonInstance
import io.ktor.application.call
import io.ktor.application.install
import io.ktor.features.CORS
import io.ktor.features.ContentNegotiation
import io.ktor.features.StatusPages
import io.ktor.gson.gson
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.request.receive
import io.ktor.request.receiveParameters
import io.ktor.response.respond
import io.ktor.response.respondText
import io.ktor.routing.get
import io.ktor.routing.post
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import mu.KLogging

data class Response(val code: HttpStatusCode, val message: String)
data class MappingRegistrationResponse(val responseMap: Map<*, *>) : NotaryGenericResponse()

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
                exception<OldNotaryException> { cause ->
                    call.respondText(
                        text = cause.message ?: "",
                        status = HttpStatusCode.BadRequest,
                        contentType = ContentType.Application.Json
                    )
                }
            }
            routing {
                post("/users") {
                    val parameters = call.receiveParameters()
                    val response = invokeRegistrationFromParameters(parameters, false) as Response
                    call.respondText(
                        response.message,
                        status = response.code,
                        contentType = ContentType.Application.Json
                    )
                }

                post("/users/json") {
                    val body = call.receive(UserDto::class)
                    val response = invokeRegistrationFromDto(body, false) as Response
                    call.respondText(
                        response.message,
                        status = response.code,
                        contentType = ContentType.Application.Json
                    )
                }

                post("$V1/users") {
                    val parameters = call.receiveParameters()
                    val response = invokeRegistrationFromParameters(parameters, true)
                    call.respond(response)
                }

                post("$V1/users/json") {
                    val body = call.receive(UserDto::class)
                    val response = invokeRegistrationFromDto(body, true)
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

    private fun invokeRegistrationFromParameters(parameters: Parameters, isNew: Boolean): Any {
        val name = parameters["name"]
        val pubkey = parameters["pubkey"]
        val domain = determineDomain(domain, parameters["domain"])
        return invokeRegistration(name, domain, pubkey, isNew)
    }

    private fun invokeRegistrationFromDto(dto: UserDto, isNew: Boolean): Any {
        val name = dto.name
        val pubkey = dto.pubkey
        val domain = determineDomain(domain, dto.domain)
        return invokeRegistration(name, domain, pubkey, isNew)
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
        pubkey: String?,
        isNew: Boolean
    ): Any {
        logger.info { "Registration invoked with parameters (name = \"$name\", domain = \"$domain\", pubkey = \"$pubkey\"" }
        check(name, domain, pubkey, isNew)
        return onPostRegistration(name!!, domain!!, pubkey!!, isNew)
    }

    private fun check(name: String?, domain: String?, pubkey: String?, isNew: Boolean) {
        val reason = validateInputs(name, domain, pubkey)
        if (reason.isNotEmpty()) {
            if (isNew) {
                throw NotaryException(NotaryExceptionErrorCode.WRONG_INPUT, reason)
            } else {
                throw OldNotaryException(reason)
            }
        }
    }

    private fun validateInputs(name: String?, domain: String?, pubkey: String?): String {
        var reason = ""
        if (name.isNullOrEmpty()) reason = reason.plus("Parameter \"name\" is not specified. ")
        if (domain.isNullOrEmpty()) reason = reason.plus("Parameter \"domain\" is not specified. ")
        if (pubkey == null || pubkey.length != 64) reason = reason.plus("Parameter \"pubkey\" is invalid.")
        return reason
    }

    private fun onPostRegistration(
        name: String,
        domain: String,
        pubkey: String,
        isNew: Boolean
    ): Any {
        registrationStrategy.register(name, domain, pubkey).fold(
            { address ->
                logger.info {
                    "Client $name@$domain was successfully registered with address $address"
                }
                val response = mapOf(CLIENT_ID to address)
                return if (isNew) MappingRegistrationResponse(response)
                else Response(HttpStatusCode.OK, GsonInstance.get().toJson(response))
            },
            { ex ->
                logger.error("Cannot register client $name", ex)
                if (!isNew && ex is NotaryException) {
                    return Response(
                        HttpStatusCode.OK,
                        GsonInstance.get().toJson(mapOf(CLIENT_ID to "$name@$domain"))
                    )
                }
                throw ex
            })
    }

    /**
     * Logger
     */
    companion object : KLogging() {
        const val CLIENT_ID = "clientId"
        const val V1 = "/v1"
    }
}

data class UserDto(
    val name: String,
    val pubkey: String,
    val domain: String?
)
