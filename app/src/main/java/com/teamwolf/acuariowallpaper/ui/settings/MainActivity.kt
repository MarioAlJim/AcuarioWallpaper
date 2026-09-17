package com.teamwolf.acuariowallpaper.ui.settings

import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.teamwolf.acuariowallpaper.AcuarioWallpaperService
import com.teamwolf.acuariowallpaper.core.ConfigManager
import com.teamwolf.acuariowallpaper.ui.theme.AcuarioTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            var appThemeMode by remember { mutableIntStateOf(0) } // 0: Auto, 1: Light, 2: Dark
            val useDark = when (appThemeMode) {
                1 -> false
                2 -> true
                else -> isSystemInDarkTheme()
            }

            AcuarioTheme(darkTheme = useDark) {
                Scaffold(
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    AcuarioSettingsScreen(
                        modifier = Modifier.padding(innerPadding),
                        appThemeMode = appThemeMode,
                        onThemeModeChange = { appThemeMode = it }
                    )
                }
            }
        }
    }
}

enum class PresetLevel(val label: String, val value: Int) {
    BAJO("Bajo", 1),
    MEDIO("Medio", 2),
    ALTO("Alto", 3),
    MUY_ALTO("Muy Alto", 4);

    companion object {
        fun fromValue(valInt: Int, maxVal: Int): PresetLevel {
            val ratio = if (maxVal > 0) valInt.toFloat() / maxVal else 0f
            return when {
                ratio <= 0.25f -> BAJO
                ratio <= 0.55f -> MEDIO
                ratio <= 0.80f -> ALTO
                else -> MUY_ALTO
            }
        }
    }
}

