package com.ai.assistance.operit.data.repository

import android.content.Context
import androidx.compose.ui.graphics.Color
import com.ai.assistance.operit.data.db.ObjectBoxManager
import com.ai.assistance.operit.data.model.Memory
import com.ai.assistance.operit.data.model.MemoryLink
import com.ai.assistance.operit.data.model.MemoryTag
import com.ai.assistance.operit.data.model.MemoryTag_
import com.ai.assistance.operit.data.model.Memory_
import com.ai.assistance.operit.data.model.DocumentChunk
import com.ai.assistance.operit.data.model.DocumentChunk_
import com.ai.assistance.operit.data.model.Embedding
import com.ai.assistance.operit.data.model.ChunkReference
import com.ai.assistance.operit.services.OnnxEmbeddingService
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Edge
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Graph
import com.ai.assistance.operit.ui.features.memory.screens.graph.model.Node
import com.ai.assistance.operit.util.vector.IndexItem
import com.ai.assistance.operit.util.vector.VectorIndexManager
import io.objectbox.Box
import io.objectbox.kotlin.boxFor
import io.objectbox.kotlin.query
import io.objectbox.query.QueryBuilder
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.objectbox.query.QueryCondition
import java.io.IOException
import java.util.UUID

/**
 * Repository for handling Memory data operations. It abstracts the data source (ObjectBox) from the
 * rest of the application.
 */
class MemoryRepository(private val context: Context, profileId: String) {

    companion object {
        /** Represents a strong link, e.g., "A is a B". */
        const val STRONG_LINK = 1.0f

        /** Represents a medium-strength link, e.g., "A is related to B". */
        const val MEDIUM_LINK = 0.7f

        /** Represents a weak link, e.g., "A is sometimes associated with B". */
        const val WEAK_LINK = 0.3f
    }

    private val store = ObjectBoxManager.get(context, profileId)
    private val memoryBox: Box<Memory> = store.boxFor()
    private val tagBox = store.boxFor<MemoryTag>()
    private val linkBox = store.boxFor<MemoryLink>()
    private val chunkBox = store.boxFor<DocumentChunk>()
    
    // --- HNSW向量索引集成 ---
    private val vectorIndexManager: VectorIndexManager<IndexItem<Memory>, String> by lazy {
        val indexFile = File(context.filesDir, "memory_hnsw_${profileId}.idx")
        
        // 检查是否有旧的100维向量，如果有则删除旧索引
        val hasOldEmbeddings = memoryBox.all.any { it.embedding != null && it.embedding!!.vector.size == 100 }
        if (hasOldEmbeddings && indexFile.exists()) {
            android.util.Log.w("MemoryRepo", "Detected old 100-dim embeddings, deleting old index file")
            indexFile.delete()
        }
        
        val manager =
                VectorIndexManager<IndexItem<Memory>, String>(
                        dimensions = 384, // ONNX模型的embedding维度为384
                        maxElements = 100_000,
                        indexFile = indexFile
                )
        manager.initIndex()
        // 首次构建索引 - 只添加384维的向量
        memoryBox.all.filter { it.embedding != null && it.embedding!!.vector.size == 384 }.forEach { memory ->
            manager.addItem(IndexItem(memory.uuid, memory.embedding!!.vector, memory))
        }
        manager
    }
    
    /**
     * 从外部文档创建记忆。
     * @param title 文档记忆的标题。
     * @param filePath 文档的路径。
     * @param fileContent 文档的文本内容。
     * @param folderPath 文件夹路径。
     * @return 创建的Memory对象。
     */
    suspend fun createMemoryFromDocument(documentName: String, originalPath: String, text: String, folderPath: String = ""): Memory = withContext(Dispatchers.IO) {
        // 1. 为文档本身生成嵌入
        val documentEmbedding = OnnxEmbeddingService.generateEmbedding(documentName)?.vector ?: FloatArray(384)

        // 2. 创建一个初始的Memory对象并立即保存以获得ID
        val documentMemory = Memory(
            title = documentName,
            content = "这是一个文档节点，包含了文件 '${documentName}' 的内容。",
            uuid = UUID.randomUUID().toString()
        ).apply {
            this.embedding = Embedding(documentEmbedding)
            this.isDocumentNode = true
            this.documentPath = originalPath
            this.folderPath = folderPath
        }
        memoryBox.put(documentMemory)

        // 3. 为文档块创建专用的HNSW索引，确保从干净的状态开始
        val indexFile = context.getFileStreamPath("doc_index_${documentMemory.id}.hnsw")
        if (indexFile.exists()) {
            indexFile.delete()
        }
        documentMemory.chunkIndexFilePath = indexFile.absolutePath
        val chunkIndexManager = VectorIndexManager<IndexItem<ChunkReference>, String>(
            dimensions = 384, // ONNX模型的embedding维度
            maxElements = 20000,
            indexFile = indexFile
        )

        // 4. 分割、清洗和处理文本块
        val chunks = text.split(Regex("(\\r?\\n[\\t ]*){2,}"))
            .mapNotNull { chunkText ->
                val cleanedText = chunkText.replace(Regex("(?m)^[\\*\\-=_]{3,}\\s*$"), "").trim()
                if (cleanedText.isNotBlank()) {
                    DocumentChunk(content = cleanedText, chunkIndex = 0) // chunkIndex will be set later
                } else {
                    null
                }
            }.mapIndexed { index, chunk ->
                chunk.apply { this.chunkIndex = index }
            }

        // 5. 为所有块生成嵌入并添加到索引和数据库
        if (chunks.isNotEmpty()) {
            // 首先，将所有块链接到父级Memory
            chunks.forEach { it.memory.target = documentMemory }
            // 其次，将chunks存入数据库以获取它们的永久ID
            chunkBox.put(chunks)

            // 然后为所有块生成嵌入
            val embeddings = chunks.map { OnnxEmbeddingService.generateEmbedding(it.content) }

            // 最后，用有效ID和嵌入更新块，并将它们添加到索引管理器中
            chunks.forEachIndexed { index, chunk ->
                if (index < embeddings.size) {
                    val embedding = embeddings[index]
                    if (embedding != null) {
                        chunk.embedding = embedding
                        // 此时 chunk.id 是有效的, 存入ChunkReference而不是整个chunk
                        val reference = ChunkReference(chunk.id)
                        chunkIndexManager.addItem(IndexItem("chunk_${chunk.id}", embedding.vector, reference))
                    }
                }
            }
            // 因为embedding被设置了，再次put来更新它们
            chunkBox.put(chunks)
        }

        // 保存索引到文件
        chunkIndexManager.save()
        android.util.Log.d("MemoryRepo", "Chunk index saved to: ${indexFile.absolutePath}. File exists: ${indexFile.exists()}")

        // 更新父Memory以保存ToMany关系和索引路径
        android.util.Log.d("MemoryRepo", "Saving memory ${documentMemory.id} with chunkIndexFilePath: ${documentMemory.chunkIndexFilePath}")
        memoryBox.put(documentMemory)
        documentMemory
    }

