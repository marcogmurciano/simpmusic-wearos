package com.simpmusic.wear.tile

import android.app.PendingIntent
import android.content.Intent
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationText
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import com.simpmusic.wear.MainActivity
import com.simpmusic.wear.R
import com.simpmusic.wear.library.Downloads

/**
 * Complicacion para la esfera: cuantas canciones hay descargadas, y un toque abre la app.
 *
 * Es el atajo real para salir a correr: se ve en la esfera sin navegar a ningun sitio.
 */
class DescargasComplicationService : ComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? =
        if (type != ComplicationType.SHORT_TEXT) null
        else construir(numero = 12, descripcion = "12 canciones descargadas")

    override fun onComplicationRequest(
        request: ComplicationRequest,
        listener: ComplicationRequestListener,
    ) {
        if (request.complicationType != ComplicationType.SHORT_TEXT) {
            listener.onComplicationData(null)
            return
        }
        val n = Downloads(this).descargadas().size
        listener.onComplicationData(
            construir(
                numero = n,
                descripcion = if (n == 0) "Sin canciones descargadas" else "$n canciones descargadas",
            ),
        )
    }

    private fun construir(numero: Int, descripcion: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(numero.toString()).build(),
            contentDescription = PlainComplicationText.Builder(descripcion).build(),
        )
            .setMonochromaticImage(
                MonochromaticImage.Builder(
                    android.graphics.drawable.Icon.createWithResource(
                        this, R.drawable.ic_launcher_foreground,
                    ),
                ).build(),
            )
            .setTapAction(abrirApp())
            .build()

    private fun abrirApp(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}
