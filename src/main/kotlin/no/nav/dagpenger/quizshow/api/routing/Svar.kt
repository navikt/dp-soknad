package no.nav.dagpenger.quizshow.api.routing

data class Svar(val type: String, val svar: Any) {
    fun valider() {
        if (type == "valg") {
            val valgteSvaralternativer = svar as List<*>
            if (valgteSvaralternativer.isEmpty()) {
                throw BadRequestException("Svar må alltid inneholde ett eller flere svaralternativer")
            }
        }
    }
}
