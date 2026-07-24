FROM amazoncorretto:17-al2023
WORKDIR /app

# AL2023 pins dnf to a fixed repo snapshot (reproducibility), so `dnf upgrade` alone
# won't pull real fixes - override with --releasever=<dated-version> instead.
#
# WARNING: never use --releasever=latest. AWS explicitly warns against this
# (https://docs.aws.amazon.com/linux/al2023/ug/updating.html): it's a moving target
# that could jump to an untested major version on the next rebuild.
#
# Bump via: dnf check-release-update (run inside the image)
ARG RELEASEVER=2023.12.20260720

RUN dnf upgrade -y --releasever=${RELEASEVER} \
    && dnf install -y --releasever=${RELEASEVER} procps bind-utils \
    && dnf clean all

COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]