package dev.thegamer.btsnooze

import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class ToggleTileService : TileService() {
    override fun onStartListening() = refresh()

    override fun onClick() {
        val prefs = Prefs(this)
        prefs.enabled = !prefs.enabled
        refresh()
        Enforcer.syncAll(this)
    }

    private fun refresh() {
        val tile = qsTile ?: return
        val prefs = Prefs(this)
        tile.state = if (prefs.enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.subtitle = "${prefs.blocked.size} device${if (prefs.blocked.size == 1) "" else "s"}"
        tile.updateTile()
    }
}
