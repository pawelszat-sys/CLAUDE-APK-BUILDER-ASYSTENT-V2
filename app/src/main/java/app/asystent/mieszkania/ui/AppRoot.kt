package app.asystent.mieszkania.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.asystent.mieszkania.Hub

private class Tab(val label: String, val icon: String)

private val tabs = listOf(
    Tab("Start", Ic.Home),
    Tab("Pogoda", Ic.Cloud),
    Tab("Mieszkanie", Ic.Grid),
    Tab("Światło", Ic.Bulb),
    Tab("Asystent", Ic.Mic),
)

private const val SETTINGS = 5

@Composable
private fun TopBar(onSettings: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(9.dp).background(Palette.Signal, CircleShape))
        Text(
            "Asystent Mieszkania",
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = Palette.Frost,
            modifier = Modifier.weight(1f).padding(start = 10.dp),
        )
        Box(
            Modifier
                .size(40.dp)
                .background(Palette.Panel, CircleShape)
                .border(1.dp, Palette.Line, CircleShape)
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center,
        ) {
            PathIcon(Ic.Tune, Palette.Slate, 22.dp)
        }
    }
}

@Composable
fun AppRoot(vm: Hub) {
    val kiosk by vm.kiosk.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }

    if (kiosk) {
        KioskScreen(vm)
        return
    }

    Scaffold(containerColor = Palette.Ink) { pad ->
        BoxWithConstraints(Modifier.padding(pad).fillMaxSize()) {
            val wide = maxWidth > maxHeight * 1.25f
            val screen: @Composable () -> Unit = {
                when (tab) {
                    0 -> StartScreen(vm)
                    1 -> WeatherScreen(vm)
                    2 -> HomeScreen(vm)
                    3 -> LightScreen(vm)
                    4 -> AssistantScreen(vm)
                    else -> SettingsScreen(vm)
                }
            }
            if (wide) {
                Row(Modifier.fillMaxSize()) {
                    NavigationRail(containerColor = Palette.Panel) {
                        tabs.forEachIndexed { i, t ->
                            NavigationRailItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { PathIcon(t.icon, if (tab == i) Palette.OnLamp else Palette.Slate) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedTextColor = Palette.Frost,
                                    unselectedTextColor = Palette.Slate,
                                    indicatorColor = Palette.Lamp,
                                ),
                            )
                        }
                    }
                    Column(Modifier.weight(1f)) {
                        TopBar { tab = SETTINGS }
                        Box(Modifier.weight(1f)) { screen() }
                    }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    TopBar { tab = SETTINGS }
                    Box(Modifier.weight(1f)) { screen() }
                    NavigationBar(containerColor = Palette.Panel) {
                        tabs.forEachIndexed { i, t ->
                            NavigationBarItem(
                                selected = tab == i,
                                onClick = { tab = i },
                                icon = { PathIcon(t.icon, if (tab == i) Palette.OnLamp else Palette.Slate) },
                                label = { Text(t.label, fontSize = 11.sp) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedTextColor = Palette.Frost,
                                    unselectedTextColor = Palette.Slate,
                                    indicatorColor = Palette.Lamp,
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}
