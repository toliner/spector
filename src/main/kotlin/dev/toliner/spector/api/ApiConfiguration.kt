package dev.toliner.spector.api

import dev.toliner.spector.model.*
import dev.toliner.spector.storage.TypeIndexer
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.server.application.*
import io.ktor.server.plugins.contentnegotiation.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory

private val logger = LoggerFactory.getLogger("ApiConfiguration")

/**
 * Configure the API server with all necessary plugins and routes.
 */
fun Application.configureApi(typeIndexer: TypeIndexer) {
    // Configure JSON serialization
    install(ContentNegotiation) {
        json(Json {
            prettyPrint = true
            ignoreUnknownKeys = true
        })
    }

    // Configure routes
    routing {
        // Health check endpoint
        get("/health") {
            call.respond(mapOf("status" to "ok"))
        }

        // Shutdown endpoint with localhost restriction
        post("/shutdown") {
            val remoteHost = call.request.local.remoteHost
            if (remoteHost !in listOf("127.0.0.1", "::1", "localhost")) {
                logger.warn("Shutdown attempt from non-localhost: $remoteHost")
                call.respond(HttpStatusCode.Forbidden, mapOf("error" to "Shutdown only allowed from localhost"))
                return@post
            }

            logger.info("Server shutdown initiated from $remoteHost")
            call.respond(mapOf("status" to "shutting down"))
        }

        // List classes in package
        get("/v1/packages/{packageName}/classes") {
            try {
                val packageName = call.parameters["packageName"]!!
                val recursive = call.request.queryParameters["recursive"]?.toBoolean() ?: false
                val publicOnly = call.request.queryParameters["publicOnly"]?.toBoolean() ?: true
                val limit = call.request.queryParameters["limit"]?.toIntOrNull()
                val offset = call.request.queryParameters["offset"]?.toIntOrNull() ?: 0

                val kindsParam = call.request.queryParameters["kinds"]
                val kinds = kindsParam?.split(",")
                    ?.mapNotNull { runCatching { ClassKind.valueOf(it.trim()) }.getOrNull() }
                    ?.toSet()

                val classes = typeIndexer.findClassesByPackage(
                    packageName = packageName,
                    recursive = recursive,
                    kinds = kinds,
                    publicOnly = publicOnly
                )

                val paginatedClasses = classes
                    .drop(offset)
                    .let { if (limit != null) it.take(limit) else it }

                val summaries = paginatedClasses.map { cls ->
                    ClassSummary(
                        fqcn = cls.fqcn,
                        kind = cls.kind,
                        modifiers = cls.modifiers.toList(),
                        kotlin = cls.kotlin?.let {
                            KotlinClassSummary(
                                isData = it.isData,
                                isValue = it.isValue
                            )
                        }
                    )
                }

                val hasMore = limit != null && classes.size > offset + limit

                call.respond(
                    ApiResponse.success(
                        ListClassesResponse(
                            packageName = packageName,
                            classes = summaries,
                            hasMore = hasMore
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Error listing classes", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<ListClassesResponse>("INTERNAL", e.message ?: "Unknown error")
                )
            }
        }

        // List subpackages of package
        get("/v1/packages/{packageName}/subpackages") {
            try {
                val packageName = call.parameters["packageName"]!!
                val subpackages = typeIndexer.findSubpackages(packageName)

                call.respond(
                    ApiResponse.success(
                        ListSubpackagesResponse(
                            packageName = packageName,
                            subpackages = subpackages
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Error listing subpackages", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<ListSubpackagesResponse>("INTERNAL", e.message ?: "Unknown error")
                )
            }
        }

        // List members of class
        get("/v1/classes/{fqcn}/members") {
            try {
                val fqcn = call.parameters["fqcn"]!!

                val classInfo = typeIndexer.findClassByFqcn(fqcn)
                if (classInfo == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error<ListMembersResponse>("NOT_FOUND", "Class not found: $fqcn")
                    )
                    return@get
                }

                val kindsParam = call.request.queryParameters["kinds"]
                val kinds = kindsParam?.split(",")
                    ?.mapNotNull { runCatching { MemberKind.valueOf(it.trim()) }.getOrNull() }
                    ?.toSet()

                val visibilityParam = call.request.queryParameters["visibility"]
                val visibilities = visibilityParam?.split(",")
                    ?.mapNotNull { runCatching { Visibility.valueOf(it.trim()) }.getOrNull() }
                    ?.toSet()

                val includeSynthetic = call.request.queryParameters["includeSynthetic"]?.toBoolean() ?: false

                val members = typeIndexer.findMembersByOwner(
                    ownerFqcn = fqcn,
                    kinds = kinds,
                    visibilities = visibilities,
                    includeSynthetic = includeSynthetic
                )

                val groupedMembers = MembersByKind(
                    properties = members.filterIsInstance<PropertyInfo>(),
                    methods = members.filterIsInstance<MethodInfo>(),
                    fields = members.filterIsInstance<FieldInfo>()
                )

                call.respond(
                    ApiResponse.success(
                        ListMembersResponse(
                            fqcn = fqcn,
                            members = groupedMembers
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Error listing members", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<ListMembersResponse>("INTERNAL", e.message ?: "Unknown error")
                )
            }
        }

        // Get member detail
        post("/v1/members/detail") {
            try {
                val request = call.receive<GetMemberDetailRequest>()

                val member = typeIndexer.findMemberBySignature(
                    ownerFqcn = request.ownerFqcn,
                    name = request.name,
                    jvmDesc = request.jvmDesc
                )

                if (member == null) {
                    call.respond(
                        HttpStatusCode.NotFound,
                        ApiResponse.error<GetMemberDetailResponse>(
                            "NOT_FOUND",
                            "Member not found: ${request.ownerFqcn}.${request.name}${request.jvmDesc}"
                        )
                    )
                    return@post
                }

                val memberKind = when (member) {
                    is FieldInfo -> MemberKind.FIELD
                    is MethodInfo -> if (member.isConstructor) MemberKind.CONSTRUCTOR else MemberKind.METHOD
                    is PropertyInfo -> MemberKind.PROPERTY
                }

                call.respond(
                    ApiResponse.success(
                        GetMemberDetailResponse(
                            ownerFqcn = member.ownerFqcn,
                            memberKind = memberKind,
                            member = member
                        )
                    )
                )
            } catch (e: Exception) {
                logger.error("Error getting member detail", e)
                call.respond(
                    HttpStatusCode.InternalServerError,
                    ApiResponse.error<GetMemberDetailResponse>("INTERNAL", e.message ?: "Unknown error")
                )
            }
        }
    }
}
