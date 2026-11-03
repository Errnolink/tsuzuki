package exh.md.dto

import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object AggregateVolumesSerializer : JsonTransformingSerializer<Map<String, AggregateVolume>>(
    MapSerializer(String.serializer(), AggregateVolume.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) JsonObject(element.associate { it.jsonObject.getValue("volume").jsonPrimitive.content to it }) else element
}

object AggregateChaptersSerializer : JsonTransformingSerializer<Map<String, AggregateChapter>>(
    MapSerializer(String.serializer(), AggregateChapter.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement =
        if (element is JsonArray) JsonObject(element.associate { it.jsonObject.getValue("chapter").jsonPrimitive.content to it }) else element
}
