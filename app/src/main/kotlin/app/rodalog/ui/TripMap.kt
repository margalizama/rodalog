package app.rodalog.ui

import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.rodalog.data.RoutePoint
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.File

/** Colores por rango de velocidad (km/h): <30, 30-60, 60-90, >90. */
val SPEED_COLORS = intArrayOf(0xFF2E7D32.toInt(), 0xFFF9A825.toInt(), 0xFFEF6C00.toInt(), 0xFFC62828.toInt())
private val BRAND_LINE = 0xFFE8590C.toInt()

private fun bucket(kmh: Int) = when {
    kmh < 30 -> 0
    kmh < 60 -> 1
    kmh < 90 -> 2
    else -> 3
}

/** Mapa OpenStreetMap con la ruta coloreada por velocidad. */
@Composable
fun TripMap(points: List<RoutePoint>, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val zoomed = remember { mutableStateOf(false) }
    val map = remember {
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = ctx.packageName
        cfg.osmdroidBasePath = File(ctx.cacheDir, "osmdroid")
        cfg.osmdroidTileCache = File(ctx.cacheDir, "osmdroid/tiles")
        MapView(ctx).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            isTilesScaledToDpi = true
            zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
        }
    }

    DisposableEffect(lifecycle, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> {}
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    AndroidView(
        factory = { map },
        modifier = modifier,
        update = { m -> render(m, points, dark, zoomed) },
    )
}

private fun render(m: MapView, pts: List<RoutePoint>, dark: Boolean, zoomed: MutableState<Boolean>) {
    m.overlays.clear()
    m.overlayManager.tilesOverlay.setColorFilter(if (dark) TilesOverlay.INVERT_COLORS else null)
    if (pts.size >= 2) {
        val geo = pts.map { GeoPoint(it.lat, it.lon) }
        val hasSpeed = pts.any { it.kmh > 0 }
        if (!hasSpeed) {
            m.overlays.add(line(geo, BRAND_LINE))
        } else {
            var cur = bucket(pts[1].kmh)
            var run = ArrayList<GeoPoint>()
            run.add(geo[0])
            run.add(geo[1])
            for (i in 2 until pts.size) {
                val b = bucket(pts[i].kmh)
                if (b != cur) {
                    m.overlays.add(line(run, SPEED_COLORS[cur]))
                    run = ArrayList()
                    run.add(geo[i - 1])
                    cur = b
                }
                run.add(geo[i])
            }
            m.overlays.add(line(run, SPEED_COLORS[cur]))
        }
        m.overlays.add(dot(m, geo.first(), 0xFF2E7D32.toInt()))
        m.overlays.add(dot(m, geo.last(), 0xFFC62828.toInt()))

        if (!zoomed.value) {
            zoomed.value = true
            val box = BoundingBox.fromGeoPoints(geo)
            if (m.width > 0 && m.height > 0) {
                m.zoomToBoundingBox(box, false, 90)
            } else {
                m.addOnFirstLayoutListener { _, _, _, _, _ -> m.zoomToBoundingBox(box, false, 90) }
            }
        }
    }
    m.invalidate()
}

private fun line(pts: List<GeoPoint>, color: Int): Polyline {
    val pl = Polyline()
    pl.setPoints(pts)
    pl.outlinePaint.color = color
    pl.outlinePaint.strokeWidth = 14f
    pl.outlinePaint.strokeCap = Paint.Cap.ROUND
    return pl
}

private fun dot(m: MapView, p: GeoPoint, color: Int): Marker {
    val d = GradientDrawable()
    d.shape = GradientDrawable.OVAL
    d.setColor(color)
    d.setStroke(5, android.graphics.Color.WHITE)
    d.setSize(38, 38)
    val mk = Marker(m)
    mk.position = p
    mk.icon = d
    mk.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
    mk.setOnMarkerClickListener { _, _ -> true }
    return mk
}
