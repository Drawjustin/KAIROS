# 망분리 환경 (AWS)

증명하려는 것은 한 문장이다.

> **업무망의 인터넷 접근은 0이지만 AI는 쓸 수 있다. 그리고 모든 호출은 기록된다.**

## 구성

```
VPC 10.0.0.0/16 · ap-northeast-2
│
├─ Public    10.0.1.0/24    [0.0.0.0/0 → IGW]
│    NAT 겸 egress 통제 지점 (t4g.nano)
│      · NAT: app 구간을 외부로 내보낸다
│      · Squid: 도메인 허용 목록으로 판정한다
│      · iptables: 전달 트래픽의 443/80을 끊어 프록시 우회를 막는다
│
├─ App       10.0.11.0/24   [0.0.0.0/0 → NAT 인스턴스]
│    KAIROS (t4g.micro)
│
└─ Workload  10.0.12.0/24   ★ 기본 경로 없음
     업무망 클라이언트 (t4g.micro)
```

## 핵심은 "없는 것"이다

`network.tf`의 `aws_route_table.workload`에는 `0.0.0.0/0` 경로가 없다.

보안 그룹은 규칙을 잘못 고치면 뚫린다. **설정으로 막았다**는 상태다.
경로가 없는 서브넷은 나갈 방법 자체가 없다. **구조적으로 못 나간다**는 상태다.

여기에 `aws_route`를 하나 더 추가하는 순간 망분리가 무너진다. 그래서 이 구성에서
가장 중요한 파일은 `network.tf`이고, 거기서 확인해야 할 것은 무엇이 있는가가 아니라
무엇이 없는가다.

## 프록시를 별도 인스턴스에 둔 이유

프록시가 KAIROS와 같은 호스트에 있으면, 프록시 설정을 지우는 것만으로 우회된다.
그러면 프록시는 통제가 아니라 권고사항이다.

별도 인스턴스에 두고 **전달 트래픽의 443을 끊으면** 프록시를 거치지 않고는 나갈 수 없다.
Squid는 그 인스턴스에서 자기 이름으로 나가므로 `OUTPUT` 체인을 타고 이 규칙에 걸리지 않는다.

```
KAIROS → (443 직결)     → NAT의 FORWARD 체인에서 REJECT
KAIROS → (3128 프록시)  → Squid 판정 → 허용 도메인만 통과
```

Squid는 HTTPS를 복호화하지 않는다. `CONNECT`의 목적지 도메인만 보고 판정한다.

> 실무에서는 AWS Network Firewall이나 보안 웹 게이트웨이가 맡는 역할이다.
> 월 30만 원을 넘는 비용 때문에 같은 목적을 Squid로 대신했다.
> 한계를 알고 대체한 것이며 판정 방식은 동일하다.

## 접속은 SSH가 아니라 SSM으로

22번 포트를 아예 열지 않는다. bastion도 키 파일도 없다.
무엇보다 **누가 언제 어느 인스턴스에 붙었는지가 CloudTrail에 남는다.**

업무망은 인터넷이 없으므로 SSM에 닿으려면 VPC 엔드포인트가 필요하다
(`ssm`, `ssmmessages`, `ec2messages`). 이 인터페이스 엔드포인트가 이 구성에서
비용의 대부분을 차지하므로, 데모 기간에만 띄우고 끝나면 `destroy`하는 것을 전제로 한다.

## 상태 파일

상태에는 리소스 ID와 일부 속성이 그대로 담긴다. 로컬에 두면 노트북이 바뀌는 순간
인프라를 관리할 수 없고, 여러 곳에서 동시에 `apply`하면 상태가 깨진다.
그래서 S3에 두고 잠금을 건다.

Terraform 1.10부터 S3 backend가 DynamoDB 테이블 없이 자체 잠금(`use_lockfile`)을
지원하므로 별도 잠금 테이블을 만들지 않았다.

상태를 둘 곳 자체는 상태로 관리할 수 없다(닭과 달걀). 그래서 `bootstrap/`만
따로 떼어 로컬 상태로 한 번 만든다.

