package com.nextnotes.tools

import com.xnotes.core.tools.EraseMode
import com.xnotes.core.tools.Tool
import com.xnotes.settings.Settings
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class NnEraserTest {
    private fun eraser(json: String) = Settings.fromJson(JSONObject(json)).configFor(Tool.ERASER).eraseMode

    @Test fun aFreshEraserCuts() {
        assertEquals(EraseMode.AREA, eraser("{}"))
    }

    @Test fun xnotesWholeStrokeDefaultGivesWay() {
        assertEquals(EraseMode.AREA, eraser("""{"tools":{"eraser":{"erase_mode":"stroke"}}}"""))
    }

    @Test fun aModePickedSinceStays() {
        val picked = Settings.fromJson(JSONObject()).let { s ->
            s.copy(tools = s.tools + (Tool.ERASER to s.configFor(Tool.ERASER).copy(eraseMode = EraseMode.STROKE)))
        }
        assertEquals(EraseMode.STROKE, Settings.fromJson(picked.toJson()).configFor(Tool.ERASER).eraseMode)
    }
}
