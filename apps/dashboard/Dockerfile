# Use Amazon Linux 2023 as the base image
FROM amazonlinux:2023.5.20240701.0

# Switch to root user
USER root

# Set up the yum repository for Nginx
RUN mkdir -p /etc/yum.repos.d && \
    echo -e "[nginx-stable]\n\
name=nginx stable repo\n\
baseurl=http://nginx.org/packages/amzn/2023/\$basearch/\n\
gpgcheck=1\n\
enabled=1\n\
gpgkey=https://nginx.org/keys/nginx_signing.key\n\
module_hotfixes=true\n\
priority=9\n\
\n\
[nginx-mainline]\n\
name=nginx mainline repo\n\
baseurl=http://nginx.org/packages/mainline/amzn/2023/\$basearch/\n\
gpgcheck=1\n\
enabled=0\n\
gpgkey=https://nginx.org/keys/nginx_signing.key\n\
module_hotfixes=true\n\
priority=9\n" > /etc/yum.repos.d/nginx.repo

# Install Nginx and nginx-module-njs
RUN yum install -y nginx nginx-module-njs

# Download and extract Nginx source
RUN yum install -y wget tar && \
    wget http://nginx.org/download/nginx-$(nginx -v 2>&1 | grep -o '[0-9.]*').tar.gz && \
    tar -zxvf nginx-$(nginx -v 2>&1 | grep -o '[0-9.]*').tar.gz

# Clone nginx-kafka-log-module
RUN yum install -y git && \
    git clone https://github.com/kaltura/nginx-kafka-log-module.git

# Set up the yum repository for Confluent
RUN echo -e "[Confluent-Clients]\n\
name=Confluent Clients repository\n\
baseurl=https://packages.confluent.io/clients/rpm/centos/9/\$basearch\n\
gpgcheck=1\n\
gpgkey=https://packages.confluent.io/clients/rpm/archive.key\n\
enabled=1\n" > /etc/yum.repos.d/confluent.repo

# Install necessary packages and compile the nginx-kafka-log-module
RUN yum install -y librdkafka1 librdkafka-devel pcre pcre-devel && \
    yum groupinstall -y "Development Tools" && \
    cd nginx-$(nginx -v 2>&1 | grep -o '[0-9.]*') && \
    ./configure --with-compat --add-dynamic-module=../nginx-kafka-log-module --with-cc-opt="-I/usr/include" --with-ld-opt="-L/usr/lib" && \
    make modules && \
    cp objs/ngx_http_kafka_log_module.so /etc/nginx/modules/

# Add the Akto njs code to the nginx njs directory
RUN mkdir -p /etc/nginx/njs
COPY api_log.js /etc/nginx/njs/api_log.js

# Expose ports (optional)
EXPOSE 80 443

# Command to run Nginx
CMD ["nginx", "-g", "daemon off;"]
