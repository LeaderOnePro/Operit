package com.ai.assistance.operit.core.workflow

import android.content.Context
import android.util.Log
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * WorkflowSchedulerInitializer
 * 
 * Initializes workflow scheduling when the app starts.
 * Re-schedules all enabled workflows to ensure they continue running
 * even if the app was force-stopped or updated.
 */
object WorkflowSchedulerInitializer {
    
    private const val TAG = "WorkflowSchedulerInit"
    
    /**
     * Initialize workflow scheduling
     * Should be called from Application.onCreate()
     */
    fun initialize(context: Context) {
        Log.d(TAG, "Initializing workflow scheduler...")
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                val result = repository.getAllWorkflows()
                
                result.getOrNull()?.let { workflows ->
                    var scheduledCount = 0
                    
                    workflows.forEach { workflow ->
                        if (workflow.enabled) {
                            val success = repository.scheduleWorkflow(workflow.id)
                            if (success) {
                                scheduledCount++
                                Log.d(TAG, "Scheduled workflow: ${workflow.name} (${workflow.id})")
                            }
                        }
                    }
                    
                    Log.d(TAG, "Workflow scheduler initialized. Scheduled $scheduledCount workflows.")
                } ?: run {
                    Log.w(TAG, "Failed to get workflows during initialization")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error initializing workflow scheduler", e)
            }
        }
    }
}

