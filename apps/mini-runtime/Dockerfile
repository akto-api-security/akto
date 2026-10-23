FROM eclipse-temurin:17-jre-resolute
WORKDIR /app

RUN apt-get update -y \
    && apt-get upgrade -y \
    && rm -rf /var/lib/apt/lists/*

# CVE-2026-91403: drop the eMudhra roots Temurin 17.0.21 removed; fail build if any remain. Remove once on 17.0.21+.
RUN for a in cn_emsign_ecc_root_ca__g3,o_emudhra_technologies_limited,ou_emsign_pki,c_in cn_emsign_ecc_root_ca__c3,o_emudhra_inc,ou_emsign_pki,c_us cn_emsign_root_ca__c1,o_emudhra_inc,ou_emsign_pki,c_us; do \
      keytool -delete -cacerts -storepass changeit -alias "$a [jdk]" || true; done; \
    ! keytool -list -v -cacerts -storepass changeit | grep -E "Owner: CN=(emSign ECC Root CA - (G3|C3)|emSign Root CA - C1|AAA Certificate Services),"

COPY ./target/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar /app/mini-runtime-1.0-SNAPSHOT-jar-with-dependencies.jar
COPY start.sh /app/start.sh
RUN chmod +x /app/start.sh
CMD ["/app/start.sh"]