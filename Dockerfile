# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  The ASF licenses this file to You
# under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.  For additional information regarding
# copyright in this work, please see the NOTICE file in the top level
# directory of this distribution.

# STAGE 1 - BUILD
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /workspace

# Copy pom.xml first and download dependencies
COPY pom.xml .
COPY app/pom.xml app/pom.xml
COPY db-utils/pom.xml db-utils/pom.xml

RUN mvn -B dependency:go-offline -pl app,db-utils -am

# Copy source
COPY app/src app/src
COPY db-utils/src db-utils/src

# Build the project
RUN mvn -B clean package -DskipTests -pl app,db-utils -am

# Download and extract Tomcat
RUN curl -L -o /tmp/tomcat.tar.gz https://archive.apache.org/dist/tomcat/tomcat-10/tomcat-10.1.40/bin/apache-tomcat-10.1.40.tar.gz && \
    tar xzf /tmp/tomcat.tar.gz -C /tmp && \
    rm /tmp/tomcat.tar.gz

# STAGE 2 - RUNTIME
FROM eclipse-temurin:17-jdk

# Tomcat environment
ENV CATALINA_HOME=/opt/tomcat
ENV CATALINA_BASE=/opt/tomcat
ENV CATALINA_TMPDIR=/tmp
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV TZ=UTC
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8
ENV JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

# Copy Tomcat from builder stage
COPY --from=builder /tmp/apache-tomcat-10.1.40 /opt/tomcat

# Clean Tomcat webapps
RUN rm -rf /opt/tomcat/webapps/*

# Create non-root user
RUN groupadd -r roller && useradd -r -g roller roller

# Copy the WAR file
COPY --from=builder /workspace/app/target/roller.war /opt/tomcat/webapps/ROOT.war

# Create data directories
RUN mkdir -p /usr/local/roller/data/mediafiles \
    /usr/local/roller/data/searchindex \
    /opt/tomcat/logs && \
    chown -R roller:roller /opt/tomcat /usr/local/roller

# Expose the default Tomcat port
EXPOSE 8080

# Switch to non-root user
USER roller

# Entry point
ENTRYPOINT ["/opt/tomcat/bin/catalina.sh", "run"]
