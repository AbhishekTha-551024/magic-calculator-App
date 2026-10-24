package com.magic.calculator.feature.secret.domain.model

/**
 * Domain representation of sensor hardware telemetry data.
 */
data class SensorLabData(
    val sensorType: Int,
    val sensorName: String,
    val values: FloatArray,
    val formattedDisplay: String
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as SensorLabData
        return sensorType == other.sensorType && values.contentEquals(other.values)
    }

    override fun hashCode(): Int {
        var result = sensorType
        result = 31 * result + values.contentHashCode()
        return result
    }
}