## 실행

```bash
# 1. 상태 버킷 (최초 한 번)
cd infra/bootstrap
terraform init
terraform apply -var state_bucket_name=kairos-tfstate-<유일한값>

# 2. 본 구성
cd ..
cp backend.hcl.example backend.hcl   # 위에서 만든 버킷 이름을 적는다
terraform init -backend-config=backend.hcl
terraform plan
terraform apply

# 3. 검증 명령 확인
terraform output verification_commands

# 4. 끝나면 반드시
terraform destroy
```

## 이미지를 어디서 받을 것인가

app 구간에는 인터넷 경로가 없고 NAT가 전달 트래픽의 443을 끊는다.
그래서 이미지도 프록시를 지나야 하는데, **AWS API는 프록시로 우회되지 않는다.**
ECR을 쓰려면 VPC 엔드포인트가 필요하고 그만큼 비용이 붙는다.

| 방식 | 설정 | 비용 |
| --- | --- | --- |
| 공개 레지스트리 (Docker Hub 등) | `app_image_uri`만 지정 | 추가 없음 |
| ECR | `enable_aws_api_endpoints = true` | 시간당 ~₩45 추가 |

인터넷이 없는 구간에서 AWS 서비스를 쓰려면 엔드포인트가 유일한 길이라는 점이
이 구성에서 드러나는 지점이다. 편의를 위해 프록시 예외를 넓히면 통제가 느슨해지므로
엔드포인트 쪽을 택했다.

## 허용 목록에 provider 도메인만 있는 것은 아니다

`allowed_egress_domains` 기본값에는 OS 패키지 저장소와 컨테이너 레지스트리도 들어 있다.
인터넷이 없는 구간에서 부팅하려면 어딘가는 열려야 하기 때문이고,
실무에서도 사내 미러를 두거나 같은 형태의 허용 목록을 유지한다.
통제를 느슨하게 한 것이 아니라 목록에 명시했다는 점이 중요하다.

## 검증 시나리오

`terraform output verification_commands`가 그대로 실행 가능한 명령을 뱉는다.

| | 시도 | 기대 | 증거 |
| --- | --- | --- | --- |
| A | 업무망에서 `curl https://api.openai.com` | 차단 | VPC Flow Log `REJECT` |
| B | 업무망에서 KAIROS 호출 | 정상 | 응답 + `ai_usage_log` |
| C | KAIROS에서 `curl https://www.google.com` | 차단 | Squid `access.log` |
| D | KAIROS에서 허용 도메인 호출 | 정상 | Squid `access.log` |
| E | 업무망에 SSH | 불가 (SSM만) | CloudTrail |

**A와 B가 나란히 놓인 캡처 한 장**이 이 프로젝트의 최종 산출물이다.
아키텍처 다이어그램 열 장보다 그게 강하다.

## 비용

절감 구성 기준, 켜 둔 시간만큼만 나간다.

| 항목 | 시간당 | 비고 |
| --- | --- | --- |
| t4g.micro × 2 | ~₩20 | KAIROS, 업무망 |
| t4g.nano × 1 | ~₩5 | NAT 겸 프록시 |
| Elastic IP | ~₩5 | |
| VPC 인터페이스 엔드포인트 × 3 | ~₩45 | SSM 접속용. **비용의 대부분** |
| VPC 인터페이스 엔드포인트 × 3 | ~₩45 | ECR·Secrets Manager용. 기본값 꺼짐 |
| CloudWatch Logs | 사용량 | |

**기본 구성은 시간당 약 ₩75, 하루 종일 켜도 ₩1,800 수준이다.**
`apply` → 시나리오 실행 및 캡처 → `destroy`를 반복하면 며칠분도 만 원을 넘지 않는다.

남는 것은 캡처와 이 코드이고, 그 둘이 평가 대상이다.

> `destroy`를 잊으면 계속 과금된다. 모든 리소스에 `Project=kairos` 태그가 붙어 있으니
> 태그 편집기에서 남은 것이 없는지 확인할 수 있다.
