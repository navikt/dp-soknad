# dp-soknad

1. Eier livsylus på søknad fra Påbegynt til Journalført.
2. Eier dokumentasjonkrav for søknader
3. Status for søknader og liste gitt bruker 

## Komme i gang

Gradle brukes som byggverktøy og er bundlet inn.

`./gradlew build`

## Profiling

Legg til i app manifest:

```
 - name: JAVA_OPTS
   value: -agentpath:/opt/cprof/profiler_java_agent.so=-cprof_service=dp-soknad,-cprof_enable_heap_sampling=true,-logtostderr,-minloglevel=0,-cprof_project_id=teamdagpenger-dev-885f
 ```

# Henvendelser

Spørsmål knyttet til koden eller prosjektet kan rettes mot:

* André Roaldseth, andre.roaldseth@nav.no
* Eller en annen måte for omverden å kontakte teamet på

## For NAV-ansatte

Interne henvendelser kan sendes via Slack i kanalen #dagpenger.