    /**
     * 生成带有元数据（可信度、重要性）的文本，用于embedding。
     */
    private fun generateTextForEmbedding(memory: Memory): String {
        // 只使用核心内容来生成向量，以确保语义的纯粹性。
        // 元数据（如credibility, importance）应该在评分阶段作为权重使用，而不是成为文本本身的一部分。
        return memory.content
    }

    /**
     * 检查并修复旧的嵌入向量（从100维升级到384维）
     * 如果检测到旧向量，自动重新生成
     */
    private suspend fun ensureEmbeddingUpToDate(memory: Memory): Boolean = withContext(Dispatchers.IO) {
        val embedding = memory.embedding
        if (embedding != null && embedding.vector.size == 100) {
            // 检测到旧的100维向量，重新生成
            android.util.Log.d("MemoryRepo", "Upgrading embedding for '${memory.title}' from 100 to 384 dimensions")
            val textForEmbedding = generateTextForEmbedding(memory)
            val newEmbedding = OnnxEmbeddingService.generateEmbedding(textForEmbedding)
            if (newEmbedding != null) {
                memory.embedding = newEmbedding
                memoryBox.put(memory)
                addMemoryToIndex(memory)
                return@withContext true
            }
        }
        return@withContext false
    }

    // --- Memory CRUD Operations ---

    /**
     * Creates or updates a memory, automatically generating its embedding.
     * @param memory The memory object to be saved.
     * @return The ID of the saved memory.
     */
    suspend fun saveMemory(memory: Memory): Long = withContext(Dispatchers.IO){
        // Generate embedding before saving
        if (memory.content.isNotBlank()) {
            val textForEmbedding = generateTextForEmbedding(memory)
            memory.embedding = OnnxEmbeddingService.generateEmbedding(textForEmbedding)
        }
        val id = memoryBox.put(memory)
        // After saving to DB, ensure it's also added to the live vector index
        addMemoryToIndex(memory)
        id
    }

