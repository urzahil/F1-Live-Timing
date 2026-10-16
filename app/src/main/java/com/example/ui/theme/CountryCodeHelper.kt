package com.example.ui.theme

object CountryCodeHelper {

    fun getCountryCode(nationality: String?): String? {
        val raw = nationality?.trim() ?: return null
        val n = raw.lowercase()
        if (n.isBlank() || n == "null" || n == "none" || n == "---" || n == "--" || n == "-") {
            return null
        }
        return when {
            n.contains("dutch") || n.contains("netherland") || n == "nl" || n == "nld" -> "NED"
            n.contains("british") || n.contains("united kingdom") || n.contains("great britain") ||
                    n.contains("england") || n.contains("scot") || n.contains("welsh") || n == "gb" || n == "gbr" || n == "uk" -> "GBR"
            n.contains("italian") || n.contains("italy") || n == "it" || n == "ita" -> "ITA"
            n.contains("monegasque") || n.contains("monaco") || n == "mc" || n == "mco" || n == "mon" -> "MON"
            n.contains("spanish") || n.contains("spain") || n == "es" || n == "esp" -> "ESP"
            n.contains("australian") || n.contains("australia") || n == "au" || n == "aus" -> "AUS"
            n.contains("french") || n.contains("france") || n == "fr" || n == "fra" -> "FRA"
            n.contains("german") || n.contains("germany") || n == "de" || n == "deu" || n == "ger" -> "GER"
            n.contains("mexican") || n.contains("mexico") || n == "mx" || n == "mex" -> "MEX"
            n.contains("canadian") || n.contains("canada") || n == "ca" || n == "can" -> "CAN"
            n.contains("japanese") || n.contains("japan") || n == "jp" || n == "jpn" -> "JPN"
            n.contains("thai") || n.contains("thailand") || n == "th" || n == "tha" -> "THA"
            n.contains("finnish") || n.contains("finland") || n == "fi" || n == "fin" -> "FIN"
            n.contains("american") || n.contains("united states") || n.contains("usa") || n == "us" -> "USA"
            n.contains("brazilian") || n.contains("brazil") || n == "br" || n == "bra" -> "BRA"
            n.contains("argentine") || n.contains("argentina") || n == "ar" || n == "arg" -> "ARG"
            n.contains("danish") || n.contains("denmark") || n == "dk" || n == "dnk" || n == "den" -> "DEN"
            n.contains("chinese") || n.contains("china") || n == "cn" || n == "chn" -> "CHN"
            n.contains("new zealander") || n.contains("new zealand") || n == "nz" || n == "nzl" -> "NZL"
            n.contains("swiss") || n.contains("switzerland") || n == "ch" || n == "che" || n == "sui" -> "SUI"
            n.contains("austrian") || n.contains("austria") || n == "at" || n == "aut" -> "AUT"
            n.contains("belgian") || n.contains("belgium") || n == "be" || n == "bel" -> "BEL"
            n.contains("polish") || n.contains("poland") || n == "pl" || n == "pol" -> "POL"
            n.contains("irish") || n.contains("ireland") || n == "ie" || n == "irl" -> "IRL"
            n.contains("swedish") || n.contains("sweden") || n == "se" || n == "swe" -> "SWE"
            n.contains("south african") || n.contains("south africa") || n == "za" || n == "rsa" -> "RSA"
            n.contains("colombian") || n.contains("colombia") || n == "co" || n == "col" -> "COL"
            n.contains("venezuelan") || n.contains("venezuela") || n == "ve" || n == "ven" -> "VEN"
            n.contains("indian") || n.contains("india") || n == "in" || n == "ind" -> "IND"
            n.contains("portuguese") || n.contains("portugal") || n == "pt" || n == "por" -> "POR"
            n.contains("norwegian") || n.contains("norway") || n == "no" || n == "nor" -> "NOR"
            n.contains("russian") || n.contains("russia") -> "AIN"
            n.length == 3 && n.all { it.isLetter() } -> n.uppercase()
            raw.length >= 3 && !n.startsWith("null") && raw.take(3).all { it.isLetter() } -> raw.take(3).uppercase()
            else -> null
        }
    }
}