@Composable
fun AcuarioSettingsScreen(
    modifier: Modifier = Modifier,
    appThemeMode: Int,
    onThemeModeChange: (Int) -> Unit
) {
    val context = LocalContext.current
    val configManager = remember { ConfigManager(context) }
    var showHelpDialog by remember { mutableStateOf(false) }

    // Config parameters
    var theme by remember { mutableIntStateOf(configManager.getAcuarioTheme()) }
    var customShallowColor by remember { mutableIntStateOf(configManager.getCustomShallowColor()) }
    var customDeepColor by remember { mutableIntStateOf(configManager.getCustomDeepColor()) }

    var turtleCount by remember { mutableIntStateOf(configManager.getTurtleCount()) }
    var fishCount by remember { mutableIntStateOf(configManager.getFishCount()) }
    var mantaCount by remember { mutableIntStateOf(configManager.getMantaCount()) }
    var jellyfishCount by remember { mutableIntStateOf(configManager.getJellyfishCount()) }
    var axolotlCount by remember { mutableIntStateOf(configManager.getAxolotlCount()) }

    var sharkPresence by remember { mutableIntStateOf(configManager.getSharkPresence()) }
    var sharkColor by remember { mutableIntStateOf(configManager.getSharkColor()) }
    var submarineColor by remember { mutableIntStateOf(configManager.getSubmarineColor()) }

    var plantDensity by remember { mutableIntStateOf(configManager.getPlantDensity()) }
    var bubbleCount by remember { mutableIntStateOf(configManager.getBubbleCount()) }
    var dayNightCycle by remember { mutableIntStateOf(configManager.getDayNightCycleDuration()) }
    var isGyroEnabled by remember { mutableStateOf(configManager.isGyroEnabled()) }

    // Section collapse states (Accordion layout)
    var isSection1Expanded by remember { mutableStateOf(true) }
    var isSection2Expanded by remember { mutableStateOf(true) }
    var isSection3Expanded by remember { mutableStateOf(true) }
    var isSection4Expanded by remember { mutableStateOf(true) }
    var isSection5Expanded by remember { mutableStateOf(true) }

    val bgColor = MaterialTheme.colorScheme.background
    val textColor = MaterialTheme.colorScheme.onBackground

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bgColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 80.dp) // Space for bottom apply button bar
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // Header with App Title, Help Icon, and Theme Mode Selector
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Ajustes de Acuario",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        color = textColor
                    )
                    Text(
                        text = "Fondo de Pantalla OpenGL ES 3.0",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(onClick = { showHelpDialog = true }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                        contentDescription = "Ayuda",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // App Theme Selector Chips (Auto / Claro / Oscuro)
            ThemeSelectorChips(
                selectedMode = appThemeMode,
                onModeSelected = onThemeModeChange
            )

            Spacer(modifier = Modifier.height(20.dp))

            // Accordion Section 1: Tema de Agua y Colores
            AccordionSection(
                title = "Ambiente y Tema del Agua",
                icon = Icons.Default.Water,
                isExpanded = isSection1Expanded,
                onToggleExpand = { isSection1Expanded = !isSection1Expanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Estilo de Iluminación del Acuario",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val themeNames = listOf("Turquesa", "Azul", "Atardecer", "Abisal", "Arrecife", "Personalizado")
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (0..2).forEach { index ->
                                FilterChip(
                                    selected = theme == index,
                                    onClick = {
                                        theme = index
                                        configManager.setAcuarioTheme(index)
                                    },
                                    label = { Text(themeNames[index], fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            (3..5).forEach { index ->
                                FilterChip(
                                    selected = theme == index,
                                    onClick = {
                                        theme = index
                                        configManager.setAcuarioTheme(index)
                                    },
                                    label = { Text(themeNames[index], fontSize = 12.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    if (theme == 5) {
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Color de la Superficie (Agua Somera)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val shallowPalette = listOf(
                            0xFF0DA699.toInt(), 0xFF0D578C.toInt(), 0xFFB34D66.toInt(), 0xFF260D40.toInt(),
                            0xFF1A7066.toInt(), 0xFFE2583E.toInt(), 0xFF2ECC71.toInt(), 0xFF9B59B6.toInt()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            shallowPalette.take(4).forEach { colorVal ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(Color(colorVal), shape = RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (customShallowColor == colorVal) 3.dp else 1.dp,
                                            color = if (customShallowColor == colorVal) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            customShallowColor = colorVal
                                            configManager.setCustomShallowColor(colorVal)
                                        }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Color del Fondo (Fondo Marino)",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        val deepPalette = listOf(
                            0xFF031821.toInt(), 0xFF020B21.toInt(), 0xFF140A26.toInt(), 0xFF02020A.toInt(),
                            0xFF05142E.toInt(), 0xFF1A0A0A.toInt(), 0xFF021A10.toInt(), 0xFF150220.toInt()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            deepPalette.take(4).forEach { colorVal ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .background(Color(colorVal), shape = RoundedCornerShape(8.dp))
                                        .border(
                                            width = if (customDeepColor == colorVal) 3.dp else 1.dp,
                                            color = if (customDeepColor == colorVal) MaterialTheme.colorScheme.primary else Color.Transparent,
                                            shape = RoundedCornerShape(8.dp)
                                        )
                                        .clickable {
                                            customDeepColor = colorVal
                                            configManager.setCustomDeepColor(colorVal)
                                        }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Accordion Section 2: Fauna Marina y Cantidades
            AccordionSection(
                title = "Fauna Marina y Criaturas",
                icon = Icons.Default.Pets,
                isExpanded = isSection2Expanded,
                onToggleExpand = { isSection2Expanded = !isSection2Expanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Ajolotes
                    CreatureQuantitySelector(
                        title = "🦎 Ajolotes de Fondo (Suelo de Piedra)",
                        subtitle = "Caminan e interactúan exclusivamente sobre las rocas del acuario.",
                        count = axolotlCount,
                        maxCount = 4,
                        onCountSelected = {
                            axolotlCount = it
                            configManager.setAxolotlCount(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Peces
                    CreatureQuantitySelector(
                        title = "🐠 Peces Tropicales",
                        subtitle = "Nadan libremente por la columna de agua.",
                        count = fishCount,
                        maxCount = 8,
                        onCountSelected = {
                            fishCount = it
                            configManager.setFishCount(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Tortugas
                    CreatureQuantitySelector(
                        title = "🐢 Tortugas Marinas",
                        subtitle = "Planetean suavemente con aleteo síncrono.",
                        count = turtleCount,
                        maxCount = 5,
                        onCountSelected = {
                            turtleCount = it
                            configManager.setTurtleCount(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Mantarrayas
                    CreatureQuantitySelector(
                        title = "🛸 Mantarrayas Gigantes",
                        subtitle = "Planeadores celestes del fondo del mar.",
                        count = mantaCount,
                        maxCount = 4,
                        onCountSelected = {
                            mantaCount = it
                            configManager.setMantaCount(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Medusas
                    CreatureQuantitySelector(
                        title = "🪼 Medusas Bioluminiscentes",
                        subtitle = "Flotan y emiten descargas eléctricas al ser tocadas.",
                        count = jellyfishCount,
                        maxCount = 6,
                        onCountSelected = {
                            jellyfishCount = it
                            configManager.setJellyfishCount(it)
                        }
                    )

                    Spacer(modifier = Modifier.height(20.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(16.dp))

                    // Tiburón
                    Text("🦈 Tiburón Depredador", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val presenceOptions = listOf("Nunca", "Ocasional", "Siempre")
                        presenceOptions.forEachIndexed { idx, label ->
                            FilterChip(
                                selected = sharkPresence == idx,
                                onClick = {
                                    sharkPresence = idx
                                    configManager.setSharkPresence(idx)
                                },
                                label = { Text(label, fontSize = 12.sp) },
                                modifier = Modifier.weight(1f)
                                )
                        }
                    }

                    if (sharkPresence > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("Color del Tiburón", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val colorOptions = listOf("Gris", "Azul", "Blanco", "Dorado")
                            colorOptions.forEachIndexed { idx, label ->
                                FilterChip(
                                    selected = sharkColor == idx,
                                    onClick = {
                                        sharkColor = idx
                                        configManager.setSharkColor(idx)
                                    },
                                    label = { Text(label, fontSize = 11.sp) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Submarino
                    Text("🟡 Submarino Explorador", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val subColors = listOf("Amarillo", "Rojo", "Azul", "Blanco", "Verde", "Negro")
                        subColors.take(3).forEachIndexed { idx, label ->
                            FilterChip(
                                selected = submarineColor == idx,
                                onClick = {
                                    submarineColor = idx
                                    configManager.setSubmarineColor(idx)
                                },
                                label = { Text(label, fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Accordion Section 3: Flora y Vegetación
            AccordionSection(
                title = "Flora y Vegetación Marina",
                icon = Icons.Default.Spa,
                isExpanded = isSection3Expanded,
                onToggleExpand = { isSection3Expanded = !isSection3Expanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Densidad de Plantas y Corales",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val densityValues = intArrayOf(0, 6, 12, 20)
                    val densityLabels = listOf("Ninguna (0)", "Poca (6)", "Media (12)", "Densa (20)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        densityValues.forEachIndexed { idx, value ->
                            FilterChip(
                                selected = plantDensity == value,
                                onClick = {
                                    plantDensity = value
                                    configManager.setPlantDensity(value)
                                },
                                label = { Text(densityLabels[idx], fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Define la cantidad de algas, anémonas, corales y pasto marino en el lecho.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Accordion Section 4: Burbujas del Fondo
            AccordionSection(
                title = "Burbujas del Fondo",
                icon = Icons.Default.Grain,
                isExpanded = isSection4Expanded,
                onToggleExpand = { isSection4Expanded = !isSection4Expanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Cantidad de Burbujas de Aire",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val bubbleValues = intArrayOf(12, 24, 35, 45)
                    val bubbleLabels = listOf("Poco (12)", "Medio (24)", "Alto (35)", "Muy Alto (45)")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        bubbleValues.forEachIndexed { idx, value ->
                            FilterChip(
                                selected = bubbleCount == value,
                                onClick = {
                                    bubbleCount = value
                                    configManager.setBubbleCount(value)
                                },
                                label = { Text(bubbleLabels[idx], fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Accordion Section 5: Ciclo Día/Noche y Giroscopio
            AccordionSection(
                title = "Ciclo Día/Noche y Sensores 3D",
                icon = Icons.Default.ScreenRotation,
                isExpanded = isSection5Expanded,
                onToggleExpand = { isSection5Expanded = !isSection5Expanded }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Duración del Ciclo Día/Noche",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    val cycleValues = intArrayOf(60, 180, 600, -1)
                    val cycleLabels = listOf("1 min", "3 min", "10 min", "Tiempo Real")
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        cycleValues.forEachIndexed { idx, value ->
                            FilterChip(
                                selected = dayNightCycle == value,
                                onClick = {
                                    dayNightCycle = value
                                    configManager.setDayNightCycleDuration(value)
                                },
                                label = { Text(cycleLabels[idx], fontSize = 11.sp) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Efecto Parallax 3D (Giroscopio)", fontWeight = FontWeight.Medium)
                            Text(
                                text = "Inclina el teléfono para percibir la profundidad del acuario.",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = isGyroEnabled,
                            onCheckedChange = {
                                isGyroEnabled = it
                                configManager.setGyroEnabled(it)
                            }
                        )
                    }
                }
            }
        }

    val isWallpaperActive = remember {
        try {
            val wallpaperManager = WallpaperManager.getInstance(context)
            val info = wallpaperManager.wallpaperInfo
            info != null && info.packageName == context.packageName && info.serviceName == AcuarioWallpaperService::class.java.name
        } catch (e: Exception) {
            false
        }
    }

        // Fixed Apply Button at Bottom
        Surface(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            tonalElevation = 8.dp,
            shadowElevation = 8.dp
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Button(
                    onClick = {
                        configManager.notifySettingsChanged()
                        if (isWallpaperActive) {
                            Toast.makeText(context, "Ajustes aplicados al fondo de pantalla", Toast.LENGTH_SHORT).show()
                            (context as? android.app.Activity)?.finish()
                        } else {
                            try {
                                val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).apply {
                                    putExtra(
                                        WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                                        ComponentName(context, AcuarioWallpaperService::class.java)
                                    )
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                try {
                                    val chooserIntent = Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER)
                                    context.startActivity(chooserIntent)
                                } catch (ex: Exception) {
                                    Toast.makeText(context, "No se pudo abrir el selector de fondos", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        text = if (isWallpaperActive) "Aplicar Ajustes" else "Establecer Fondo de Pantalla",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
        }

        // Help Dialog
        if (showHelpDialog) {
            AlertDialog(
                onDismissRequest = { showHelpDialog = false },
                title = { Text("Ayuda e Interacciones de Acuario") },
                text = {
                    Text(
                        "• Alimentar Peces: Toca el agua vacía para soltar alimento.\n\n" +
                                "• Ajolotes de Fondo: Caminan e interactúan sobre el lecho de piedras.\n\n" +
                                "• Susto de Tortuga: Toca una tortuga para asustarla y verla nadar velozmente.\n\n" +
                                "• Pirueta de Mantarraya: Toca una mantarraya para hacer un giro acrobático.\n\n" +
                                "• Ataque de Tiburón: Toca al tiburón para provocar su mordida feroz.\n\n" +
                                "• Medusa Eléctrica: Toca una medusa para activar chispas y relámpagos.\n\n" +
                                "• Tormenta de Burbujas: Sacude tu dispositivo para desatar una tormenta de burbujas."
                    )
                },
                confirmButton = {
                    TextButton(onClick = { showHelpDialog = false }) {
                        Text("Entendido")
                    }
                }
            )
        }
    }
}

@Composable
fun CreatureQuantitySelector(
    title: String,
    subtitle: String,
    count: Int,
    maxCount: Int,
    onCountSelected: (Int) -> Unit
) {
    Column {
        Text(text = title, fontWeight = FontWeight.Medium, fontSize = 14.sp)
        Text(text = subtitle, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            (0..maxCount).forEach { value ->
                FilterChip(
                    selected = count == value,
                    onClick = { onCountSelected(value) },
                    label = { Text(value.toString(), fontSize = 12.sp) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun ThemeSelectorChips(
    selectedMode: Int,
    onModeSelected: (Int) -> Unit
) {
    val options = listOf("Auto", "Claro", "Oscuro")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        options.forEachIndexed { index, label ->
            val isSelected = selectedMode == index
            val chipBg = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
            val chipText = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant

            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(chipBg, shape = RoundedCornerShape(8.dp))
                    .clickable { onModeSelected(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    color = chipText
                )
            }
        }
    }
}

@Composable
fun AccordionSection(
    title: String,
    icon: ImageVector,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    content: @Composable () -> Unit
) {
    val rotationAngle by animateFloatAsState(targetValue = if (isExpanded) 180f else 0f)

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(12.dp)
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(rotationAngle),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                content()
            }
        }
    }
}
