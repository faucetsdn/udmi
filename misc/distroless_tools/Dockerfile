FROM openjdk:17-jdk-slim AS build-env
COPY . /app/
WORKDIR /app
RUN /app/validator/bin/build

FROM gcr.io/distroless/java17-debian12
WORKDIR /app
COPY --from=build-env /app/validator/build/libs/ /app/
COPY gencode/java/udmi/schema /app/schema
CMD ["validator-1.0-SNAPSHOT-all.jar"]
