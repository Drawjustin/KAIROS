# KAIROS

Multi-LLM Gateway and Observability Platform

KAIROS는 여러 AI 모델 제공자를 하나의 일관된 API로 추상화하고, 인증/권한, 라우팅, 장애 대응, 비용 통제, 관측 가능성을 중앙화하는 백엔드 플랫폼입니다. 단순한 LLM 프록시가 아니라, 여러 서비스와 팀이 AI 기능을 안정적으로 운영할 수 있도록 돕는 `AI Gateway + 운영 플랫폼`을 목표로 합니다.

## Why

AI 기능을 서비스에 도입할 때 다음과 같은 문제가 자주 발생합니다.

- 모델 제공자마다 API 형식이 다름
- 어떤 팀이 어떤 모델을 얼마나 사용하는지 파악하기 어려움
- 비용, 에러율, 지연시간을 통합 관리하기 어려움
- 특정 provider 장애 시 전체 서비스가 영향을 받음
- 요청량 증가에 대비한 quota, rate limit, budget 정책이 필요함

KAIROS는 이런 문제를 해결하기 위해 설계되었습니다.

## Goals

- 여러 LLM provider를 하나의 API로 통합
- tenant별 인증, 권한, quota, budget 정책 적용
- fallback, retry, circuit breaker 기반 장애 대응
- 요청 수, 응답시간, 에러율, 토큰 사용량, 비용 추적
- Kafka 기반 비동기 이벤트 파이프라인 구축
- Prometheus, Grafana, OpenTelemetry 기반 관측 환경 제공

## Core Features

- Unified AI API
- Multi-provider routing
- API key authentication
- Tenant-based quota and budget control
- Rate limiting
- Fallback and resilience policies
- Usage and cost tracking
- Prometheus metrics
- Structured logging
- Distributed tracing
- Kafka event pipeline
- Admin APIs for platform operations

## Architecture

KAIROS는 다음과 같은 구성으로 동작합니다.

- API Gateway
- Routing Engine
- Provider Adapters
- Policy Service
- Usage Service
- Kafka Event Producer/Consumer
- PostgreSQL
- Redis
- Prometheus / Grafana
- Loki / Tempo

요청은 Gateway를 통해 들어오고, 정책 검증과 라우팅을 거쳐 적절한 AI provider로 전달됩니다. 응답 이후에는 사용량, 비용, 실패 이벤트가 비동기적으로 수집되며, 운영자는 대시보드를 통해 전체 상태를 확인할 수 있습니다.

## Tech Stack
- Java
- Kotlin
- Spring Boot
- PostgreSQL
- Redis
- Kafka
- Prometheus
- Grafana

## What This Project Demonstrates

이 프로젝트는 단순히 AI API를 호출하는 예제가 아니라, 실제 서비스 환경에서 필요한 백엔드 역량을 보여주는 데 초점을 맞춥니다.

- 멀티 provider 추상화
- 운영 정책 중앙화
- 장애 허용 설계
- 비용 및 사용량 통제
- 관측 가능성 확보
- 비동기 이벤트 기반 데이터 처리
- 플랫폼 백엔드 아키텍처 설계

## Target Use Cases

- 여러 팀이 공통 AI 인프라를 사용하는 환경
- AI 기능을 서비스에 안전하게 도입하고 싶은 경우
- 비용, 성능, 안정성을 함께 관리해야 하는 경우
- LLM 서비스 운영을 위한 Gateway 계층이 필요한 경우

## Project Status

In Progress

## Future Work

- Streaming response support
- Semantic cache
- OPA-based policy engine
- Canary routing for new model rollout
- Admin console UI
- Usage anomaly detection

## Motivation

이 프로젝트는 AI 시대에도 경쟁력 있는 서버 백엔드 개발자가 되기 위해, 단순 기능 개발을 넘어서 `운영 가능한 AI 플랫폼`을 직접 설계하고 구현하는 것을 목표로 시작했습니다. 특히 대규모 서비스 환경에서 중요한 인증, 정책, 장애 대응, 메트릭, 비용 관리 문제를 백엔드 관점에서 해결하는 데 집중합니다.
