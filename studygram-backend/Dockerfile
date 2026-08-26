# StudyGram backend container
#
# A two-stage build. The first stage has the JDK and Maven and produces a jar;
# the second stage keeps only that jar and a Java runtime.
#
# WHY TWO STAGES
#
# The build needs a full JDK, Maven, and every downloaded dependency - roughly
# 600MB of tooling that has no purpose once the jar exists. Copying just the jar
# into a fresh runtime image leaves all of that behind, so the deployed image is
# a few hundred megabytes rather than nearly a gigabyte. Smaller images push
# faster, start faster, and expose less.

# ---------------------------------------------------------------- build ----
FROM eclipse-temurin:17-jdk AS build

WORKDIR /app

# Copy ONLY the build descriptor first, then download dependencies.
#
# Docker caches each step and reuses it while its inputs are unchanged. Because
# the source has not been copied yet, editing a Java file does not invalidate
# this layer - so a normal code change reuses the cached dependencies instead of
# re-downloading them. Copying everything up front would throw that cache away
# on every single commit.
COPY .mvn/ .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

# Now the source, which changes constantly.
COPY src ./src

# -DskipTests because tests already ran in development and in CI. A deploy
# should build the artifact, not re-litigate whether the code works.
RUN ./mvnw -B clean package -DskipTests

# ------------------------------------------------------------- runtime ----
# JRE, not JDK: running Java needs a runtime, not a compiler.
FROM eclipse-temurin:17-jre

WORKDIR /app

# Run as a non-root user.
#
# If anything ever escapes the application, it lands as an unprivileged account
# rather than as root inside the container. Containers default to root, which is
# more privilege than a web server has any use for.
RUN useradd --create-home --shell /bin/bash studygram
USER studygram

COPY --from=build /app/target/*.jar app.jar

# Documentation only - it does not publish anything. The real port comes from
# the PORT environment variable at runtime (see application.properties).
EXPOSE 8080

# MaxRAMPercentage rather than a fixed -Xmx.
#
# The JVM's default heap sizing assumes it owns the machine, which is wrong in a
# container with a hard memory limit - it will happily size a heap larger than
# the limit and get killed by the OOM killer. This tells it to use 75% of
# whatever the container was actually given, leaving room for the JVM's own
# non-heap memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
