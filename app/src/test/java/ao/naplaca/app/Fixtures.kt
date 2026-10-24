package ao.naplaca.app

import ao.naplaca.app.data.remote.Network
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

object Fixtures {
    fun json(name: String): JsonObject {
        val text = requireNotNull(javaClass.classLoader?.getResource(name)) { "fixture $name em falta" }.readText()
        return Network.json.parseToJsonElement(text).jsonObject
    }
}
