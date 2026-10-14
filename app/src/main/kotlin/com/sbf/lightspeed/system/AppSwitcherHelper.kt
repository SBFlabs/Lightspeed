package com.sbf.lightspeed.system

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log

internal object AppSwitcherHelper {
    private val TAG = ElevatedTaskCloser.TAG

    fun switchToPreviousApp(context: Context) {
        Log.i(TAG, "switchToPreviousApp() invoked")
        ElevatedTaskCloser.exemptHiddenApis()

        if (ElevatedTaskCloser.isShizukuActive && switchViaShizuku(context)) {
            Log.i(TAG, "Task switched via Shizuku IActivityTaskManager")
            return
        }

        switchViaAccessibility(context)
    }

    private fun switchViaShizuku(context: Context): Boolean {
        return try {
            val myPkg = context.packageName
            val atm = ElevatedTaskCloser.getActivityTaskManager()

            if (atm != null) {
                    var recentTasksList: List<*>? = null
                    try {
                        val getRecentTasksMethod = atm.javaClass.methods.firstOrNull { it.name == "getRecentTasks" }
                        if (getRecentTasksMethod != null) {
                            val paramsCount = getRecentTasksMethod.parameterTypes.size
                            val rawResult = when (paramsCount) {
                                2 -> getRecentTasksMethod.invoke(atm, 10, 0x0002 /* RECENT_IGNORE_UNAVAILABLE */)
                                3 -> getRecentTasksMethod.invoke(atm, 10, 0x0002, 0)
                                else -> null
                            }
                            if (rawResult != null) {
                                val getListMethod = rawResult.javaClass.getMethod("getList")
                                recentTasksList = getListMethod.invoke(rawResult) as? List<*>
                            }
                        }
                    } catch (e: Exception) {
                        Log.d(TAG, "getRecentTasks reflection failed", e)
                    }

                    if (!recentTasksList.isNullOrEmpty()) {
                        val currentTaskItem = recentTasksList[0]
                        val currentTaskId = if (currentTaskItem != null) {
                            try { currentTaskItem.javaClass.getField("taskId").getInt(currentTaskItem) } catch (_: Exception) { -1 }
                        } else -1
                        val splitGroup = if (currentTaskId > 0) ElevatedTaskCloser.getSplitGroupTaskIds(currentTaskId) else emptySet()

                        var targetTaskId: Int? = null
                        for (i in 1 until recentTasksList.size) {
                            val taskInfo = recentTasksList[i] ?: continue
                            val taskIdField = taskInfo.javaClass.getField("taskId")
                            val tId = taskIdField.getInt(taskInfo)

                            if (tId in splitGroup) {
                                Log.w(TAG, "Skipping task $tId as split sibling of $currentTaskId")
                                continue
                            }

                            val baseIntentField = try { taskInfo.javaClass.getField("baseIntent") } catch (_: Exception) { null }
                            val intent = baseIntentField?.get(taskInfo) as? Intent
                            val pkg = intent?.component?.packageName ?: intent?.`package`

                            if (pkg != null && !ElevatedTaskCloser.isSystem(pkg) && pkg != myPkg && tId > 0) {
                                targetTaskId = tId
                                break
                            } else if (tId > 0 && targetTaskId == null) {
                                targetTaskId = tId
                            }
                        }

                        if (targetTaskId != null && targetTaskId > 0) {
                            val startActivityFromRecentsMethod = atm.javaClass.methods.firstOrNull { it.name == "startActivityFromRecents" }
                            if (startActivityFromRecentsMethod != null) {
                                val pCount = startActivityFromRecentsMethod.parameterTypes.size
                                when (pCount) {
                                    2 -> startActivityFromRecentsMethod.invoke(atm, targetTaskId, null)
                                    3 -> startActivityFromRecentsMethod.invoke(atm, targetTaskId, null, null)
                                }
                                return true
                            }

                            val moveTaskToFrontMethod = atm.javaClass.methods.firstOrNull { it.name == "moveTaskToFront" }
                            if (moveTaskToFrontMethod != null) {
                                val pCount = moveTaskToFrontMethod.parameterTypes.size
                                when (pCount) {
                                    2 -> moveTaskToFrontMethod.invoke(atm, targetTaskId, 0)
                                    3 -> moveTaskToFrontMethod.invoke(atm, targetTaskId, 0, null)
                                }
                                return true
                            }
                        }
                    }
                }

            val proc = ElevatedTaskCloser.execShizuku("input keyevent KEYCODE_APP_SWITCH && sleep 0.04 && input keyevent KEYCODE_APP_SWITCH")
            proc.waitForOrKill()
            true
        } catch (e: Exception) {
            Log.e(TAG, "switchViaShizuku error", e)
            false
        }
    }

    private fun switchViaAccessibility(context: Context) {
        val service = com.sbf.lightspeed.LightspeedAccessibilityService.instance
        if (service != null) {
            service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            Handler(Looper.getMainLooper()).postDelayed({
                service.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_RECENTS)
            }, 80L)
        }
    }
}
