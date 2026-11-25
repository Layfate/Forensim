package com.forensim.app.feature.professor

import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.forensim.app.core.designsystem.ForensimTheme
import com.forensim.app.core.designsystem.Spacing
import com.forensim.app.engine.renderer.VictimPreviewRenderer
import com.forensim.app.engine.scene.Victim

/**
 * 3D preview of the victim's body as students will find it: slowly turning, drag to turn it
 * yourself. Updates in place when the sex or position changes.
 */
@Composable
fun VictimPreview(victim: Victim, modifier: Modifier = Modifier) {
    val assets = androidx.compose.ui.platform.LocalContext.current.assets
    val renderer = remember { VictimPreviewRenderer(victim, assets) }
    LaunchedEffect(victim) { renderer.victim = victim }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var surface by remember { mutableStateOf<GLSurfaceView?>(null) }
    val colors = ForensimTheme.colors
    Surface(modifier, color = colors.hudScrim.copy(alpha = 1f), contentColor = colors.onHud, shape = MaterialTheme.shapes.large) {
        Box(Modifier.fillMaxSize()
            .semantics { contentDescription = "3D model of the victim: ${victim.label}" }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { renderer.spin = false },
                    onDragEnd = { renderer.spin = true },
                    onDragCancel = { renderer.spin = true },
                ) { _, dx -> renderer.yawDeg -= dx * 0.4f }
            }) {
            AndroidView(factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(3)
                    setEGLConfigChooser(com.forensim.app.engine.renderer.HdConfigChooser())
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
                    surface = this
                }
            }, modifier = Modifier.fillMaxSize())
            Text(victim.label, style = MaterialTheme.typography.labelMedium, color = colors.onHud,
                modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.sm))
        }
    }
    DisposableEffect(lifecycle, surface) {
        val current = surface
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) current?.onPause()
            if (event == Lifecycle.Event.ON_RESUME) current?.onResume()
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            current?.onPause()
        }
    }
}
