# 인증 서버 분리 - Keycloak + OAuth 2.1

## 개요

초기에는 Spring Security + JWT를 직접 구현하여 하나의 서버가 인증, 토큰 발급, 자원 제공을 모두 담당했다.
멘토링을 통해 OAuth2/OIDC 관점에서 역할 분리가 필요하다는 피드백을 받아 **Keycloak을 인증 서버로 도입**하고,
이후 **OAuth 2.0 → OAuth 2.1 (PKCE)** 로 업그레이드하였다.

---

## 1단계 - Keycloak 도입 + User Storage SPI 연동

### 구조 개요

![SPI 흐름도](https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdna%2FpSbIP%2FdJMcacJrSTX%2FAAAAAAAAAAAAAAAAAAAAAO5_LdOijwR5w7Ej4MiRc8JFndm7OhGvP25aD8IHfz35%2Fimg.png%3Fcredential%3DyqXZFxpELC7KVnFOS48ylbz2pIh7yKj8%26expires%3D1777561199%26allow_ip%3D%26allow_referer%3D%26signature%3DJ8CstkJEgWF%252Fsm5z96GC8VukYJw%253D)

Spring 애플리케이션과 Keycloak이 동일한 사용자 DB를 바라보는 절충안을 선택.
인증 서버와 리소스 서버를 분리하면서도 사용자 저장소를 이관하지 않아도 되는 구조.

### User Storage SPI 구현

Keycloak이 자체 DB 대신 우리 MySQL의 사용자 테이블을 바라보도록 `UserStorageProvider`를 직접 구현하였다.

핵심 구현 포인트는 두 가지이다.

- **UserLookupProvider** - `user_id` 기준으로 사용자 조회 후 `FcUserAdapter`로 래핑하여 Keycloak이 이해할 수 있는 `UserModel`로 변환
- **CredentialInputValidator** - `isValid()`에서 입력된 비밀번호와 DB에 저장된 암호화 비밀번호를 직접 비교

### Docker 구성

커스텀 Provider JAR, MySQL Connector, spring-security-crypto를 포함하여 Keycloak 이미지를 빌드하였다.

```dockerfile
FROM quay.io/keycloak/keycloak:26.5.5 AS builder

COPY build/libs/keycloak-0.0.1-SNAPSHOT.jar /opt/keycloak/providers/
COPY mysql-connector-j-9.6.0.jar /opt/keycloak/providers/
COPY spring-security-crypto-6.5.1.jar /opt/keycloak/providers/

RUN /opt/keycloak/bin/kc.sh build
```

### Keycloak Admin 설정 순서

1. Realm 생성
2. User Storage Provider 등록 및 DB 연결 정보 주입
3. Client 생성 + Direct Access Grants 활성화
4. Redirect URI / Web Origins 설정
5. Token Mapper 설정 (`id`, `userId`, `nickname`, `role` 등 커스텀 클레임 포함)

### 1단계의 한계

Direct Access Grants(Password Grant) 방식은 OAuth 2.0 기준으로도 레거시에 가깝고, OAuth 2.1에서는 완전히 제거된 방식이다.

---

## 2단계 - OAuth 2.1 (Authorization Code + PKCE) 전환

### OAuth 2.0 vs OAuth 2.1 비교

| | OAuth 2.0 | OAuth 2.1 |
|---|---|---|
| PKCE | 선택 | **필수** |
| Implicit Flow | 허용 | 완전 제거 |
| Password Grant | 허용 | 완전 제거 |
| Refresh Token | 재사용 가능 | **단 1회 사용** |
| redirect_uri | 부분 매칭 허용 | 정확히 일치 필수 |

### PKCE란?

인가 코드가 중간에 탈취되어도 토큰을 발급받지 못하도록 막는 보안 기법이다.

```
code_verifier  = 랜덤 문자열 (브라우저만 알고 있음)
code_challenge = SHA256(code_verifier)
```

