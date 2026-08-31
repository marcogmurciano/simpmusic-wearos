###############################################################################
# Reglas derivadas del PR #1864 de SimpMusic (Wear OS: artwork + volumen)
# Solo las que aplican a esta app: aqui NO se usa DelegatingForwardingPlayer.
###############################################################################

# Callbacks de Player.Listener: sin esto R8 los borra por "no usados" y la UI
# deja de recibir cambios de pista, metadatos y volumen.
-keepclassmembers class * implements androidx.media3.common.Player$Listener {
    void onMediaItemTransition(androidx.media3.common.MediaItem, int);
    void onMediaMetadataChanged(androidx.media3.common.MediaMetadata);
    void onAvailableCommandsChanged(androidx.media3.common.Player$Commands);
    void onVolumeChanged(float);
}

# Campos de artwork: el fallo exacto que mato al PR #1864 (carátulas y metadatos
# correctos en debug, rotos en release).
-keepclassmembers class androidx.media3.common.MediaMetadata {
    *** artworkData;
    *** artworkUri;
}

# Callbacks de sesion, que instancia el framework indirectamente.
-keepclassmembers class * extends androidx.media3.session.MediaSession$Callback {
    *;
}

# Corrutinas: descubrimiento de dispatcher y manejo de excepciones.
-keep class kotlinx.coroutines.internal.MainDispatcherFactory
-keep class kotlinx.coroutines.CoroutineExceptionHandler

###############################################################################
# Rhino (motor JS que usa el scraper para descifrar las firmas de YouTube)
# Referencia java.beans.*, que no existe en Android. Sin esto R8 ni compila.
###############################################################################
-dontwarn java.beans.BeanDescriptor
-dontwarn java.beans.BeanInfo
-dontwarn java.beans.IntrospectionException
-dontwarn java.beans.Introspector
-dontwarn java.beans.PropertyDescriptor

###############################################################################
# kotlinx.serialization — IMPRESCINDIBLE
#
# El scraper parsea toda la respuesta de YouTube con kotlinx.serialization, pero
# su `consumer-rules.pro` esta VACIO (0 bytes) y el modulo no declara
# consumerProguardFiles, asi que sus reglas NO llegan aqui. Sin estas, R8 borra
# los serializadores y la busqueda deja de funcionar solo en release.
# Copiadas de core/service/kotlinYtmusicScraper/proguard-rules.pro.
###############################################################################

-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**

# Campo `Companion` de las clases serializables.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# `serializer()` en los companion objects.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# `INSTANCE.serializer()` de los objetos serializables.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# Los propios modelos del scraper.
-keep,includedescriptorclasses class com.maxrave.kotlinytmusicscraper.**$$serializer { *; }
-keepclassmembers class com.maxrave.kotlinytmusicscraper.** {
    *** Companion;
}

###############################################################################
# Ktor y OkHttp (cliente HTTP del scraper)
###############################################################################
-dontwarn org.slf4j.**
-dontwarn okhttp3.**
-dontwarn okio.**
-keepclassmembers class io.ktor.** { *; }
