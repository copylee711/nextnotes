package com.nextnotes.ui.ai

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

/**
 * A picture from the conversation, over the whole screen: pinch or double-tap to look closer, drag
 * to move about, tap outside it or the close button to go back.
 */
@Composable
fun ImagePreview(bitmap: Bitmap, label: String?, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        com.nextnotes.ui.KeepBarsHidden()
        var scale by remember { mutableFloatStateOf(1f) }
        var offset by remember { mutableStateOf(Offset.Zero) }
        Box(
            Modifier.fillMaxSize().background(Color(0xE6101216))
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { if (scale <= 1.01f) onClose() },
                        onDoubleTap = {
                            if (scale > 1.01f) {
                                scale = 1f
                                offset = Offset.Zero
                            } else {
                                scale = 2.5f
                            }
                        },
                    )
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        scale = (scale * zoom).coerceIn(1f, 8f)
                        offset = if (scale <= 1f) Offset.Zero else offset + pan
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Image(
                bitmap.asImageBitmap(), label, contentScale = ContentScale.Fit,
                modifier = Modifier.padding(48.dp).fillMaxSize()
                    .graphicsLayer { scaleX = scale; scaleY = scale; translationX = offset.x; translationY = offset.y },
            )
            if (!label.isNullOrBlank()) {
                Text(
                    label, color = Color.White.copy(alpha = 0.85f), fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp),
                )
            }
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(16.dp).size(44.dp).clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.14f)),
            ) { Icon(Icons.Outlined.Close, "关闭预览", tint = Color.White) }
        }
    }
}
