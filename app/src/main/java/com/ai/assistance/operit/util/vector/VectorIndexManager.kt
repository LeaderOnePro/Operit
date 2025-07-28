package com.ai.assistance.operit.util.vector

import com.github.jelmerk.hnswlib.core.DistanceFunctions
import com.github.jelmerk.hnswlib.core.Item
import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex
import java.io.File
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.io.IOException

/**
 * 精简的HNSW向量索引管理器，支持初始化、添加、查询、保存、加载。
 */
class VectorIndexManager<T : Item<Id, FloatArray>, Id : Any>(
    private val dimensions: Int,
    private val maxElements: Int,
    private val indexFile: File? = null
) {
    private var index: HnswIndex<Id, FloatArray, T, Float>? = null

    init {
        initIndex()
    }

    /** 初始化索引（新建或加载） */
    fun initIndex() {
        index = if (indexFile != null && indexFile.exists()) {
            try {
                ObjectInputStream(indexFile.inputStream()).use { it.readObject() as HnswIndex<Id, FloatArray, T, Float> }
            } catch (e: Exception) {
                android.util.Log.e("VectorIndexManager", "Failed to load index, creating new one.", e)
                // 如果加载失败，删除可能已损坏的文件并创建一个新的
                indexFile.delete()
                HnswIndex
                    .newBuilder(dimensions, DistanceFunctions.FLOAT_COSINE_DISTANCE, maxElements)
                    .build()
            }
        } else {
            HnswIndex
                .newBuilder(dimensions, DistanceFunctions.FLOAT_COSINE_DISTANCE, maxElements)
                .build()
        }
    }

    /** 添加一个向量项 */
    fun addItem(item: T) {
        index?.add(item)
    }

    /** 查询最近的K个邻居 */
    fun findNearest(query: FloatArray, k: Int): List<T> {
        return index?.findNearest(query, k)?.map { it.item() } ?: emptyList()
    }

    /** 保存索引到文件 */
    fun save() {
        if (indexFile != null && index != null) {
            try {
                // 确保父目录存在
                indexFile.parentFile?.mkdirs()
                ObjectOutputStream(indexFile.outputStream()).use { it.writeObject(index) }
            } catch (e: IOException) {
                android.util.Log.e("VectorIndexManager", "Failed to save index to ${indexFile.absolutePath}", e)
            }
        }
    }

    /** 关闭索引（可选） */
    fun close() {
        index = null
    }
} 