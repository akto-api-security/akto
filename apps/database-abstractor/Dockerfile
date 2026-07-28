FROM eclipse-temurin:17-jre-noble

# Ubuntu 24.04 LTS base (supported to 2029, ships NO python -> no python 2.7/3.9 EOL findings).
# The base already provides curl/tar/gzip/free/useradd and scans grype-clean (0 crit/high),
# so no apt install/upgrade is needed. (libpcap-dev from the old image was unused cruft —
# db-abstractor has no pcap dependency.)

# Jetty 12, EE8 (javax.servlet) environment. 12.0.37 >= 12.0.33 fixes the jetty-http
# request-smuggling CVE (GHSA-355h-qmc2-wpwf) with no fix in the Jetty 9.4 line, and the
# jetty-security digest-auth issue (GHSA-2fvj-hgj9-j2gr).
ENV JETTY_VERSION=12.0.37
ENV JETTY_HOME=/opt/jetty-home
ENV JETTY_BASE=/opt/jetty

RUN curl -sSL -o /tmp/jetty-home.tar.gz https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz \
    && mkdir -p ${JETTY_HOME} \
    && tar -xzf /tmp/jetty-home.tar.gz -C ${JETTY_HOME} --strip-components=1 \
    && rm -f /tmp/jetty-home.tar.gz

# EE8 (javax.servlet) modules for the Struts 6 webapp. Only ee8-deploy is enabled, so
# webapps/*.war deploy under the ee8 environment.
#   ee8-deploy(+ee8-webapp/ee8-servlet) : deploy + servlet container for javax webapps
#   ee8-annotations                     : SCI / annotation scanning (Struts)
#   ee8-jsp                             : provides the javax.servlet.jsp API — Struts 6 tag/view
#                                         classes reference it even with no .jsp files present
#   gzip                                : server-side response compression (replaces removed GzipFilter)
#   forwarded                           : honour X-Forwarded-* from the reverse proxy
RUN mkdir -p ${JETTY_BASE} && cd ${JETTY_BASE} \
    && java -jar ${JETTY_HOME}/start.jar --approve-all-licenses \
         --add-modules=server,http,forwarded,ee8-deploy,ee8-annotations,ee8-jsp,gzip

# Deploy the db-abstractor war at the root context (root.war -> "/").
ADD ./target/database-abstractor.war ${JETTY_BASE}/webapps/root.war

# Do not leak the Jetty version in the Server response header.
RUN echo "jetty.httpConfig.sendServerVersion=false" >> ${JETTY_BASE}/start.d/server.ini

RUN useradd jetty && chown -R jetty:jetty ${JETTY_HOME} ${JETTY_BASE}

ENV JAVA_OPTIONS="-XX:+ExitOnOutOfMemoryError"
WORKDIR ${JETTY_BASE}
EXPOSE 9000

COPY set_xmx.sh /app/set_xmx.sh
RUN chmod +x /app/set_xmx.sh
USER jetty
CMD ["/app/set_xmx.sh"]
