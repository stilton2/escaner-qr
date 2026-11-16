package es.kalvo.escanerqr.wifis.nucleo

/** Who saved a network, from the creator name Android keeps (`android.uid.system:1000`, a package name…). */
data class Origen(val tipo: Tipo, val paquete: String?, val crudo: String?) {
    enum class Tipo { AJUSTES, PANEL, SHELL, SISTEMA, APP, DESCONOCIDO }

    /** Kept for the tests of the list; the screens no longer treat QR Bot apart (S-J5: «De otras apps»). */
    val esQrBot get() = paquete == QR_BOT

    companion object {
        const val QR_BOT = "com.teacapps.barcodescanner"

        /**
         * Apps whose name on the phone does not say which one they are (e.g. several scanners are called «Escáner QR»).
         * General knowledge, not anyone's data; any other app shows the name Android gives it.
         */
        val NOMBRES_CONOCIDOS = mapOf(QR_BOT to "QR Bot")

        fun desde(creador: String?): Origen {
            val c = creador?.trim().orEmpty()
            val tipo = when {
                c.isEmpty() || c == "null" -> Tipo.DESCONOCIDO
                c.startsWith("android.uid.system:") || c == "com.android.settings" -> Tipo.AJUSTES
                c.startsWith("android.uid.systemui") || c == "com.android.systemui" -> Tipo.PANEL
                c.startsWith("android.uid.shell") || c == "com.android.shell" -> Tipo.SHELL
                c.startsWith("android.uid.") || c.startsWith("android.media") -> Tipo.SISTEMA
                else -> Tipo.APP
            }
            return Origen(tipo, if (tipo == Tipo.APP) c.substringBefore(':') else null, creador)
        }
    }
}
