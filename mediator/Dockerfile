FROM eclipse-temurin:17 as jre-build

# Create a custom Java runtime
RUN $JAVA_HOME/bin/jlink \
         --add-modules ALL-MODULE-PATH \
         --strip-debug \
         --no-man-pages \
         --no-header-files \
         --compress=2 \
         --output /javaruntime

# agents
FROM debian:buster-slim as agents
RUN apt-get update && apt-get install -y wget && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /opt/cprof && \
  wget -q -O- https://storage.googleapis.com/cloud-profiler/java/latest/profiler_java_agent.tar.gz \
  | tar xzv -C /opt/cprof

ARG JAVA_OTEL_VERSION=v1.32.0
RUN mkdir -p /opt/tracing && \
  wget -q -O- https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/$JAVA_OTEL_VERSION/opentelemetry-javaagent.jar \
    -O /opt/tracing/opentelemetry-javaagent.jar


# Runtime
FROM debian:buster-slim

ENV TZ="Europe/Oslo"
ENV JAVA_HOME=/opt/java/openjdk
ENV PATH "${JAVA_HOME}/bin:${PATH}"

COPY --from=jre-build /javaruntime $JAVA_HOME
COPY --from=agents /opt/cprof /opt/cprof
COPY --from=agents /opt/tracing/opentelemetry-javaagent.jar /opt/tracing/opentelemetry-javaagent.jar

COPY build/install/* /

USER nobody
CMD ["mediator"]