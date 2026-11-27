package app.punktgenau.data

/**
 * Landesverbände im click-TT-Verbund, Kürzel wie in den Website-Adressen (`/click-tt/<Kürzel>/…`).
 * Quelle: Seite „Verbände“ der Website; BeTTV (Berlin) steht dort nicht, taucht aber in der Wechselliste auf.
 */
object Verbaende {
    val alle: List<Pair<String, String>> = listOf(
        "BaTTV" to "Baden",
        "TTBW" to "Baden-Württemberg",
        "ByTTV" to "Bayern",
        "BeTTV" to "Berlin",
        "TTVB" to "Brandenburg",
        "FTTB" to "Bremen",
        "HaTTV" to "Hamburg",
        "HeTTV" to "Hessen",
        "TTVMV" to "Mecklenburg-Vorpommern",
        "TTVN" to "Niedersachsen",
        "WTTV" to "Nordrhein-Westfalen",
        "PTTV" to "Pfalz",
        "RTTVR" to "Rheinland/Rheinhessen",
        "STTB" to "Saarland",
        "TTVSA" to "Sachsen-Anhalt",
        "TTTV" to "Thüringen",
    )

    /** Anzeigename zu einem Kürzel; unbekannte Kürzel bleiben unverändert. */
    fun name(code: String?): String = alle.firstOrNull { it.first == code }?.second ?: code ?: "Verband"

    /** Standard, wenn der Verband des eigenen Kontos nicht bekannt ist. */
    const val STANDARD = "RTTVR"
}
