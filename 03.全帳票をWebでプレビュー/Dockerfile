FROM eclipse-temurin:21-jdk AS build
WORKDIR /work
COPY ["03.全帳票をWebでプレビュー/", "./"]
COPY common/database/sample.db /data/sample.db
RUN sh ./gradlew installDist --no-daemon
FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends curl fontconfig fonts-noto-cjk && rm -rf /var/lib/apt/lists/*
COPY --from=build /work/build/install/reports-java-native-web-sample/ ./
COPY --from=build /work/resources ./resources
COPY --from=build /data/sample.db /data/sample.db
ENV REPORTS_SAMPLE_DB=/data/sample.db
EXPOSE 8080
ENTRYPOINT ["bin/reports-java-native-web-sample"]
