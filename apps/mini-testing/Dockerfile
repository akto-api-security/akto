FROM amazoncorretto:17-al2023

# Base image pins dnf to a fixed AL2023 repo snapshot for reproducibility, which lags behind
# published ALAS fixes. Pin to a specific newer dated release (not "latest" - see
# https://docs.aws.amazon.com/linux/al2023/ug/updating.html) to pull in patched packages
# deterministically. Check for a newer version with: dnf check-release-update
RUN dnf upgrade -y --releasever=2023.12.20260720 glib2 krb5-libs libacl python3 python3-libs \
    && dnf clean all
WORKDIR /app
COPY ./target/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar
CMD "java" "-XX:+ExitOnOutOfMemoryError" "-jar" "/app/mini-testing-1.0-SNAPSHOT-jar-with-dependencies.jar"
