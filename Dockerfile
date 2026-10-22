# 세 단계로 짓는다. 빌드 도구는 완성된 이미지에 들어가지 않는다.
#
#   docker build -t weboffice .
#
# 보통은 직접 부르지 않고 compose 가 부른다: docker compose up

# ---------- 1단계: 화면 짓기 ----------
#
# node 를 베이스 이미지에서 가져온다. Maven 의 frontend 플러그인은 nodejs.org
# 에서 node 를 직접 내려받는데, 그러면 빌드가 그 주소에 닿는 것에 묶인다 -
# TLS 를 가로채는 망에서는 "certificate_unknown" 으로 통째로 실패한다
# (2026-10-22 실측). 베이스 이미지에서 받으면 그 왕복이 없다.
FROM node:24-bookworm-slim AS web
WORKDIR /src/frontend

# 자물쇠를 먼저 넣고 받는다. 소스만 바뀐 다음 빌드는 이 층을 다시 쓴다.
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci --no-audit --no-fund

COPY frontend/ ./
# vite 가 ../target/classes/static 에 쏟는다(vite.config.ts 의 outDir).
RUN npm run build

# ---------- 2단계: 서버 짓기 ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# pom 을 먼저 넣고 자바 의존을 받는다. 소스만 바뀐 다음 빌드는 이 층을 다시 쓴다.
COPY pom.xml ./
RUN mvn -B -q dependency:go-offline -DskipTests -Dskip.frontend=true

COPY src/ src/
# 1단계가 만든 번들을 그대로 쓴다. Maven 의 resources 는 target/classes 에
# 덧붙이기만 하므로 먼저 넣어 두면 그대로 jar 에 담긴다.
COPY --from=web /src/target/classes/static/ target/classes/static/

# 시험은 돌리지 않는다. 이미지 짓는 일과 맞는지 보는 일은 따로다
# (맞는지는 mvn test 가 CI 에서 본다). 여기서 돌리면 빌드가 느려지고,
# 실패했을 때 "이미지를 못 만든다" 와 "코드가 틀렸다" 가 섞인다.
#
# 짓고 나서 이름이 버전에 묶이지 않게 옮긴다. 한 RUN 으로 묶는 이유는 층을
# 하나 덜 만드는 것뿐이다.
RUN mvn -B -q package -DskipTests -Dskip.frontend=true \
 && cp target/*.jar /app.jar

# ---------- 3단계: 돌리기 ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

# curl 은 compose 의 healthcheck 가 쓴다. 없으면 앱이 "unhealthy" 로만 보여
# 왜 안 뜨는지 알 수 없다.
#
# 버전을 못 박지 않는다(hadolint DL3008). 우분투 apt 색인은 주마다 움직여서,
# 못 박으면 그 버전이 색인에서 빠지는 날 빌드가 통째로 실패한다 - 받은 사람이
# 겪는 것은 "안 깔린다" 다. healthcheck 에 쓰는 curl 하나를 고정해서 얻는
# 재현성보다 그 쪽이 비싸다.
# hadolint ignore=DL3008
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl \
 && rm -rf /var/lib/apt/lists/*

# root 로 돌리지 않는다. 문서를 받아 POI 로 뜯는 서버라 더 그렇다.
#
# USER 에 숫자를 적는다. 이름을 적으면 호스트가 UID 를 다시 매길 때(쿠버네티스의
# runAsUser, 볼륨 소유자 맞추기) 그 이름을 풀 수 없다.
#
# data/sessions 를 미리 만들고 소유자를 맞춘다. 서블릿 세션을 파일로 남기는
# 설정(server.servlet.session.store-dir)이라 그 디렉터리가 없으면 톰캣이
# 아예 뜨지 않는다 - "Session dir /app/./data/sessions does not exist" 로
# 죽고 restart 에 걸려 끝없이 되살아난다(2026-10-22, compose 로 실측).
# 10001 이 root 소유 /app 아래에 스스로 만들 수도 없다.
RUN useradd --system --create-home --uid 10001 --user-group weboffice \
 && mkdir -p /app/data/sessions \
 && chown -R 10001:10001 /app
USER 10001:10001

COPY --from=build /app.jar /app/app.jar

EXPOSE 8710

# 컨테이너가 쥔 메모리에 맞춰 힘을 정한다. 큰 문서 하나가 통째로 메모리에
# 올라오므로 기본값(1/4)보다 넉넉히 준다.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75"

# PostgreSQL 프로필로 띄운다. 접속 정보는 환경 변수로 들어온다
# (WO_DB_HOST, WO_DB_PORT, WO_DB_NAME, WO_DB_USER, WO_DB_PASSWORD).
ENV SPRING_PROFILES_ACTIVE=postgresql

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
