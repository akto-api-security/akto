FROM eclipse-temurin:17-jre-noble

# Ubuntu 24.04 LTS base (supported to 2029, ships NO python -> removes the amazoncorretto:17
# (Amazon Linux 2023) python 3.9 EOL finding). Scans grype-clean (0 crit/high) and already
# provides the tools start.sh needs (bash, coreutils, procps `free` for the cgroup fallback),
# so no package install/upgrade step is required.
#
# NOTE: Not yet tested on Red Hat (RHEL/Podman/OpenShift). The image ships its own Ubuntu
# userspace so host distro shouldn't matter, but a real Red Hat run is still unverified.

WORKDIR /app

COPY ./target/testing-1.0-SNAPSHOT-jar-with-dependencies.jar /app/testing-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh

RUN chmod +x /app/start.sh

CMD ["/app/start.sh"]
