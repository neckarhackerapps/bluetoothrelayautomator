package com.neckarhackerapps.bluetoothrelayautomator.engine

sealed class MacroExecutionState {
    object Idle : MacroExecutionState()

    data class Running(
        val macroId: String,
        val macroName: String,
        val stepIndex: Int,
        val totalSteps: Int,
        val currentStepTitle: String
    ) : MacroExecutionState() {
        val progress: Float
            get() = if (totalSteps > 0) (stepIndex + 1).toFloat() / totalSteps else 0f
    }

    data class Success(
        val macroId: String,
        val macroName: String,
        val durationMs: Long
    ) : MacroExecutionState()

    data class Failure(
        val macroId: String,
        val macroName: String,
        val errorReason: String,
        val durationMs: Long
    ) : MacroExecutionState()

    val isRunning: Boolean
        get() = this is Running
}
