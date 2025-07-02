# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Deploy to Tomcat
FROM tomcat:10.1-jdk17

# Remove default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Disable shutdown port by modifying server.xml
RUN sed -i 's/<Server port="8005"/<Server port="-1"/' /usr/local/tomcat/conf/server.xml

# Copy WAR into Tomcat's ROOT.war
COPY --from=build /app/target/renewable-0.0.1-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war

# Optional: Set timezone, permissions, etc.
ENV TZ=Asia/Kolkata
RUN chmod +x /usr/local/tomcat/bin/*.sh

# Expose default Tomcat HTTP port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
