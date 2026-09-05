# syntax=docker/dockerfile:1

# ---------- 빌드 ----------
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# 의존성 정의만 먼저 복사한다. 소스만 바뀐 경우 아래 의존성 내려받기 계층이 캐시에서 재사용된다.
COPY gradle gradle
COPY gradlew build.gradle.kts settings.gradle.kts ./
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null 2>&1 || true

COPY src src
# 테스트는 CI에서 Testcontainers로 이미 돌린다. 이미지 빌드에서 또 돌리면 도커 안에 도커가 필요해진다.
RUN ./gradlew --no-daemon bootJar -x test

# 계층을 나눠 두면 소스만 바뀐 배포에서 의존성 계층을 다시 전송하지 않는다.
RUN java -Djarmode=layertools -jar build/libs/*-SNAPSHOT.jar extract --destination extracted

# ---------- 실행 ----------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# root로 돌리지 않는다. 컨테이너가 뚫렸을 때 host로 넘어갈 여지를 줄인다.
RUN addgroup -S kairos && adduser -S -G kairos kairos

COPY --from=builder --chown=kairos:kairos /build/extracted/dependencies/ ./
COPY --from=builder --chown=kairos:kairos /build/extracted/spring-boot-loader/ ./
COPY --from=builder --chown=kairos:kairos /build/extracted/snapshot-dependencies/ ./
COPY --from=builder --chown=kairos:kairos /build/extracted/application/ ./

USER kairos

# 8080은 서비스, 9090은 지표다. 두 포트를 나눠 두어야 보안 그룹에서 따로 통제할 수 있다.
EXPOSE 8080 9090

# 컨테이너 안에서만 도는 확인이라 관리 포트를 그대로 쓴다.
HEALTHCHECK --interval=15s --timeout=3s --start-period=40s --retries=3 \
    CMD wget -q -O- http://127.0.0.1:9090/actuator/health | grep -q '"status":"UP"' || exit 1

# 컨테이너 메모리 한도를 JVM이 인식하게 둔다. 고정 힙을 박으면 인스턴스 크기를 바꿀 때마다 이미지를 다시 만들어야 한다.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseSerialGC -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
