package com.simpmusic.wear

import com.google.android.horologist.media.data.repository.PlayerRepositoryImpl
import com.google.android.horologist.media.repository.PlayerRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Verifica los supuestos sobre la API de Horologist en los que se apoya el ADR 0003.
 * Si una actualización de Horologist los rompe, estos tests lo dicen antes que el reloj.
 */
class PlaybackConnectionContractTest {

    @Test
    fun `PlayerRepositoryImpl implementa la interfaz que consume la UI`() {
        // Arrange / Act
        val repository: PlayerRepository = PlayerRepositoryImpl()

        // Assert
        assertTrue(repository is PlayerRepositoryImpl)
    }

    @Test
    fun `un repositorio recien creado se reporta como no conectado`() {
        // Arrange
        val repository = PlayerRepositoryImpl()

        // Act
        val connected = repository.connected.value

        // Assert: la UI debe poder distinguir "aun no hay servicio" de "pausado"
        assertFalse(connected)
    }

    @Test
    fun `connect acepta cualquier Player de Media3`() {
        // Assert: la firma connect(Player, () -> Unit) es la base del puente (ADR 0003).
        // Si Horologist la cambiara, esto deja de compilar y falla el build, que es el aviso.
        val connectMethod = PlayerRepositoryImpl::class.java.methods
            .firstOrNull { it.name == "connect" }

        assertNotNull("PlayerRepositoryImpl debe exponer connect()", connectMethod)
        assertEquals(
            "connect() debe aceptar androidx.media3.common.Player, no un tipo concreto",
            "androidx.media3.common.Player",
            connectMethod!!.parameterTypes.first().name,
        )
    }
}
