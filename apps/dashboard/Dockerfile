FROM eclipse-temurin:17-jre-noble

# NOTE: Not yet tested on Red Hat (RHEL/Podman/OpenShift). Verified only under Docker on a
# cgroup-v2 host (a faithful proxy for RHEL 9's runtime constraints), not on an actual Red Hat
# host. The image ships its own Ubuntu userspace so host distro shouldn't matter, but a real
# RHEL/Podman (and especially OpenShift arbitrary-UID + SELinux volume) run is still unverified.

# Ubuntu 24.04 LTS base (supported to 2029, ships NO python -> no python 2.7/3.9 EOL findings).
# Replaces the old jetty:9.4-jre8 image + JRE-17 overlay hack. The base already provides
# curl/tar/gzip/free/useradd and scans grype-clean (0 crit/high), so no apt install/upgrade is
# needed (also avoids the flaky arm64 ports.ubuntu.com mirror).

# Jetty 12, EE8 (javax.servlet) environment. 12.0.37 >= 12.0.33 fixes the jetty-http
# request-smuggling CVE (GHSA-355h-qmc2-wpwf) that has no fix in the Jetty 9.4 line.
ENV JETTY_VERSION=12.0.37
ENV JETTY_HOME=/opt/jetty-home
ENV JETTY_BASE=/opt/jetty

RUN curl -sSL -o /tmp/jetty-home.tar.gz https://repo1.maven.org/maven2/org/eclipse/jetty/jetty-home/${JETTY_VERSION}/jetty-home-${JETTY_VERSION}.tar.gz \
    && mkdir -p ${JETTY_HOME} \
    && tar -xzf /tmp/jetty-home.tar.gz -C ${JETTY_HOME} --strip-components=1 \
    && rm -f /tmp/jetty-home.tar.gz

# Provision a jetty.base with only the EE8 (javax.servlet) modules the Struts 6 webapp needs.
# Only ee8-deploy is enabled (no ee9/ee10), so webapps/*.war deploy under the ee8 environment.
#   ee8-deploy(+ee8-webapp/ee8-servlet) : deploy + servlet container for javax webapps
#   ee8-annotations                     : @WebServlet/@WebFilter + SCI scanning (Struts)
#   ee8-jsp                             : JSP support (web.xml error page /pages/error.jsp; uses bundled ecj, no javac needed)
#   gzip                                : server-side response compression (replaces removed GzipFilter)
#   forwarded                           : honour X-Forwarded-* from the reverse proxy
RUN mkdir -p ${JETTY_BASE} && cd ${JETTY_BASE} \
    && java -jar ${JETTY_HOME}/start.jar --approve-all-licenses \
         --add-modules=server,http,forwarded,ee8-deploy,ee8-annotations,ee8-jsp,gzip

# Deploy the dashboard war at the root context (root.war -> "/").
ADD ./target/dashboard.war ${JETTY_BASE}/webapps/root.war

# Do not leak the Jetty version in the Server response header.
RUN echo "jetty.httpConfig.sendServerVersion=false" >> ${JETTY_BASE}/start.d/server.ini

# gzip control knobs (response mime types + methods), restored from the old web.xml GzipFilter
# + jetty-web.xml GzipHandler. Overwrites the module's default gzip.ini.
COPY gzip.ini ${JETTY_BASE}/start.d/gzip.ini

RUN useradd jetty && chown -R jetty:jetty ${JETTY_HOME} ${JETTY_BASE}

ENV JAVA_OPTIONS="-XX:+ExitOnOutOfMemoryError"
WORKDIR ${JETTY_BASE}
EXPOSE 8080

COPY set_xmx.sh /app/set_xmx.sh
RUN chmod +x /app/set_xmx.sh
USER jetty
CMD ["/app/set_xmx.sh"]
