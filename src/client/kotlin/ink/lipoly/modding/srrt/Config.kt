package ink.lipoly.modding.srrt

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    var isShulkerRDKManaged: Boolean = false,
    var monitorDefaultPath: Boolean = true,
    var monitorPaxiPath: Boolean = true,
    var reloadOnFocusGained: Boolean = false,
    var enableToastNotification: Boolean = false
)