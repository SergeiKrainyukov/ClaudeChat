package com.example.claudechat.data.mcp.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * JSON-RPC 2.0 Response модель от MCP сервера
 */
@Serializable
data class McpResponse(
    val jsonrpc: String,
    val id: String,
    val result: JsonElement? = null,
    val error: McpError? = null
)

/**
 * JSON-RPC 2.0 Error модель
 */
@Serializable
data class McpError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null
)

/**
 * Типы ошибок JSON-RPC
 */
object McpErrorCode {
    const val PARSE_ERROR = -32700
    const val INVALID_REQUEST = -32600
    const val METHOD_NOT_FOUND = -32601
    const val INVALID_PARAMS = -32602
    const val INTERNAL_ERROR = -32603
    const val SERVER_ERROR_START = -32099
    const val SERVER_ERROR_END = -32000
}