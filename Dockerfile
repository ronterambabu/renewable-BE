# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# Stage 2: Deploy to Tomcat
FROM tomcat:10.1-jdk17

# Remove default webapps
RUN rm -rf /usr/local/tomcat/webapps/*

# Copy WAR into Tomcat webapps directory
COPY --from=build /app/target/renewable-0.0.1-SNAPSHOT.war /usr/local/tomcat/webapps/ROOT.war



# Expose port
EXPOSE 8080

# Start Tomcat
CMD ["catalina.sh", "run"]
