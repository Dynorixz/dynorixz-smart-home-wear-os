package com.dynorixz.smarthome.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.dynorixz.smarthome.domain.Capability
import kotlinx.serialization.json.JsonPrimitive

@Preview(widthDp = 192, heightDp = 192, showBackground = true)
@Composable
private fun OnOffControlPreview() {
    DynorixzTheme(dynamicColor = false) {
        CapabilityControl(
            capability = Capability.OnOff(
                instance = "on",
                retrievable = true,
                reportable = false,
                value = JsonPrimitive(true),
                lastUpdatedSeconds = null,
            ),
            pending = false,
            execute = { _, _ -> },
        )
    }
}

