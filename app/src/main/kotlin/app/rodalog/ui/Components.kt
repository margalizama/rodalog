@file:OptIn(ExperimentalMaterial3Api::class)

package app.rodalog.ui

import android.app.DatePickerDialog
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Event
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.rodalog.R
import app.rodalog.util.Fmt
import java.util.Calendar

@Composable
fun Card2(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    val shape = RoundedCornerShape(20.dp)
    if (onClick != null) {
        Card(onClick = onClick, modifier = modifier, shape = shape, colors = colors, elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    } else {
        Card(modifier = modifier, shape = shape, colors = colors, elevation = CardDefaults.cardElevation(1.dp)) {
            Column(Modifier.padding(16.dp), content = content)
        }
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    big: Boolean = false,
    tint: Color = MaterialTheme.colorScheme.primary,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            .padding(12.dp)
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(value, fontSize = if (big) 28.sp else 18.sp, fontWeight = FontWeight.Bold, color = tint)
    }
}

@Composable
fun EmptyState(icon: ImageVector, text: String, actionLabel: String? = null, onAction: (() -> Unit)? = null) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(icon, null, Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (actionLabel != null && onAction != null) Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp)
    )
}

/** Diálogo de formulario con Guardar / Cancelar y, opcionalmente, Eliminar. */
@Composable
fun FormDialog(
    title: String,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    saveEnabled: Boolean = true,
    onDelete: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        },
        confirmButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Icon(Icons.Filled.Delete, null, tint = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onSave, enabled = saveEnabled) { Text(stringResource(R.string.save)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun NumField(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}

@Composable
fun TextF(value: String, onValue: (String) -> Unit, label: String, modifier: Modifier = Modifier.fillMaxWidth(), single: Boolean = true) {
    OutlinedTextField(
        value = value,
        onValueChange = onValue,
        label = { Text(label) },
        singleLine = single,
        modifier = modifier,
    )
}

@Composable
fun DateField(label: String, millis: Long, fmt: Fmt, onChange: (Long) -> Unit) {
    val ctx = LocalContext.current
    OutlinedButton(
        onClick = {
            val c = Calendar.getInstance()
            if (millis > 0) c.timeInMillis = millis
            DatePickerDialog(
                ctx,
                { _, y, m, d ->
                    val cc = Calendar.getInstance()
                    cc.set(y, m, d, 12, 0, 0)
                    onChange(cc.timeInMillis)
                },
                c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)
            ).show()
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(Icons.Filled.Event, null, Modifier.size(18.dp))
        Spacer(Modifier.size(8.dp))
        Text("$label: ${fmt.date(millis)}")
    }
}

@Composable
fun Choice(options: List<Pair<String, String>>, selected: String, onSelect: (String) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (key, label) ->
            FilterChip(selected = selected == key, onClick = { onSelect(key) }, label = { Text(label) })
        }
    }
}

@Composable
fun categoryLabel(c: String): String = stringResource(
    when (c) {
        "SERVICE" -> R.string.cat_service
        "REPAIR" -> R.string.cat_repair
        "TIRES" -> R.string.cat_tires
        "INSURANCE" -> R.string.cat_insurance
        "TAX" -> R.string.cat_tax
        "PARKING" -> R.string.cat_parking
        "TOLL" -> R.string.cat_toll
        "FINE" -> R.string.cat_fine
        "WASH" -> R.string.cat_wash
        else -> R.string.cat_other
    }
)

/** Gráfica de línea simple (por ejemplo consumo por tanque). */
@Composable
fun LineChart(values: List<Double>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val grid = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
    Canvas(modifier.fillMaxWidth().height(110.dp)) {
        if (values.size < 2) return@Canvas
        val lo = values.minOrNull() ?: 0.0
        val hi = values.maxOrNull() ?: 1.0
        val span = if (hi - lo > 1e-9) hi - lo else 1.0
        val w = size.width
        val h = size.height
        for (g in 0..2) {
            val gy = h * g / 2f
            drawLine(grid, Offset(0f, gy), Offset(w, gy), 1f)
        }
        val path = Path()
        val pts = ArrayList<Offset>()
        values.forEachIndexed { i, v ->
            val px = w * i / (values.size - 1)
            val py = (h - 10f - ((v - lo) / span * (h - 20f))).toFloat()
            pts.add(Offset(px, py))
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        drawPath(path, color, style = Stroke(width = 4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        pts.forEach { drawCircle(color, 6f, it) }
    }
}

/** Dibuja la ruta de un viaje ("lat,lon;lat,lon") ajustada a su caja. */
@Composable
fun RouteSketch(route: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.secondary) {
    val pts = remember(route) {
        route.split(";").mapNotNull {
            val p = it.split(",")
            if (p.size == 2) {
                val la = p[0].toDoubleOrNull()
                val lo = p[1].toDoubleOrNull()
                if (la != null && lo != null) Pair(la, lo) else null
            } else null
        }
    }
    val bg = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    Canvas(modifier.fillMaxWidth().height(140.dp).clip(RoundedCornerShape(16.dp)).background(bg)) {
        if (pts.size < 2) return@Canvas
        val minLa = pts.minOf { it.first }
        val maxLa = pts.maxOf { it.first }
        val minLo = pts.minOf { it.second }
        val maxLo = pts.maxOf { it.second }
        val spanLa = if (maxLa - minLa > 1e-9) maxLa - minLa else 1e-9
        val spanLo = if (maxLo - minLo > 1e-9) maxLo - minLo else 1e-9
        val pad = 14f
        val scale = minOf((size.width - 2 * pad) / spanLo, (size.height - 2 * pad) / spanLa)
        val ox = (size.width - spanLo * scale).toFloat() / 2f
        val oy = (size.height - spanLa * scale).toFloat() / 2f
        val path = Path()
        pts.forEachIndexed { i, p ->
            val x = ox + ((p.second - minLo) * scale).toFloat()
            val y = size.height - oy - ((p.first - minLa) * scale).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color, style = Stroke(width = 5f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}
