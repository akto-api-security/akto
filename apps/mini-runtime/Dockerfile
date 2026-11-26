FROM eclipse-temurin:17-jre-resolute
WORKDIR /app

RUN apt-get update -y \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

# CVE-2026-91403: drop all eMudhra roots per openjdk.org/emudhra's 2026/10/24 update (now
# includes G1/G2, not just the compromised G3); fail build if any remain. Remove once Temurin's
# base image ships this itself.
RUN for a in cn_emsign_root_ca__g1,o_emudhra_technologies_limited,ou_emsign_pki,c_in cn_emsign_root_ca__g2,o_emudhra_technologies_limited,ou_emsign_pki,c_in cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in cn_emsign_ecc_root_ca__c3,o_emudhra_inc,ou_emsign_pki,c_us cn_emsign_root_ca__c1,o_emudhra_inc,ou_emsign_pki,c_us; do \
      keytool -delete -cacerts -storepass changeit -alias "$a [jdk]" || true; done; \
    ! keytool -list -v -cacerts -storepass changeit | grep -E "Owner: CN=emSign (ECC Root CA - (G3|C3)|Root CA - (G1|G2|C1))|Owner: CN=AAA Certificate Services,"

COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
# Run as a non-root user. Only /app (the working directory) and /var/log/app
# (start.sh's log file) need to be writable; the jar and start.sh stay root-owned. Group 0 gets
# write too, so an arbitrary UID with group 0 (OpenShift) also works.
RUN chmod +x /app/start.sh \
    && mkdir -p /var/log/app \
    && chown 65532:0 /app /var/log/app \
    && chmod 775 /app /var/log/app
USER 65532:65532
CMD ["/app/start.sh"]