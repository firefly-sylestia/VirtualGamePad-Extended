package io.github.kitswas.virtualgamepadmobile.ui.utils

import io.github.kitswas.VGP_Data_Exchange.GamepadReading
import java.util.WeakHashMap

/**
 * Tracks logical button sources so remapped controls that share one output do not
 * release that output while another source is still holding it.
 */
object GamepadButtonDispatcher {
    private val activeSources = WeakHashMap<GamepadReading, MutableMap<String, Int>>()
    private val pressCounts = WeakHashMap<GamepadReading, MutableMap<Int, Int>>()

    @Synchronized
    fun press(state: GamepadReading, source: String, mask: Int) {
        if (mask == 0) return
        val sources = activeSources.getOrPut(state) { mutableMapOf() }
        val previous = sources[source]
        if (previous == mask) return
        if (previous != null) release(state, source)

        activeSources.getOrPut(state) { mutableMapOf() }[source] = mask
        val counts = pressCounts.getOrPut(state) { mutableMapOf() }
        val count = counts[mask] ?: 0
        if (count == 0) state.ButtonsDown = state.ButtonsDown or mask
        counts[mask] = count + 1
    }

    @Synchronized
    fun release(state: GamepadReading, source: String) {
        val sources = activeSources[state] ?: return
        val mask = sources.remove(source) ?: return
        if (sources.isEmpty()) activeSources.remove(state)

        val counts = pressCounts[state] ?: return
        val count = counts[mask] ?: return
        if (count <= 1) {
            counts.remove(mask)
            state.ButtonsDown = state.ButtonsDown and mask.inv()
            state.ButtonsUp = state.ButtonsUp or mask
        } else {
            counts[mask] = count - 1
        }
        if (counts.isEmpty()) pressCounts.remove(state)
    }

    @Synchronized
    fun releaseAll(state: GamepadReading) {
        activeSources[state]?.keys?.toList()?.forEach { source -> release(state, source) }
    }
}
