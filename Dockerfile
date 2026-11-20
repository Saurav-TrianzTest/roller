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


# STAGE 1 - BUILD ------------------------------------------------

FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /project
COPY pom.xml .
COPY app/pom.xml ./app/
COPY db-utils/pom.xml ./db-utils/
RUN mvn -B -q -DskipTests dependency:go-offline

COPY app/src ./app/src
COPY db-utils/src ./db-utils/src
RUN mvn -B -q -DskipTests clean install


# STAGE 2 - PACKAGE ------------------------------------------------

FROM tomcat:11.0-jdk17-temurin

# Remove existing Tomcat webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Add Roller configuration to environment
ARG STORAGE_ROOT=/usr/local/tomcat/data
ARG DATABASE_JDBC_DRIVERCLASS=org.postgresql.Driver
ARG DATABASE_JDBC_CONNECTIONURL=jdbc:postgresql://postgresql/rollerdb
ARG DATABASE_JDBC_USERNAME=scott
ARG DATABASE_JDBC_PASSWORD
ARG DATABASE_HOST=postgresql:5432

ENV STORAGE_ROOT=${STORAGE_ROOT}
ENV DATABASE_JDBC_DRIVERCLASS=${DATABASE_JDBC_DRIVERCLASS}
ENV DATABASE_JDBC_CONNECTIONURL=${DATABASE_JDBC_CONNECTIONURL}
ENV DATABASE_JDBC_USERNAME=${DATABASE_JDBC_USERNAME}
ENV DATABASE_JDBC_PASSWORD=${DATABASE_JDBC_PASSWORD}
ENV DATABASE_HOST=${DATABASE_HOST}
ENV TZ=UTC
ENV JAVA_OPTS="-Xmx512m -Xms256m -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"
ENV LANG=C.UTF-8
ENV LC_ALL=C.UTF-8

# install Roller WAR as ROOT.war, create data dirs
WORKDIR /usr/local/roller
COPY --from=builder /project/app/target/roller.war /usr/local/tomcat/webapps/ROOT.war
RUN mkdir -p data/mediafiles data/searchindex

# download PostgreSQL driver plus Mail and Activation JARs
WORKDIR /usr/local/tomcat/lib
RUN wget -O postgresql.jar https://repo1.maven.org/maven2/org/postgresql/postgresql/42.7.13/postgresql-42.7.13.jar && \
    wget -O angus-mail.jar https://repo1.maven.org/maven2/org/eclipse/angus/angus-mail/2.0.5/angus-mail-2.0.5.jar && \
    wget -O jakarta.mail-api.jar https://repo1.maven.org/maven2/jakarta/mail/jakarta.mail-api/2.1.5/jakarta.mail-api-2.1.5.jar && \
    wget -O jakarta.activation-api.jar https://repo1.maven.org/maven2/jakarta/activation/jakarta.activation-api/2.1.4/jakarta.activation-api-2.1.4.jar && \
    wget -O angus-activation.jar https://repo1.maven.org/maven2/org/eclipse/angus/angus-activation/2.0.3/angus-activation-2.0.3.jar

# Add Roller entry-point and go!
COPY docker/entry-point.sh /usr/local/tomcat/bin
COPY docker/wait-for-it.sh /usr/local/tomcat/bin
RUN chmod +x /usr/local/tomcat/bin/entry-point.sh && \
    chmod +x /usr/local/tomcat/bin/wait-for-it.sh && \
    chown -R tomcat:tomcat /usr/local/tomcat && \
    chmod -R g+rw /usr/local/tomcat

# Switch to non-root user (tomcat user from base image)
USER tomcat

WORKDIR /usr/local/tomcat
EXPOSE 8080

ENTRYPOINT ["/usr/local/tomcat/bin/entry-point.sh"]
