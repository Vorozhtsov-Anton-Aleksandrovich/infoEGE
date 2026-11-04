# Сборка: зависимости кэшируются отдельным слоем, исходники копируются после.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Запуск: только JRE и jar, без root.
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app && mkdir -p /app/data/files && chown -R app /app/data
WORKDIR /app
COPY --from=build /build/target/infoege.jar app.jar
USER app
ENV STORAGE_DIR=/app/data/files \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
