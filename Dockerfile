FROM eclipse-temurin:21.0.2_13-jdk-jammy AS builder
WORKDIR /app

COPY .mvn/ .mvn
COPY mvnw pom.xml ./

RUN chmod +x ./mvnw

RUN ./mvnw dependency:go-offline
COPY ./src ./src
RUN ./mvnw clean install -DskipTests

FROM eclipse-temurin:21.0.2_13-jre-jammy AS final
WORKDIR /opt/app

# Install Python
RUN apt-get update && apt-get install -y \
    python3 \
    python3-pip \
    && rm -rf /var/lib/apt/lists/* \
    && ln -s /usr/bin/python3 /usr/bin/python

# Copy Python files
COPY requirements.txt ./
COPY artwork_importer_improved.py ./
COPY artwork_checkpoint.pkl ./
COPY artwork_import.log ./

# Install Python dependencies
RUN pip3 install -r requirements.txt

# Copy Java application
COPY --from=builder /app/target/*.jar /opt/app/app.jar

EXPOSE 7040

# One-liner startup
ENTRYPOINT ["bash", "-c", "java -jar /opt/app/app.jar & sleep 30 && python artwork_importer_improved.py --delay 0 --batch-size 20 & wait"]