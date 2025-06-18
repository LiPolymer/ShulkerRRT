package ink.lipoly.modding.srrt

import kotlinx.serialization.Serializable

@Serializable
data class Config(
    var isShulkerRDKManaged: Boolean = false,
    var monitorDefaultPath: Boolean = true
)