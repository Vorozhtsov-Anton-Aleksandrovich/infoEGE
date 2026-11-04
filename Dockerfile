# Сборка
FROM eclipse-temurin:21-jdk AS build
WORKDIR /build
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

# Запуск
FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 app && mkdir -p /app/data/files && chown -R app /app/data
WORKDIR /app
COPY --from=build /build/target/infoege.jar app.jar
USER app
ENV STORAGE_DIR=/app/data/files
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
