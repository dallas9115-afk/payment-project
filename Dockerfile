# --- 1단계: Build Stage (빌드 환경) ---
# Java 17 버전의 Corretto 이미지를 가져와서 'build'라는 별명을 붙임
FROM amazoncorretto:17-alpine AS build

# 컨테이너 내부의 작업 디렉토리를 /app으로 설정
WORKDIR /app

# 현재 프로젝트의 모든 파일을 컨테이너 안으로 복사
COPY . .

# Gradle을 사용하여 JAR 파일을 빌드 (테스트는 시간 절약을 위해 제외)
# 실행 권한이 없을 수 있으므로 chmod 추가
RUN chmod +x ./gradlew
RUN ./gradlew clean build -x test

# --- 2단계: Run Stage (실행 환경) ---
# 다시 가벼운 이미지를 새로 가져옴 (빌드 도구들은 버리고 실행용 자바만 남김)
FROM amazoncorretto:17-alpine

WORKDIR /app

# (수정 전) COPY --from=build /app/build/libs/*[!plain].jar app.jar
# (수정 후) 1.0.0.jar 로 끝나는 진짜 실행 파일만 복사
COPY --from=build /app/build/libs/*1.0.0.jar app.jar

# 서버가 사용할 포트 번호 명시 (문서화 용도)
EXPOSE 8080

# 컨테이너가 시작될 때 실행할 명령어
ENTRYPOINT ["java", "-jar", "app.jar"]