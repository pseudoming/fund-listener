package com.fundlistener.routes

import com.fundlistener.model.TradeRequest
import com.fundlistener.service.PositionService
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import org.koin.ktor.ext.inject

fun Route.positionRoutes() {
    val positionService by inject<PositionService>()

    route("/api/positions") {
        
        // GET /api/positions - 查询所有持仓
        get {
            val positions = positionService.getAllPositions()
            call.respond(positions)
        }

        // GET /api/positions/{code} - 查询单个持仓及其交易明细
        get("{code}") {
            val code = call.parameters["code"] ?: return@get call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing fund code"))
            val detail = positionService.getPositionDetail(code)
            if (detail != null) {
                call.respond(detail)
            } else {
                call.respond(HttpStatusCode.NotFound, mapOf("error" to "Position not found"))
            }
        }

        // POST /api/positions/{code}/buy - 买入
        post("{code}/buy") {
            val code = call.parameters["code"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing fund code"))
            val req = call.receive<TradeRequest>()
            val result = positionService.buy(code, req)
            call.respond(HttpStatusCode.Created, result)
        }

        // POST /api/positions/{code}/sell - 卖出
        post("{code}/sell") {
            val code = call.parameters["code"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing fund code"))
            val req = call.receive<TradeRequest>()
            val result = positionService.sell(code, req)
            call.respond(HttpStatusCode.OK, result)
        }

        // POST /api/positions/{code}/reset - 重置/覆盖持仓
        post("{code}/reset") {
            val code = call.parameters["code"] ?: return@post call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing fund code"))
            val req = call.receive<TradeRequest>()
            val result = positionService.reset(code, req)
            call.respond(HttpStatusCode.OK, result)
        }

        // DELETE /api/positions/{code} - 删除持仓
        delete("{code}") {
            val code = call.parameters["code"] ?: return@delete call.respond(HttpStatusCode.BadRequest, mapOf("error" to "Missing fund code"))
            positionService.deletePosition(code)
            call.respond(HttpStatusCode.NoContent)
        }
    }
}
