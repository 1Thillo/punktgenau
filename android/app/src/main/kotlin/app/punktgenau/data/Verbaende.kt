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

/**
 * Regionen des RTTVR für den Filter der Wechselliste (Parameter `region`). Der Oberbereich steht für alle
 * Kreise. Geprüft sind der Oberbereich und Koblenz/Neuwied (.01) per Klick; die übrigen Kreise folgen der
 * Reihenfolge im Menü der Website. Für andere Verbände sind die Codes nicht bekannt.
 */
object RttvrRegionen {
    val alle: List<Pair<String, String>> = listOf(
        "DE.SW.R7.12" to "Rheinland-Rheinhessen",
        "DE.SW.R7.12.01" to "Koblenz/Neuwied",
        "DE.SW.R7.12.02" to "Ahrweiler/Mayen-Cochem-Zell",
        "DE.SW.R7.12.03" to "Nördlicher Westerwald/Altenkirchen",
        "DE.SW.R7.12.04" to "Südlicher Westerwald/Rhein-Lahn",
        "DE.SW.R7.12.05" to "Kreuznach/Birkenfeld",
        "DE.SW.R7.12.06" to "Rhein-Hunsrück",
        "DE.SW.R7.12.07" to "Trier/Wittlich",
        "DE.SW.R7.12.08" to "Eifel",
        "DE.SW.R7.12.09" to "Bingen",
        "DE.SW.R7.12.10" to "Mainz",
        "DE.SW.R7.12.11" to "Alzey",
        "DE.SW.R7.12.12" to "Worms",
    )

    fun name(code: String?): String = alle.firstOrNull { it.first == code }?.second ?: "Alle Regionen"
}
