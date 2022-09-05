package no.nav.dagpenger.soknad

import com.spun.util.persistence.Loader
import org.approvaltests.Approvals
import org.approvaltests.core.Options
import org.approvaltests.namer.NamerWrapper
import java.nio.file.Paths

class PlantUmlObservatør() : SøknadObserver {
    private val tilstander = mutableListOf<String>()

    private companion object {
        val path = "${
        Paths.get("").toAbsolutePath().toString().substringBeforeLast("/")
        }/docs/arkitektur/"
        val options = Options()
            .forFile()
            .withExtension(".puml")
    }

    override fun søknadTilstandEndret(event: SøknadObserver.SøknadEndretTilstandEvent) {
        tilstander.add("${event.forrigeTilstand} --> ${event.gjeldendeTilstand.name}")
    }

    fun reset() {
        tilstander.clear()
    }

    fun toPlantUml(tittel: String): String =
        """
          |@startuml
          |title 
          |Søknadsflyt – flyt for $tittel
          |end title           
          |[*]-->${tilstander.førsteTilstand()}
          |${tilstander.joinToString("\n")}
          |${tilstander.sisteTilstand()}--> [*]
          |@enduml
      """.trimMargin()

    fun verify(tittel: String) {
        Approvals.namerCreater = Loader { NamerWrapper({ "tilstander/$tittel" }, { path }) }
        Approvals.verify(
            toPlantUml(tittel), options
        )
    }
}

private fun MutableList<String>.førsteTilstand(): String = this.first().substringBefore("--> ")
private fun MutableList<String>.sisteTilstand(): String = this.last().substringAfter("--> ")
