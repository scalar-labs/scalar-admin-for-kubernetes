FROM ghcr.io/scalar-labs/jre8:1.1.14

COPY build/libs/scalar-admin-for-kubernetes-cli*.jar /app.jar

RUN groupadd -r --gid 201 scalar && \
    useradd -r --uid 201 -g scalar scalar

USER 201

ENTRYPOINT ["java", "-jar", "/app.jar"]