    /**
     * Finds a memory by its ID.
     * @param id The ID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryById(id: Long): Memory? = withContext(Dispatchers.IO) {
        memoryBox.get(id)
    }

    /**
     * Finds a memory by its UUID.
     * @param uuid The UUID of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryByUuid(uuid: String): Memory? = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.uuid.equal(uuid)).build().findFirst()
    }

    /**
     * Finds a memory by its exact title.
     * @param title The title of the memory to find.
     * @return The found Memory object, or null if not found.
     */
    suspend fun findMemoryByTitle(title: String): Memory? = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.title.equal(title)).build().findFirst()
    }

    /**
     * Finds all memories with the exact title (case-sensitive).
     * @param title The title of the memories to find.
     * @return A list of found Memory objects.
     */
    suspend fun findMemoriesByTitle(title: String): List<Memory> = withContext(Dispatchers.IO) {
        memoryBox.query(Memory_.title.equal(title)).build().find()
    }

    /**
     * Deletes a memory and all its links. This is a critical operation and should be handled with
     * care.
     * @param memoryId The ID of the memory to delete.
     * @return True if deletion was successful, false otherwise.
     */
    suspend fun deleteMemory(memoryId: Long): Boolean = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId) ?: return@withContext false

        // 如果是文档节点，删除其专属的区块索引文件和所有区块
        if (memory.isDocumentNode) {
            if (memory.chunkIndexFilePath != null) {
                try {
                    val indexFile = File(memory.chunkIndexFilePath!!)
                    if (indexFile.exists()) {
                        if (indexFile.delete()) {
                            android.util.Log.d("MemoryRepo", "Deleted chunk index file: ${indexFile.path}")
                        } else {
                            android.util.Log.w("MemoryRepo", "Failed to delete chunk index file: ${indexFile.path}")
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MemoryRepo", "Error deleting chunk index file for memory ID $memoryId", e)
                }
            }
            // 删除关联的区块
            val chunkIds = memory.documentChunks.map { it.id }
            if (chunkIds.isNotEmpty()) {
                chunkBox.removeByIds(chunkIds)
                android.util.Log.d("MemoryRepo", "Deleted ${chunkIds.size} associated chunks for document.")
            }
        }

        // Before deleting the memory, we must clean up its links.
        // This prevents dangling references.
        memory.links.forEach { linkBox.remove(it) }
        memory.backlinks.forEach { linkBox.remove(it) }
        memoryBox.remove(memory)
    }

    // --- Link CRUD Operations ---
    suspend fun findLinkById(linkId: Long): MemoryLink? = withContext(Dispatchers.IO) {
        linkBox.get(linkId)
    }

    suspend fun updateLink(linkId: Long, type: String, weight: Float, description: String): MemoryLink? = withContext(Dispatchers.IO) {
        val link = findLinkById(linkId) ?: return@withContext null
        val sourceMemory = link.source.target

        link.type = type
        link.weight = weight
        link.description = description
        linkBox.put(link)

        // 在更新link后，同样put其所属的source memory。
        // 这是为了向ObjectBox明确指出，这个父实体的关系集合“脏了”，
        // 以此来避免后续查询时拿到缓存的旧数据。
        if (sourceMemory != null) {
            memoryBox.put(sourceMemory)
        }

        link
    }

    suspend fun deleteLink(linkId: Long): Boolean = withContext(Dispatchers.IO) {
        // 为了健壮性，在删除链接后，也更新其父实体。
        val link = findLinkById(linkId)
        val sourceMemory = link?.source?.target

        val wasRemoved = linkBox.remove(linkId)

        if (wasRemoved && sourceMemory != null) {
            // 通过put源实体，我们确保它的ToMany关系缓存在其他线程或未来的查询中得到更新。
            memoryBox.put(sourceMemory)
        }
        wasRemoved
    }

    // --- Tagging Operations ---

    /**
     * Adds a tag to a memory.
     * @param memory The memory to tag.
     * @param tagName The name of the tag.
     * @return The MemoryTag object.
     */
    suspend fun addTagToMemory(memory: Memory, tagName: String): MemoryTag = withContext(Dispatchers.IO) {
        // Find existing tag or create a new one
        val tag =
                tagBox.query()
                        .equal(MemoryTag_.name, tagName, QueryBuilder.StringOrder.CASE_SENSITIVE)
                        .build()
                        .findFirst()
                        ?: MemoryTag(name = tagName).also { tagBox.put(it) }

        if (!memory.tags.any { it.id == tag.id }) {
            memory.tags.add(tag)
            memoryBox.put(memory)
        }
        tag
    }

    // --- Linking Operations ---

    /**
     * Creates a link between two memories.
     * @param source The source memory.
     * @param target The target memory.
     * @param type The type of the link (e.g., "causes", "explains").
     * @param weight The strength of the link, ideally between 0.0 and 1.0.
     *               It's recommended to use the predefined constants like [STRONG_LINK],
     *               [MEDIUM_LINK], or [WEAK_LINK] for consistency. The value will be
     *               automatically clamped to the [0.0, 1.0] range.
     * @param description A description of the link.
     */
    suspend fun linkMemories(
            source: Memory,
            target: Memory,
            type: String,
            weight: Float = MEDIUM_LINK,
            description: String = ""
    ) = withContext(Dispatchers.IO) {
        // Coerce the weight to be within the valid range [0.0, 1.0] to ensure data integrity.
        val sanitizedWeight = weight.coerceIn(0.0f, 1.0f)
        val link = MemoryLink(type = type, weight = sanitizedWeight, description = description)
        link.source.target = source
        link.target.target = target

        source.links.add(link)
        memoryBox.put(source)
    }

    /** Gets all outgoing links from a memory. */
    suspend fun getOutgoingLinks(memoryId: Long): List<MemoryLink> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        memory?.links ?: emptyList()
    }

    /** Gets all incoming links to a memory. */
    suspend fun getIncomingLinks(memoryId: Long): List<MemoryLink> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        memory?.backlinks ?: emptyList()
    }

    // --- Complex Queries ---

    /**
     * Searches memories using semantic search if a query is provided, otherwise returns all
     * memories.
     * @param query The search query string.
     * @param semanticThreshold The minimum semantic similarity threshold (0.0-1.0). Lower values return more results.
     * @return A list of matching Memory objects, sorted by relevance.
     */
    suspend fun searchMemories(query: String, semanticThreshold: Float = 0.6f): List<Memory> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext memoryBox.all

        val keywords = query.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) {
            return@withContext emptyList()
        }

        val scores = mutableMapOf<Long, Double>()
        val k = 60.0 // RRF constant for result fusion
        val allMemories = memoryBox.all // Fetch all memories once for efficiency

        // 1. Keyword-based search (Memory title/content contains query)
        val titleConditions = keywords.map { Memory_.title.contains(it, QueryBuilder.StringOrder.CASE_INSENSITIVE) }
        val contentConditions = keywords.map { Memory_.content.contains(it, QueryBuilder.StringOrder.CASE_INSENSITIVE) }

        val allConditions = titleConditions + contentConditions

        val keywordResults = if (allConditions.isNotEmpty()) {
            val finalCondition = allConditions.drop(1)
                .fold(allConditions.first() as QueryCondition<Memory>) { acc, condition ->
                    acc.or(condition)
                }
            memoryBox.query(finalCondition).build().find()
        } else {
            emptyList()
        }

        android.util.Log.d("MemoryRepo", "--- Keyword Search Results: ${keywordResults.size} ---")
        keywordResults.forEachIndexed { index, memory ->
            val rank = index + 1
            val baseScore = 1.0 / (k + rank)
            val keywordWeight = 10.0 // Boost keyword search importance
            val weightedScore = baseScore * memory.importance * keywordWeight
            scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore
            android.util.Log.d("MemoryRepo", "  [Keyword] Score for '${memory.title}': rank=$rank, baseScore=${String.format("%.4f", baseScore)}, importance=${memory.importance}, addedScore=${String.format("%.4f", weightedScore)}")
        }

        // 2. Reverse Containment Search (Query contains Memory Title)
        // This is crucial for finding "长安大学" within the query "长安大学在西安".
        val reverseContainmentResults =
                allMemories.filter { memory -> query.contains(memory.title, ignoreCase = true) }
        
        android.util.Log.d("MemoryRepo", "--- Reverse Containment Search Results: ${reverseContainmentResults.size} ---")
        reverseContainmentResults.forEachIndexed { index, memory ->
            val rank = index + 1
            // Use the same RRF formula to add to the score
            val baseScore = 1.0 / (k + rank)
            val revContainWeight = 10.0 // Also boost this signal
            val weightedScore = baseScore * memory.importance * revContainWeight
            scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore
            android.util.Log.d("MemoryRepo", "  [RevContain] Score for '${memory.title}': rank=$rank, baseScore=${String.format("%.4f", baseScore)}, importance=${memory.importance}, addedScore=${String.format("%.4f", weightedScore)}")
        }

        // 3. Semantic search (for conceptual matches)
        val queryEmbedding = OnnxEmbeddingService.generateEmbedding(query)
        if (queryEmbedding != null) {
            // 自动升级旧的100维向量到384维
            allMemories.forEach { memory ->
                if (memory.embedding != null && memory.embedding!!.vector.size == 100) {
                    ensureEmbeddingUpToDate(memory)
                }
            }
            
            val allMemoriesWithEmbedding = allMemories.filter { it.embedding != null }

            val minSimilarityThreshold = semanticThreshold // 语义相似度阈值（可配置）
            
            // Calculate ALL similarities for debugging
            val allSimilarities = allMemoriesWithEmbedding
                .map { memory ->
                                memory.embedding?.let { memoryEmbedding ->
                        val similarity = OnnxEmbeddingService.cosineSimilarity(
                                                    queryEmbedding,
                                                    memoryEmbedding
                                            )
                        Triple(memory, similarity, similarity >= minSimilarityThreshold)
                    } ?: Triple(memory, 0f, false)
                            }
                            .sortedByDescending { it.second }
            
            // Log top similarities regardless of threshold
            android.util.Log.d("MemoryRepo", "--- All Semantic Similarities (Total: ${allSimilarities.size}) ---")
            allSimilarities.take(10).forEach { (memory, similarity, aboveThreshold) ->
                val marker = if (aboveThreshold) "✓" else "✗"
                android.util.Log.d("MemoryRepo", "  $marker [${memory.title}]: Similarity = ${String.format("%.4f", similarity)}")
            }
            
            val semanticResultsWithScores = allSimilarities
                .filter { it.third }
                .map { Pair(it.first, it.second) }

            android.util.Log.d("MemoryRepo", "--- Top Semantic Search Results (Threshold: $minSimilarityThreshold): ${semanticResultsWithScores.size} ---")
            semanticResultsWithScores.take(5).forEach { (memory, similarity) ->
                android.util.Log.d("MemoryRepo", "  - [${memory.title}]: Similarity = ${String.format("%.4f", similarity)}")
            }

            semanticResultsWithScores.forEachIndexed { index, (memory, similarity) ->
                val rank = index + 1
                
                // RRF score (retains ranking information but has low impact)
                val rankScore = 1.0 / (k + rank)
                
                // Raw similarity score (high impact, directly reflects semantic relevance)
                val semanticWeight = 0.5f 
                val similarityScore = similarity * semanticWeight

                // Combine them. Importance should only affect the rank score, not the raw similarity.
                val weightedScore = (rankScore * Math.sqrt(memory.importance.toDouble())) + similarityScore
                scores[memory.id] = scores.getOrDefault(memory.id, 0.0) + weightedScore

                android.util.Log.d("MemoryRepo", "  [Semantic] Score for '${memory.title}': rank=$rank, similarity=${String.format("%.4f", similarity)}, rankScore=${String.format("%.4f", rankScore)}, similarityScore=${String.format("%.4f", similarityScore)}, importance=${memory.importance}, addedScore=${String.format("%.4f", weightedScore)}")
            }
        }

        // 4. Graph-based expansion: Boost scores of connected memories based on edge weights
        // Take top-scoring memories as "seed nodes" and propagate scores through edges
        val topMemoriesForExpansion = scores.entries.sortedByDescending { it.value }.take(10)
        android.util.Log.d("MemoryRepo", "Graph expansion: Using ${topMemoriesForExpansion.size} seed nodes")

        var edgesTraversed = 0
        val graphPropagationWeight = 0.4 // Increased from 0.1
        val basePropagationScore = 0.03 // Give a minimum score boost for any connection

        topMemoriesForExpansion.forEach { (sourceId, sourceScore) ->
            val sourceMemory = allMemories.find { it.id == sourceId } ?: return@forEach
            val sourceScore = scores[sourceId] ?: 0.0
            
            // 重置关系缓存以获取最新连接
            sourceMemory.links.reset()
            sourceMemory.backlinks.reset()
            
            // Propagate score through outgoing links
            sourceMemory.links.forEach { link ->
                val targetMemory = link.target.target
                if (targetMemory != null) {
                    // 边权重越高，传播的分数越多
                    val propagatedScore = (sourceScore * link.weight * graphPropagationWeight) + basePropagationScore
                    scores[targetMemory.id] = scores.getOrDefault(targetMemory.id, 0.0) + propagatedScore
                    android.util.Log.d("MemoryRepo", "  Propagated ${String.format("%.4f", propagatedScore)} from '${sourceMemory.title}' to '${targetMemory.title}' (weight: ${link.weight})")
                    edgesTraversed++
                }
            }
            
            // Propagate score through incoming links (backlinks)
            sourceMemory.backlinks.forEach { link ->
                val targetMemory = link.source.target
                if (targetMemory != null) {
                    // 边权重越高，传播的分数越多
                    val propagatedScore = (sourceScore * link.weight * graphPropagationWeight) + basePropagationScore
                    scores[targetMemory.id] = scores.getOrDefault(targetMemory.id, 0.0) + propagatedScore
                    android.util.Log.d("MemoryRepo", "  Propagated ${String.format("%.4f", propagatedScore)} from '${targetMemory.title}' to '${sourceMemory.title}' (weight: ${link.weight})")
                    edgesTraversed++
                }
            }
        }
        android.util.Log.d("MemoryRepo", "Graph expansion completed: ${edgesTraversed} edges traversed")

        // 5. Fuse results using RRF and return sorted list
        if (scores.isEmpty()) {
            return@withContext emptyList()
        }
        
        // 添加相关性阈值过滤，避免返回不相关的记忆
        val minScoreThreshold = 0.025 // 最低分数阈值，可根据实际效果调整
        val filteredScores = scores.entries.filter { it.value >= minScoreThreshold }
        
        android.util.Log.d("MemoryRepo", "Score filtering: ${scores.size} total results, ${filteredScores.size} above threshold $minScoreThreshold")
        
        // --- 详细日志记录 ---
        val sortedScoresForLogging = scores.entries.sortedByDescending { it.value }
        sortedScoresForLogging.take(15).forEach { (id, score) ->
            val memory = allMemories.find { it.id == id }
            if (memory != null) {
                android.util.Log.d("MemoryRepo", "  - [${memory.title}]: Final Score = ${String.format("%.4f", score)}")
            }
        }
        // --- 日志记录结束 ---

        if (filteredScores.isEmpty()) {
            android.util.Log.d("MemoryRepo", "No memories above relevance threshold")
            return@withContext emptyList()
        }
        
        val sortedMemoryIds = filteredScores.sortedByDescending { it.value }.map { it.key }

        // Fetch the sorted entities from the database
        val sortedMemories = memoryBox.get(sortedMemoryIds)

        // 7. Semantic Deduplication
        // deduplicateBySemantics(sortedMemories)
        sortedMemories
    }

    /**
     * 获取指定记忆的所有文档区块。
     * @param memoryId 父记忆的ID。
     * @return 该记忆关联的DocumentChunk列表。
     */
    suspend fun getChunksForMemory(memoryId: Long): List<DocumentChunk> = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId)
        // 从数据库关系中获取，并按原始顺序排序
        memory?.documentChunks?.sortedBy { it.chunkIndex } ?: emptyList()
    }

    /**
     * 在指定文档的区块内进行搜索。
     * @param memoryId 父记忆的ID。
     * @param query 搜索查询。
     * @return 匹配的DocumentChunk列表。
     */
    suspend fun searchChunksInDocument(memoryId: Long, query: String): List<DocumentChunk> = withContext(Dispatchers.IO) {
        android.util.Log.d("MemoryRepo", "--- Starting search in document (Memory ID: $memoryId) for query: '$query' ---")
        val memory = findMemoryById(memoryId) ?: return@withContext emptyList<DocumentChunk>().also {
            android.util.Log.w("MemoryRepo", "Document with ID $memoryId not found.")
        }

        if (query.isBlank()) {
            android.util.Log.d("MemoryRepo", "Query is blank, returning all chunks sorted by index.")
            return@withContext getChunksForMemory(memoryId) // 返回有序的全部区块
        }

        val keywords = query.split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) {
            return@withContext getChunksForMemory(memoryId)
        }

        // --- 关键词搜索（作为补充） ---
        val keywordResults = getChunksForMemory(memoryId)
            .filter { chunk -> keywords.any { keyword -> chunk.content.contains(keyword, ignoreCase = true) } }
            .toMutableList()

        // 2. 向量语义搜索
        val semanticResults = mutableListOf<DocumentChunk>()
        val documentMemory = memoryBox.get(memoryId)
        if (documentMemory?.chunkIndexFilePath != null && File(documentMemory.chunkIndexFilePath!!).exists()) {
            val queryEmbedding = OnnxEmbeddingService.generateEmbedding(query)?.vector
            if (queryEmbedding != null) {
                android.util.Log.d("MemoryRepo", "Generated query embedding successfully. Starting semantic search.")
                try {
                    val chunkIndexManager = VectorIndexManager<IndexItem<ChunkReference>, String>(
                        dimensions = 384,
                        maxElements = 20000,
                        indexFile = File(documentMemory.chunkIndexFilePath!!)
                    )
                    val searchResults = chunkIndexManager.findNearest(queryEmbedding, 20)
                    val chunkIds = searchResults.map { it.value.chunkId }
                    // 从数据库批量获取完整的chunk对象
                    semanticResults.addAll(chunkBox.get(chunkIds))

                    android.util.Log.d("MemoryRepo", "Semantic search found ${semanticResults.size} results (similarity > 0.82).")
                } catch (e: Exception) {
                    android.util.Log.e("MemoryRepo", "Error during semantic search in document", e)
                }
            }
        } else {
            android.util.Log.w("MemoryRepo", "Chunk index file not found or path is null for document ID $memoryId. Skipping semantic search.")
        }

        // 3. 合并并去重结果
        val combinedResults = (keywordResults + semanticResults).distinctBy { it.id }
        android.util.Log.d("MemoryRepo", "Combined and deduplicated results count: ${combinedResults.size}. Results are now ordered by relevance.")
        android.util.Log.d("MemoryRepo", "--- Search in document finished ---")

        combinedResults
    }

    /**
     * 更新单个文档区块的内容。
     * @param chunkId 要更新的区块ID。
     * @param newContent 新的文本内容。
     */
    suspend fun updateChunk(chunkId: Long, newContent: String) = withContext(Dispatchers.IO) {
        val chunk = chunkBox.get(chunkId) ?: return@withContext
        val memory = chunk.memory.target ?: return@withContext

        chunk.content = newContent
        val newEmbeddingVector = OnnxEmbeddingService.generateEmbedding(newContent)?.vector ?: return@withContext // 重新生成向量并获取vector
        chunk.embedding = Embedding(newEmbeddingVector)
        chunkBox.put(chunk)

        // 同时更新专用索引文件中的向量
        val parentMemory = memory
        if (parentMemory?.chunkIndexFilePath != null) {
            val indexFile = File(parentMemory.chunkIndexFilePath!!)
            if (indexFile.exists()) {
                val chunkIndexManager = VectorIndexManager<IndexItem<ChunkReference>, String>(384, 20_000, indexFile)
                chunkIndexManager.initIndex() // 加载
                chunkIndexManager.addItem(IndexItem(chunk.id.toString(), newEmbeddingVector, ChunkReference(chunk.id)))
                chunkIndexManager.save() // 保存更改
                android.util.Log.d("MemoryRepo", "Updated chunk ${chunk.id} in index file: ${indexFile.path}")
            }
        }
    }

    suspend fun addMemoryToIndex(memory: Memory) = withContext(Dispatchers.IO) {
        if (memory.embedding != null) {
            // 只添加384维的向量到索引
            if (memory.embedding!!.vector.size == 384) {
                vectorIndexManager.addItem(IndexItem(memory.uuid, memory.embedding!!.vector, memory))
            } else {
                android.util.Log.w("MemoryRepo", "Skipping adding memory '${memory.title}' to index: wrong dimension ${memory.embedding!!.vector.size}")
            }
        }
    }
    suspend fun removeMemoryFromIndex(memory: Memory) = withContext(Dispatchers.IO) {
        // hnswlib支持removeEnabled时可用，若不支持可忽略
        // vectorIndexManager.removeItem(memory.uuid)
    }

    /** 使用HNSW索引的高效语义检索。 */
    suspend fun searchMemoriesPrecise(query: String, similarityThreshold: Float = 0.95f): List<Memory> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        val queryEmbedding = OnnxEmbeddingService.generateEmbedding(query) ?: return@withContext emptyList()
        // 取前100个最相近的记忆，再按阈值过滤
        val candidates = vectorIndexManager.findNearest(queryEmbedding.vector, 100)
        candidates.mapNotNull {
            val memory = it.value
            if (memory.embedding != null && OnnxEmbeddingService.cosineSimilarity(queryEmbedding, memory.embedding!!) >= similarityThreshold) {
                memory
            } else {
                null
            }
        }
    }

    /**
     * Builds a Graph object from a given list of memories. This is used to display a subset of the
     * entire memory graph, e.g., after a search.
     * @param memories The list of memories to include in the graph.
     * @return A Graph object.
     */
    suspend fun getGraphForMemories(memories: List<Memory>): Graph = withContext(Dispatchers.IO) {
        // Expand the initial list of memories to include direct neighbors
        val expandedMemories = mutableSetOf<Memory>()
        expandedMemories.addAll(memories)

        memories.forEach { memory ->
            memory.links.forEach { link -> link.target.target?.let { expandedMemories.add(it) } }
            memory.backlinks.forEach { backlink ->
                backlink.source.target?.let { expandedMemories.add(it) }
            }
        }

        android.util.Log.d(
                "MemoryRepo",
                "Initial memories: ${memories.size}, Expanded memories: ${expandedMemories.size}"
        )
        buildGraphFromMemories(expandedMemories.toList(), null)
    }

    /** Retrieves a single memory by its UUID. */
    suspend fun getMemoryByUuid(uuid: String): Memory? =
            withContext(Dispatchers.IO) {
                memoryBox.query(Memory_.uuid.equal(uuid)).build().findUnique()
            }

    /**
     * 获取所有唯一的文件夹路径。
     * @return 所有唯一的文件夹路径列表。
     */
    suspend fun getAllFolderPaths(): List<String> = withContext(Dispatchers.IO) {
        memoryBox.all
            .map { it.folderPath ?: "未分类" }
            .distinct()
            .sorted()
    }

    /**
     * 按文件夹路径获取记忆（包括所有子文件夹）。
     * @param folderPath 文件夹路径。
     * @return 该文件夹及其所有子文件夹下的记忆列表。
     */
    suspend fun getMemoriesByFolderPath(folderPath: String): List<Memory> = withContext(Dispatchers.IO) {
        if (folderPath == "未分类") {
            // 查询 folderPath 为 null 或空字符串的记忆
            memoryBox.all.filter { it.folderPath.isNullOrEmpty() }
        } else {
            // 包括当前文件夹及所有子文件夹的记忆
            // 例如：选择 "技术" 会包含 "技术/编程"、"技术/编程/Java" 等
            memoryBox.all.filter { memory ->
                val path = memory.folderPath ?: ""
                path == folderPath || path.startsWith("$folderPath/")
            }
        }
    }

    /**
     * 获取指定文件夹的图谱（包括跨文件夹的边）。
     * @param folderPath 文件夹路径。
     * @return 该文件夹的图谱对象。
     */
    suspend fun getGraphForFolder(folderPath: String): Graph = withContext(Dispatchers.IO) {
        val memories = getMemoriesByFolderPath(folderPath)
        buildGraphFromMemories(memories, folderPath)
    }

    /**
     * 重命名文件夹（更新该文件夹下所有记忆的 folderPath）。
     * @param oldPath 旧的文件夹路径。
     * @param newPath 新的文件夹路径。
     * @return 是否成功。
     */
    suspend fun renameFolder(oldPath: String, newPath: String): Boolean = withContext(Dispatchers.IO) {
        if (oldPath == newPath) return@withContext true
        
        try {
            // 获取该文件夹及其所有子文件夹下的记忆
            val memories = memoryBox.all.filter { memory ->
                val path = memory.folderPath ?: ""
                path == oldPath || path.startsWith("$oldPath/")
            }
            
            // 批量更新路径
            memories.forEach { memory ->
                val currentPath = memory.folderPath ?: ""
                memory.folderPath = if (currentPath == oldPath) {
                    newPath
                } else {
                    currentPath.replaceFirst(oldPath, newPath)
                }
            }
            
            memoryBox.put(memories)
            true
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepo", "Failed to rename folder", e)
            false
        }
    }

    /**
     * 移动记忆到新文件夹。
     * @param memoryIds 要移动的记忆ID列表。
     * @param targetFolderPath 目标文件夹路径。
     * @return 是否成功。
     */
    suspend fun moveMemoriesToFolder(memoryIds: List<Long>, targetFolderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val memories = memoryIds.mapNotNull { findMemoryById(it) }
            memories.forEach { it.folderPath = targetFolderPath }
            memoryBox.put(memories)
            true
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepo", "Failed to move memories", e)
            false
        }
    }

    /**
     * 创建新文件夹（实际上是通过在该路径下创建一个占位记忆来实现）。
     * @param folderPath 新文件夹的路径。
     * @return 是否成功。
     */
    suspend fun createFolder(folderPath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            // 检查是否已存在该文件夹
            val exists = memoryBox.all.any { (it.folderPath ?: "") == folderPath }
            if (exists) return@withContext true
            
            // 创建一个占位记忆
            val placeholder = Memory(
                title = "文件夹说明",
                content = "这是 $folderPath 文件夹的说明。",
                uuid = UUID.randomUUID().toString(),
                folderPath = folderPath
            )
            val embedding = OnnxEmbeddingService.generateEmbedding(placeholder.content)
            if (embedding != null) {
                placeholder.embedding = embedding
            }
            memoryBox.put(placeholder)
            true
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepo", "Failed to create folder", e)
            false
        }
            }

    /**
     * 创建新记忆并自动生成embedding，保存到数据库并同步索引。
     */
    suspend fun createMemory(title: String, content: String, contentType: String = "text/plain", source: String = "user_input", folderPath: String = ""): Memory? = withContext(Dispatchers.IO) {
        val memory = Memory(
            title = title,
            content = content,
            contentType = contentType,
            source = source,
            folderPath = folderPath
        )
        val textForEmbedding = generateTextForEmbedding(memory)
        memory.embedding = OnnxEmbeddingService.generateEmbedding(textForEmbedding) ?: return@withContext null
        saveMemory(memory)
        addMemoryToIndex(memory)
        memory
    }

    /**
     * 更新已有记忆内容（title/content等），自动更新embedding和索引。
     */
    suspend fun updateMemory(
        memory: Memory,
        newTitle: String,
        newContent: String,
        newContentType: String = memory.contentType,
        newSource: String = memory.source,
        newCredibility: Float = memory.credibility,
        newImportance: Float = memory.importance,
        newFolderPath: String? = memory.folderPath,
        newTags: List<String>? = null // 可选的要更新的标签列表
    ): Memory? = withContext(Dispatchers.IO) {
        // 检查是否有旧的100维向量需要升级
        val hasOldEmbedding = memory.embedding != null && memory.embedding!!.vector.size == 100
        
        val contentChanged = memory.content != newContent
        val credibilityChanged = memory.credibility != newCredibility
        val importanceChanged = memory.importance != newImportance

        // 只有在影响embedding的因素变化时才重新生成，或者发现旧向量时强制升级
        val needsReEmbedding = contentChanged || credibilityChanged || importanceChanged || hasOldEmbedding

        // 更新记忆属性
        memory.apply {
            title = newTitle
            content = newContent
            contentType = newContentType
            source = newSource
            credibility = newCredibility
            importance = newImportance
            folderPath = newFolderPath
        }

        val newEmbedding = if (needsReEmbedding) {
            val textForEmbedding = generateTextForEmbedding(memory)
            OnnxEmbeddingService.generateEmbedding(textForEmbedding) ?: memory.embedding
        } else {
            memory.embedding
        }
        memory.embedding = newEmbedding

        // 更新标签
        if (newTags != null) {
            memory.tags.clear() // 清除旧标签
            newTags.forEach { tagName ->
                // Find existing tag or create a new one
                val tag = tagBox.query(MemoryTag_.name.equal(tagName, QueryBuilder.StringOrder.CASE_SENSITIVE))
                    .build().findFirst() ?: MemoryTag(name = tagName).also { tagBox.put(it) }
                memory.tags.add(tag)
            }
        }

        // 更新记忆属性
        memory.apply {
            this.updatedAt = java.util.Date()
        }

        // 这里不再需要调用 saveMemory，因为 memory 对象已经被修改，
        // 最后的 memoryBox.put(memory) 会保存所有更改。
        memoryBox.put(memory)

        if (needsReEmbedding) {
            addMemoryToIndex(memory)
        }
        memory
    }

    /**
     * Merges multiple source memories into a single new memory, redirecting all links.
     */
    suspend fun mergeMemories(
        sourceTitles: List<String>,
        newTitle: String,
        newContent: String,
        newTags: List<String>,
        folderPath: String
    ): Memory? = withContext(Dispatchers.IO) {
        // Step 1: Find all unique source memories from the given titles.
        // Using a Set ensures that we handle each memory object only once, even if titles are duplicated.
        val sourceMemories = mutableSetOf<Memory>()
        for (title in sourceTitles.distinct()) {
            sourceMemories.addAll(findMemoriesByTitle(title))
        }

        // After finding all memories, check if we have enough to merge.
        if (sourceMemories.size < 2) {
            android.util.Log.w("MemoryRepo", "Merge requires at least two unique source memories to be found. Found: ${sourceMemories.size} from titles: ${sourceTitles.joinToString()}.")
            return@withContext null
        }

        var newMemory: Memory? = null
        try {
            store.runInTx {
                // 2. Create the new merged memory (without embedding yet)
                val mergedMemory = Memory(
            title = newTitle,
            content = newContent,
                    folderPath = folderPath,
                    source = "merged_from_problem_library"
                )
                memoryBox.put(mergedMemory) // Save to get an ID

                // 3. Add tags to the new memory
                newTags.forEach { tagName ->
                    val tag = tagBox.query(MemoryTag_.name.equal(tagName, QueryBuilder.StringOrder.CASE_SENSITIVE))
                        .build().findFirst() ?: MemoryTag(name = tagName).also { tagBox.put(it) }
                    mergedMemory.tags.add(tag)
                }
                memoryBox.put(mergedMemory)

                // 4. Collect all unique links and redirect them
                val allLinksToProcess = mutableSetOf<MemoryLink>()
                val sourceIdsSet = sourceMemories.map { it.id }.toSet()

                sourceMemories.forEach {
                    it.links.reset()
                    it.backlinks.reset()
                    allLinksToProcess.addAll(it.links)
                    allLinksToProcess.addAll(it.backlinks)
                }

                allLinksToProcess.forEach { link ->
                    if (link.source.targetId in sourceIdsSet) {
                        link.source.target = mergedMemory
                    }
                    if (link.target.targetId in sourceIdsSet) {
                        link.target.target = mergedMemory
                    }
                }
                linkBox.put(allLinksToProcess.toList())

                // 5. Delete old source memories
                memoryBox.removeByIds(sourceIdsSet.toList())

                newMemory = mergedMemory
            }

            // After the transaction, handle non-transactional parts
            newMemory?.let { memory ->
                // Generate and save embedding for the new memory
                val textForEmbedding = generateTextForEmbedding(memory)
                memory.embedding = OnnxEmbeddingService.generateEmbedding(textForEmbedding)
                memoryBox.put(memory)

                // Update vector index
                addMemoryToIndex(memory)
                for (mem in sourceMemories) {
                    removeMemoryFromIndex(mem)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepo", "Error during memory merge transaction.", e)
            return@withContext null
        }

        newMemory
    }

    /**
     * 删除记忆并同步索引。
     */
    suspend fun deleteMemoryAndIndex(memoryId: Long): Boolean = withContext(Dispatchers.IO) {
        val memory = findMemoryById(memoryId) ?: return@withContext false
        removeMemoryFromIndex(memory)
        deleteMemory(memoryId)
    }

    /**
     * 根据UUID批量删除记忆及其所有关联。
     * @param uuids 要删除的记忆的UUID集合。
     * @return 如果操作成功，返回true。
     */
    suspend fun deleteMemoriesByUuids(uuids: Set<String>): Boolean = withContext(Dispatchers.IO) {
        android.util.Log.d("MemoryRepo", "Attempting to delete memories with UUIDs: $uuids")
        if (uuids.isEmpty()) {
            android.util.Log.d("MemoryRepo", "UUID set is empty, nothing to delete.")
            return@withContext true
        }

        // 使用QueryBuilder动态构建OR查询
        val builder = memoryBox.query()
        // ObjectBox的QueryBuilder.equal()不支持字符串，我们必须从Property本身开始构建条件
        if (uuids.isNotEmpty()) {
            var finalCondition: QueryCondition<Memory> = Memory_.uuid.equal(uuids.first())
            uuids.drop(1).forEach { uuid ->
                finalCondition = finalCondition.or(Memory_.uuid.equal(uuid))
            }
            builder.apply(finalCondition)
        }
        val memoriesToDelete = builder.build().find()

        android.util.Log.d("MemoryRepo", "Found ${memoriesToDelete.size} memories to delete.")
        if (memoriesToDelete.isEmpty()) {
            return@withContext true
        }

        // 在一个事务中执行所有数据库写入操作
        try {
            store.runInTx {
                // 1. 收集所有相关链接和区块的ID
                val linkIdsToDelete = mutableSetOf<Long>()
                val chunkIdsToDelete = mutableSetOf<Long>()
                for (memory in memoriesToDelete) {
                    memory.links.reset()
                    memory.backlinks.reset()
                    memory.links.forEach { link -> linkIdsToDelete.add(link.id) }
                    memory.backlinks.forEach { link -> linkIdsToDelete.add(link.id) }

                    if (memory.isDocumentNode) {
                        memory.documentChunks.reset()
                        memory.documentChunks.forEach { chunk -> chunkIdsToDelete.add(chunk.id) }
                    }
                }
                android.util.Log.d("MemoryRepo", "Found ${linkIdsToDelete.size} unique links and ${chunkIdsToDelete.size} chunks to delete.")

                // 2. 批量删除链接和区块
                if (linkIdsToDelete.isNotEmpty()) {
                    linkBox.removeByIds(linkIdsToDelete)
                    android.util.Log.d("MemoryRepo", "Bulk-deleted ${linkIdsToDelete.size} links.")
                }
                if (chunkIdsToDelete.isNotEmpty()) {
                    chunkBox.removeByIds(chunkIdsToDelete)
                    android.util.Log.d("MemoryRepo", "Bulk-deleted ${chunkIdsToDelete.size} chunks.")
                }

                // 3. 批量删除记忆本身
                val memoryIdsToDelete = memoriesToDelete.map { it.id }
                memoryBox.removeByIds(memoryIdsToDelete)
                android.util.Log.d("MemoryRepo", "Bulk-deleted ${memoriesToDelete.size} memories.")
            }
            android.util.Log.d("MemoryRepo", "Transaction completed successfully.")
        } catch (e: Exception) {
            android.util.Log.e("MemoryRepo", "Error during bulk delete transaction.", e)
            return@withContext false
        }

        // 4. 在事务外处理向量索引和文件
        for (memory in memoriesToDelete) {
            removeMemoryFromIndex(memory)
            // 删除文档的专属索引文件
            if (memory.isDocumentNode && memory.chunkIndexFilePath != null) {
                try {
                    val indexFile = File(memory.chunkIndexFilePath!!)
                    if (indexFile.exists() && indexFile.delete()) {
                         android.util.Log.d("MemoryRepo", "Deleted chunk index file: ${indexFile.path}")
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MemoryRepo", "Error deleting chunk index file for memory UUID ${memory.uuid}", e)
                }
            }
        }
        android.util.Log.d("MemoryRepo", "Removed memories from vector index and cleaned up chunk index files.")

        return@withContext true
    }

    // --- Graph Export ---

    /** Fetches all memories and their links, and converts them into a Graph data structure. */
    suspend fun getMemoryGraph(): Graph = withContext(Dispatchers.IO) {
        buildGraphFromMemories(memoryBox.all, null)
    }

    /**
     * Private helper to construct a graph from a specific list of memories. Ensures that edges are
     * only created if both source and target nodes are in the list.
     * @param memories 要构建图谱的记忆列表
     * @param currentFolderPath 当前选中的文件夹路径（用于判断跨文件夹连接），null表示显示全部
     */
    private fun buildGraphFromMemories(memories: List<Memory>, currentFolderPath: String? = null): Graph {
        val memoryUuids = memories.map { it.uuid }.toSet()

        val nodes =
                memories.map { memory ->
                    Node(
                            id = memory.uuid,
                            label = memory.title,
                            color =
                                    if (memory.isDocumentNode) {
                                        Color(0xFF9575CD) // Purple for documents
                                    } else {
                                    when (memory.tags.firstOrNull()?.name) {
                                        "Person" -> Color(0xFF81C784) // Green
                                        "Concept" -> Color(0xFF64B5F6) // Blue
                                        else -> Color.LightGray
                                        }
                                    }
                    )
                }

        val edges = mutableListOf<Edge>()
        memories.forEach { memory ->
            // 关键：重置关系缓存，确保获取最新的连接信息
            memory.links.reset()
            memory.links.forEach { link ->
                val sourceMemory = link.source.target
                val targetMemory = link.target.target
                val sourceId = sourceMemory?.uuid
                val targetId = targetMemory?.uuid
                
                // Only add edges if both source and target are in the filtered list
                if (sourceId != null &&
                    targetId != null &&
                    sourceId in memoryUuids &&
                    targetId in memoryUuids
                ) {
                    // 检测是否为跨文件夹连接
                    // 始终检测跨文件夹连接，无论是否选择了特定文件夹
                    val isCrossFolder = if (sourceMemory != null && targetMemory != null) {
                        val sourcePath = sourceMemory.folderPath ?: "未分类"
                        val targetPath = targetMemory.folderPath ?: "未分类"
                        sourcePath != targetPath
                    } else {
                        false
                    }
                    
                    edges.add(
                        Edge(
                            id = link.id,
                            sourceId = sourceId,
                            targetId = targetId,
                            label = link.type,
                            weight = link.weight,
                            isCrossFolderLink = isCrossFolder
                        )
                    )
                } else if (sourceId != null && targetId != null) {
                    // Log discarded edges for debugging
                    // android.util.Log.d("MemoryRepo", "Discarding edge: $sourceId -> $targetId
                    // (Not in filtered list)")
                }
            }
        }
        android.util.Log.d(
                "MemoryRepo",
                "Built graph with ${nodes.size} nodes and ${edges.distinct().size} edges."
        )
        return Graph(nodes = nodes, edges = edges.distinct())
    }

    /**
     * 删除文件夹：将所有属于 folderPath 的记忆移动到"未分类"
     */
    suspend fun deleteFolder(folderPath: String) {
        withContext(Dispatchers.IO) {
            val memories = memoryBox.query(Memory_.folderPath.equal(folderPath)).build().find()
            memories.forEach { memory ->
                memory.folderPath = "未分类"
                memoryBox.put(memory)
            }
            android.util.Log.d("MemoryRepo", "Deleted folder '$folderPath', moved ${memories.size} memories to '未分类'")
        }
    }

}