1. `/auth` 요청 시 → `code_challenge`만 Keycloak에 전송
2. `/token` 요청 시 → `code_verifier` 원본을 전송
3. Keycloak이 `SHA256(verifier) == stored_challenge` 검증

공격자가 인가 코드만 훔쳐도 `code_verifier`를 모르기 때문에 토큰 발급이 불가하다.

### 변경 전후 비교

| | 변경 전 (OAuth 2.0 + SPI) | 변경 후 (OAuth 2.1) |
|---|---|---|
| 인증 방식 | Password Grant | PKCE (code + code_verifier) |
| 토큰 교환 주체 | Keycloak ↔ 클라이언트 | Spring ↔ Keycloak |
| Refresh Token | 클라이언트 보관 | Spring 보관 |
| User DB | MySQL (커스텀 SPI) | Keycloak 자체 DB |

### 채택한 패턴 - Token Relay (BFF 하이브리드)

| | BFF | Token Relay |
|---|---|---|
| 토큰 보관 주체 | BFF 서버 | 클라이언트 |
| 클라이언트가 받는 것 | 세션 & 쿠키 | Access Token |
| 주 사용처 | SPA 보안 강화 | MSA 서비스 간 인증 체인 |

BFF는 규모 대비 오버엔지니어링이라 판단하였다.
단, Refresh Token만큼은 서버 메모리(Caffeine Cache)에 저장하기 때문에 **BFF + Token Relay 하이브리드** 구조에 가깝다.

---

## 인증 흐름

### 1. 로그인 (인가 코드 → Access Token)
![SPI 흐름도](https://img1.daumcdn.net/thumb/R1280x0/?scode=mtistory2&fname=https%3A%2F%2Fblog.kakaocdn.net%2Fdna%2Fd0W8ju%2FdJMcafF9TtR%2FAAAAAAAAAAAAAAAAAAAAADRwD-9OF12r29VMO0wz9DhbWlcHLUt41YKZiLmwQ4Vz%2Fimg.png%3Fcredential%3DyqXZFxpELC7KVnFOS48ylbz2pIh7yKj8%26expires%3D1777561199%26allow_ip%3D%26allow_referer%3D%26signature%3DLdgk6vaWTXtiW4vgtxmmGw4VWPU%253D)


### 2. 토큰 갱신

```
[Client]
  → POST /refresh (Authorization: Bearer <expired_token>)
  → [Spring]
       → 만료된 JWT에서 userId 추출 (서명 검증 없이 payload 디코딩)
       → Caffeine Cache에서 Refresh Token 조회
       → Keycloak에 Refresh Token으로 새 토큰 요청
       → 새 Refresh Token 캐시에 재저장
       → 새 Access Token 클라이언트에 반환
```

---

## Refresh Token 저장소 - Caffeine Cache

Redis 대신 Caffeine을 선택한 이유: 서비스 규모 대비 별도 인프라 운영 비용이 과하다는 멘토 피드백.

| | HashMap | Caffeine |
|---|---|---|
| TTL | 불가 | 가능 |
| 최대 개수 제한 (OOM 방지) | 불가 | 가능 |
| 성능 통계 | 불가 | 가능 |

```java
// TTL: 3600초, 최대 10,000명
CacheType.REFRESH_TOKENS("refreshTokens", 3600, 10_000)
```

---

## 트러블 슈팅

### Keycloak 응답 JSON 역직렬화 실패

Keycloak의 `/token` 엔드포인트는 스네이크 케이스(`access_token`)로 응답한다.
Java 필드명(카멜케이스)과 불일치로 매핑 실패가 발생하였다.

```java
// 해결: @JsonProperty로 명시적 매핑
@JsonProperty("access_token")
private String accessToken;

@JsonProperty("refresh_token")
private String refreshToken;
```

---

## 참고

- [Keycloak으로 인증 서버를 분리](https://inscowoo.tistory.com/64)
- [OAuth2.0 → OAuth2.1 변경](https://inscowoo.tistory.com/65)
