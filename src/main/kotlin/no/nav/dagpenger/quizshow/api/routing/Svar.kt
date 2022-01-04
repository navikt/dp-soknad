package no.nav.dagpenger.quizshow.api.routing

import BadRequestException

data class Svar(val type: String, val svar: Any) {

    fun valider() {
        if (type == "valg") {
            svar as List<*>
            if (svar.isEmpty()) {
                throw BadRequestException("Svar må alltid inneholde ett eller flere svaralternativer")
            }
        }
    }
}
