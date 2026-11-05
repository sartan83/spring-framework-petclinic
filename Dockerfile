# syntax=docker/dockerfile:1

# ---- Build stage: compile and package the WAR with Java 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
# Optional Maven repository mirror (e.g. a corporate Nexus/Artifactory proxy of Maven Central)
ARG MAVEN_MIRROR_URL=""
RUN if [ -n "$MAVEN_MIRROR_URL" ]; then \
      mkdir -p /root/.m2 && printf '<settings><mirrors><mirror><id>mirror</id><mirrorOf>*</mirrorOf><url>%s</url></mirror></mirrors></settings>' "$MAVEN_MIRROR_URL" > /root/.m2/settings.xml; \
    fi
COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2/repository mvn -B -ntp package -DskipTests

# ---- Runtime stage: Jetty 12 (Jakarta EE 11) on JDK 21 ----
FROM jetty:12-jdk21
RUN java -jar "$JETTY_HOME/start.jar" --add-modules=ee11-deploy,ee11-annotations,ee11-jsp
COPY --from=build --chown=jetty:jetty /workspace/target/petclinic.war /var/lib/jetty/webapps/ROOT.war
ENV MONGODB_URI=mongodb://mongo:27017/petclinic?replicaSet=rs0
EXPOSE 8080
