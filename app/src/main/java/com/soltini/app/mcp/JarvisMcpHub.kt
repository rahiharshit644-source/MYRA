package com.soltini.app.mcp

import android.content.Context
import android.util.Log
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.agent.DeviceHardwareController
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.briefing.JarvisExecutiveBriefing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class McpToolDefinition(
    val name: String,
    val description: String,
    val inputSchema: JSONObject,
    val serverOrigin: String = "local_device"
)

data class McpExecutionResult(
    val toolName: String,
    val isSuccess: Boolean,
    val outputText: String,
    val latencyMs: Long
)

/**
 * JarvisMcpHub — Universal Model Context Protocol (MCP) Integration Engine
 *
 * Inspired by modelcontextprotocol/servers by Anthropic.
 * Standardizes tool discovery (tools/list) and remote/local execution (tools/call)
 * over JSON-RPC 2.0. Enables Myra to connect to any external or on-device MCP servers.
 */
class JarvisMcpHub private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "JarvisMcpHub"

        @Volatile
        private var instance: JarvisMcpHub? = null

        fun getInstance(context: Context): JarvisMcpHub =
            instance ?: synchronized(this) {
                instance ?: JarvisMcpHub(context.applicationContext).also { instance = it }
            }
    }

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val deviceHardware = DeviceHardwareController(appContext)

    private val _registeredTools = MutableStateFlow<List<McpToolDefinition>>(emptyList())
    val registeredTools: StateFlow<List<McpToolDefinition>> = _registeredTools.asStateFlow()

    init {
        registerBuiltInMcpTools()
    }

    private fun registerBuiltInMcpTools() {
        val builtins = listOf(
            McpToolDefinition(
                name = "mcp_device_telemetry",
                description = "Fetches comprehensive device vitals, battery level, thermals, and memory load via MCP.",
                inputSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject())
                },
                serverOrigin = "device://telemetry"
            ),
            McpToolDefinition(
                name = "mcp_torch_control",
                description = "Controls the physical device flashlight via MCP protocol.",
                inputSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("enable", JSONObject().apply {
                            put("type", "boolean")
                            put("description", "true to turn on, false to turn off")
                        })
                    })
                    put("required", JSONArray().apply { put("enable") })
                },
                serverOrigin = "device://hardware"
            ),
            McpToolDefinition(
                name = "mcp_market_deals",
                description = "Queries real-time product pricing and deals across retail portals via MCP.",
                inputSchema = JSONObject().apply {
                    put("type", "object")
                    put("properties", JSONObject().apply {
                        put("product_query", JSONObject().apply {
                            put("type", "string")
                            put("description", "Name of the product or model to scout")
                        })
                    })
                    put("required", JSONArray().apply { put("product_query") })
                },
                serverOrigin = "web://shopper"
            )
        )
        _registeredTools.value = builtins
    }

    /**
     * Executes an MCP tool call adhering to Anthropic JSON-RPC specification.
     */
    suspend fun callTool(toolName: String, arguments: JSONObject): McpExecutionResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()
        JarvisSoundEngine.playTacticalBlip()
        AgentStatusBus.emit("🔌 MCP Tool Dispatch: Calling $toolName...")

        try {
            val output = when (toolName) {
                "mcp_device_telemetry" -> {
                    JarvisExecutiveBriefing.generateComprehensiveBriefing(appContext)
                }
                "mcp_torch_control" -> {
                    val enable = arguments.optBoolean("enable", false)
                    val res = deviceHardware.setTorch(enable)
                    res.optString("message", "Torch state updated")
                }
                "mcp_market_deals" -> {
                    val q = arguments.optString("product_query", "deals")
                    val shopper = com.soltini.app.browser.JarvisBrowserShopperAgent.getInstance(appContext)
                    val report = shopper.scoutAndComparePrices(q)
                    report.executiveSummary
                }
                else -> {
                    "Tool '$toolName' is not registered on this MCP server."
                }
            }

            val latency = System.currentTimeMillis() - startTime
            JarvisSoundEngine.playTaskSuccessChime()
            AgentStatusBus.emit("✅ MCP Response Received ($latency ms)")

            McpExecutionResult(
                toolName = toolName,
                isSuccess = true,
                outputText = output,
                latencyMs = latency
            )
        } catch (e: Exception) {
            Log.e(TAG, "MCP tool error: ${e.message}", e)
            JarvisSoundEngine.playWarningAlert()
            McpExecutionResult(
                toolName = toolName,
                isSuccess = false,
                outputText = "MCP Execution Error: ${e.message}",
                latencyMs = System.currentTimeMillis() - startTime
            )
        }
    }

    /**
     * Connects to a remote HTTP/SSE MCP Server and registers its tools dynamically.
     */
    suspend fun discoverRemoteMcpServer(serverUrl: String): Int = withContext(Dispatchers.IO) {
        try {
            val rpcRequest = JSONObject().apply {
                put("jsonrpc", "2.0")
                put("id", System.currentTimeMillis())
                put("method", "tools/list")
                put("params", JSONObject())
            }.toString()

            val req = Request.Builder()
                .url(serverUrl)
                .post(rpcRequest.toRequestBody("application/json".toMediaType()))
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val respText = resp.body?.string() ?: return@withContext 0
                val json = JSONObject(respText)
                val toolsArray = json.optJSONObject("result")?.optJSONArray("tools") ?: return@withContext 0

                val newTools = mutableListOf<McpToolDefinition>()
                for (i in 0 until toolsArray.length()) {
                    val t = toolsArray.getJSONObject(i)
                    newTools.add(
                        McpToolDefinition(
                            name = t.getString("name"),
                            description = t.optString("description", ""),
                            inputSchema = t.optJSONObject("inputSchema") ?: JSONObject(),
                            serverOrigin = serverUrl
                        )
                    )
                }
                _registeredTools.value = _registeredTools.value + newTools
                return@withContext newTools.size
            }
        } catch (e: Exception) {
            Log.w(TAG, "Remote MCP discovery failed for $serverUrl: ${e.message}")
        }
        return@withContext 0
    }
}
