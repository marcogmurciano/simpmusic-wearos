package com.simpmusic.wear.tile

import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.DeviceParametersBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Chip
import androidx.wear.protolayout.material.ChipColors
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.protolayout.material.layouts.PrimaryLayout
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.simpmusic.wear.MainActivity
import com.simpmusic.wear.library.Downloads

private const val VERSION_RECURSOS = "1"

/**
 * Tile de acceso rapido: se llega deslizando desde la esfera, sin abrir la app.
 *
 * Muestra cuantas canciones hay descargadas —el dato que importa antes de salir a
 * correr— y abre la app de un toque.
 */
class SimpMusicTileService : TileService() {

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        Futures.immediateFuture(
            ResourceBuilders.Resources.Builder().setVersion(VERSION_RECURSOS).build(),
        )

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> {
        val descargadas = Downloads(this).descargadas().size
        val subtitulo = when (descargadas) {
            0 -> "Sin descargas"
            1 -> "1 cancion sin conexion"
            else -> "$descargadas canciones sin conexion"
        }

        val layout = PrimaryLayout.Builder(requestParams.deviceConfiguration)
            .setResponsiveContentInsetEnabled(true)
            .setPrimaryLabelTextContent(
                Text.Builder(this, "SimpMusic")
                    .setTypography(Typography.TYPOGRAPHY_CAPTION1)
                    .setColor(androidx.wear.protolayout.ColorBuilders.argb(0xFFFFFFFF.toInt()))
                    .build(),
            )
            .setContent(
                Chip.Builder(this, abrirApp(), requestParams.deviceConfiguration)
                    .setPrimaryLabelContent("Escuchar")
                    .setSecondaryLabelContent(subtitulo)
                    .setChipColors(ChipColors.primaryChipColors(DEFAULT_COLORS))
                    .build(),
            )
            .build()

        return Futures.immediateFuture(
            TileBuilders.Tile.Builder()
                .setResourcesVersion(VERSION_RECURSOS)
                // El tile se regenera al mostrarse; no hace falta refresco periodico,
                // que en un reloj cuesta bateria.
                .setTileTimeline(
                    TimelineBuilders.Timeline.fromLayoutElement(layout),
                )
                .build(),
        )
    }

    private fun abrirApp() = ModifiersBuilders.Clickable.Builder()
        .setId("abrir")
        .setOnClick(
            ActionBuilders.LaunchAction.Builder()
                .setAndroidActivity(
                    ActionBuilders.AndroidActivity.Builder()
                        .setPackageName(packageName)
                        .setClassName(MainActivity::class.java.name)
                        .build(),
                )
                .build(),
        )
        .build()

    private companion object {
        val DEFAULT_COLORS = androidx.wear.protolayout.material.Colors(
            /* primary = */ 0xFFD0BCFF.toInt(),
            /* onPrimary = */ 0xFF381E72.toInt(),
            /* surface = */ 0xFF1C1B1F.toInt(),
            /* onSurface = */ 0xFFE6E1E5.toInt(),
        )
    }
}
