package com.ai.assistance.operit.services.tasker

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.ai.assistance.operit.data.repository.WorkflowRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * BroadcastReceiver for receiving workflow trigger requests from Tasker
 * 
 * This receiver allows Tasker to trigger Operit workflows via broadcasts.
 */
class WorkflowTaskerReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkflowTaskerReceiver"
        const val ACTION_TRIGGER_WORKFLOW = "com.ai.assistance.operit.TRIGGER_WORKFLOW"
        const val EXTRA_WORKFLOW_ID = "workflow_id"
        
        /**
         * Create an intent to trigger a workflow
         */
        fun createTriggerIntent(context: Context, workflowId: String): Intent {
            return Intent(ACTION_TRIGGER_WORKFLOW).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_WORKFLOW_ID, workflowId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_WORKFLOW) {
            return
        }

        val workflowId = intent.getStringExtra(EXTRA_WORKFLOW_ID)
        if (workflowId.isNullOrBlank()) {
            Log.w(TAG, "Received trigger intent without workflow ID")
            return
        }

        Log.d(TAG, "Received workflow trigger request: $workflowId")

        // Use goAsync to allow async work
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                val result = repository.triggerWorkflow(workflowId)
                
                if (result.isSuccess) {
                    Log.d(TAG, "Workflow triggered successfully: ${result.getOrNull()}")
                } else {
                    Log.e(TAG, "Failed to trigger workflow: ${result.exceptionOrNull()?.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error triggering workflow", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

/**
 * BroadcastReceiver for boot completed event
 * 
 * Re-schedules all enabled workflows after device reboot
 */
class WorkflowBootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "WorkflowBootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) {
            return
        }

        Log.d(TAG, "Device booted, rescheduling workflows")

        // Use goAsync to allow async work
        val pendingResult = goAsync()
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val repository = WorkflowRepository(context.applicationContext)
                val result = repository.getAllWorkflows()
                
                result.getOrNull()?.forEach { workflow ->
                    if (workflow.enabled) {
                        repository.scheduleWorkflow(workflow.id)
                        Log.d(TAG, "Rescheduled workflow: ${workflow.name}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error rescheduling workflows after boot", e)
            } finally {
                pendingResult.finish()
            }
        }
    }
}

