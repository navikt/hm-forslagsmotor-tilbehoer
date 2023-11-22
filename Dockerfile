FROM gcr.io/distroless/java17-debian12:latest
COPY /build/libs/hm-forslagsmotor-tilbehoer-1.0-SNAPSHOT-all.jar /app.jar
ENV TZ="Europe/Oslo"
CMD ["/app.jar"]
