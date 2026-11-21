package app.punktgenau.domain

/** Liest den gespeicherten Namen einer Altersklasse; unbekannt oder fehlend ergibt Erwachsen. */
fun parseAgeGroup(name: String?): AgeGroup = AgeGroup.entries.firstOrNull { it.name == name } ?: AgeGroup.ADULT

/** Anzeigename der Altersklasse für die Auswahl. */
val AgeGroup.label: String
    get() = when (this) {
        AgeGroup.ADULT -> "Erwachsen"
        AgeGroup.UNDER_21 -> "unter 21"
        AgeGroup.UNDER_16 -> "unter 16"
    }
