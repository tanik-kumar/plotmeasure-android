package com.tanik.biharmapmeasure.plotmeasure.core

class UndoManager<T>(
    private val maxEntries: Int = 48,
) {
    private val previousStates = ArrayDeque<T>()
    private val nextStates = ArrayDeque<T>()

    fun canUndo(): Boolean = previousStates.isNotEmpty()

    fun canRedo(): Boolean = nextStates.isNotEmpty()

    fun record(snapshot: T) {
        previousStates.addLast(snapshot)
        while (previousStates.size > maxEntries) {
            previousStates.removeFirst()
        }
        nextStates.clear()
    }

    fun undo(current: T): T? {
        val restored = previousStates.removeLastOrNull() ?: return null
        nextStates.addLast(current)
        return restored
    }

    fun redo(current: T): T? {
        val restored = nextStates.removeLastOrNull() ?: return null
        previousStates.addLast(current)
        return restored
    }

    fun reset() {
        previousStates.clear()
        nextStates.clear()
    }
}
