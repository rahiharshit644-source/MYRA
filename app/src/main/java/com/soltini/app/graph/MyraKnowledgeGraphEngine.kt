package com.soltini.app.graph

import android.content.Context
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class GraphEntity(
    val id: String,
    val name: String,
    val type: String, // PERSON, PLACE, ROUTINE, DEVICE, PREFERENCE, ACCOUNT
    val aliases: List<String> = emptyList(),
    val attributes: Map<String, String> = emptyMap()
)

data class GraphRelation(
    val sourceId: String,
    val targetId: String,
    val relation: String, // MOTHER_OF, FRIEND_OF, WORKS_AT, PREFERS, OWNS, SCHEDULED_AT
    val confidence: Float = 0.95f
)

/**
 * MyraKnowledgeGraphEngine — Personal Knowledge Graph & Semantic Edge Matrix
 *
 * Inspired by mem0ai/mem0 & microsoft/graphrag.
 * Maintains an interconnected relational graph of Boss's life:
 *  - People & Relationships (Mummy, Papa, Rahul, Colleagues)
 *  - Aliases & Phone Numbers / UPI Handles
 *  - Daily Habits & Routine timings
 *  - Preferences (Music genres, travel mode, food preferences)
 *
 * Traverses 1-hop and 2-hop entity relations so Myra understands context seamlessly
 * without having to ask "Kisko call karna hai?" or "Kaha jana hai?".
 */
class MyraKnowledgeGraphEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "KnowledgeGraph"
        private const val GRAPH_FILE_NAME = "myra_knowledge_graph.json"

        @Volatile
        private var instance: MyraKnowledgeGraphEngine? = null

        fun getInstance(context: Context): MyraKnowledgeGraphEngine =
            instance ?: synchronized(this) {
                instance ?: MyraKnowledgeGraphEngine(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val graphFile = File(appContext.filesDir, GRAPH_FILE_NAME)

    private val entities = mutableMapOf<String, GraphEntity>()
    private val relations = mutableListOf<GraphRelation>()

    private val _nodeCount = MutableStateFlow(0)
    val nodeCount: StateFlow<Int> = _nodeCount.asStateFlow()

    init {
        loadGraph()
    }

    @Synchronized
    private fun loadGraph() {
        if (!graphFile.exists()) {
            seedDefaultKnowledge()
            saveGraph()
            return
        }

        try {
            val json = JSONObject(graphFile.readText())
            val entArr = json.optJSONArray("entities") ?: JSONArray()
            entities.clear()
            for (i in 0 until entArr.length()) {
                val obj = entArr.getJSONObject(i)
                val id = obj.getString("id")
                val name = obj.getString("name")
                val type = obj.optString("type", "GENERAL")
                val aliasArr = obj.optJSONArray("aliases") ?: JSONArray()
                val aliases = (0 until aliasArr.length()).map { aliasArr.getString(it) }
                val attrObj = obj.optJSONObject("attributes") ?: JSONObject()
                val attrs = mutableMapOf<String, String>()
                attrObj.keys().forEach { k -> attrs[k] = attrObj.getString(k) }

                entities[id] = GraphEntity(id, name, type, aliases, attrs)
            }

            val relArr = json.optJSONArray("relations") ?: JSONArray()
            relations.clear()
            for (i in 0 until relArr.length()) {
                val obj = relArr.getJSONObject(i)
                relations.add(
                    GraphRelation(
                        sourceId = obj.getString("sourceId"),
                        targetId = obj.getString("targetId"),
                        relation = obj.getString("relation"),
                        confidence = obj.optDouble("confidence", 0.95).toFloat()
                    )
                )
            }
            _nodeCount.value = entities.size
            Log.i(TAG, "Knowledge Graph loaded: ${entities.size} entities, ${relations.size} edges")
        } catch (e: Exception) {
            Log.w(TAG, "Error loading knowledge graph, reseeding: ${e.message}")
            seedDefaultKnowledge()
            saveGraph()
        }
    }

    private fun seedDefaultKnowledge() {
        entities.clear()
        relations.clear()

        // Core Boss Identity
        addEntity(
            GraphEntity(
                id = "boss",
                name = "Harshit Raahi",
                type = "PERSON",
                aliases = listOf("boss", "harshit", "creator", "sir", "me"),
                attributes = mapOf(
                    "role" to "Architect & Creator",
                    "assistant" to "Myra",
                    "favorite_tone" to "Tactical J.A.R.V.I.S. Hinglish"
                )
            )
        )

        // Myra Self Identity
        addEntity(
            GraphEntity(
                id = "myra",
                name = "Myra",
                type = "AI_COMPANION",
                aliases = listOf("myra", "jarvis", "edith"),
                attributes = mapOf(
                    "protocol_version" to "v2.5 Tactical",
                    "directive" to "Absolute loyalty and autonomous device command"
                )
            )
        )

        addRelation("boss", "myra", "CREATOR_OF")
        addRelation("myra", "boss", "ASSISTANT_TO")

        _nodeCount.value = entities.size
    }

    @Synchronized
    fun saveGraph() {
        scope.launch {
            try {
                val json = JSONObject()
                val entArr = JSONArray()
                entities.values.forEach { ent ->
                    val obj = JSONObject().apply {
                        put("id", ent.id)
                        put("name", ent.name)
                        put("type", ent.type)
                        put("aliases", JSONArray(ent.aliases))
                        put("attributes", JSONObject(ent.attributes))
                    }
                    entArr.put(obj)
                }
                json.put("entities", entArr)

                val relArr = JSONArray()
                relations.forEach { rel ->
                    val obj = JSONObject().apply {
                        put("sourceId", rel.sourceId)
                        put("targetId", rel.targetId)
                        put("relation", rel.relation)
                        put("confidence", rel.confidence.toDouble())
                    }
                    relArr.put(obj)
                }
                json.put("relations", relArr)

                graphFile.writeText(json.toString(2))
                _nodeCount.value = entities.size
            } catch (e: Exception) {
                Log.e(TAG, "Failed to persist knowledge graph: ${e.message}", e)
            }
        }
    }

    @Synchronized
    fun addEntity(entity: GraphEntity) {
        entities[entity.id] = entity
        _nodeCount.value = entities.size
        saveGraph()
    }

    @Synchronized
    fun addRelation(sourceId: String, targetId: String, relation: String, confidence: Float = 0.95f) {
        val existing = relations.firstOrNull { it.sourceId == sourceId && it.targetId == targetId && it.relation == relation }
        if (existing == null) {
            relations.add(GraphRelation(sourceId, targetId, relation, confidence))
            saveGraph()
        }
    }

    /**
     * Resolves an entity query by name or alias (e.g., "mummy", "rahul", "boss").
     */
    @Synchronized
    fun resolveEntity(query: String): GraphEntity? {
        val clean = query.trim().lowercase()
        return entities.values.firstOrNull { ent ->
            ent.name.lowercase() == clean ||
            ent.aliases.any { it.lowercase() == clean || clean.contains(it.lowercase()) }
        }
    }

    /**
     * Retrieves related facts connected to an entity up to 2 hops.
     * e.g., for query "mummy": returns relations and linked entities.
     */
    @Synchronized
    fun getRelationalContext(query: String): String {
        val target = resolveEntity(query) ?: return ""
        val sb = StringBuilder()
        sb.append("ENTITY: ${target.name} (${target.type})\n")
        target.attributes.forEach { (k, v) -> sb.append(" - $k: $v\n") }

        val connectedEdges = relations.filter { it.sourceId == target.id || it.targetId == target.id }
        if (connectedEdges.isNotEmpty()) {
            sb.append("RELATIONS:\n")
            for (edge in connectedEdges) {
                val otherId = if (edge.sourceId == target.id) edge.targetId else edge.sourceId
                val other = entities[otherId]?.name ?: otherId
                val direction = if (edge.sourceId == target.id) "--> [${edge.relation}] --> $other" else "<-- [${edge.relation}] <-- $other"
                sb.append(" - ${target.name} $direction\n")
            }
        }
        return sb.toString().trim()
    }

    /**
     * Dumps complete active knowledge matrix for prompt augmentation.
     */
    @Synchronized
    fun buildGraphSummaryPrompt(): String {
        if (entities.isEmpty()) return ""
        val sb = StringBuilder()
        sb.append("\n## MYRA KNOWLEDGE GRAPH (EDGE GRAPH-RAG MATRIX)\n")
        entities.values.take(15).forEach { ent ->
            sb.append("• ${ent.name} (${ent.type})")
            if (ent.aliases.isNotEmpty()) sb.append(" [aliases: ${ent.aliases.joinToString()}]")
            val rels = relations.filter { it.sourceId == ent.id }.map { "${it.relation} ${entities[it.targetId]?.name ?: it.targetId}" }
            if (rels.isNotEmpty()) sb.append(" | Links: ${rels.joinToString("; ")}")
            sb.append("\n")
        }
        return sb.toString()
    }
}
