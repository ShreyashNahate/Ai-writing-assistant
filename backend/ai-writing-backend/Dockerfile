FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY . .

RUN chmod +x gradlew

RUN ./gradlew build

EXPOSE 8080

CMD ["java", "-jar", "build/libs/ai-writing-backend-0.0.1-SNAPSHOT.jar"]
