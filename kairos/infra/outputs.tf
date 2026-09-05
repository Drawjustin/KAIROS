output "app_instance_id" {
  description = "KAIROS 인스턴스. aws ssm start-session --target <id>로 접속한다."
  value       = aws_instance.app.id
}

output "workload_instance_id" {
  description = "업무망 인스턴스. 검증 시나리오 A와 B를 여기서 실행한다."
  value       = aws_instance.workload.id
}

output "nat_instance_id" {
  description = "NAT 겸 프록시 인스턴스. Squid 로그를 여기서 본다."
  value       = aws_instance.nat.id
}

output "app_private_ip" {
  description = "업무망에서 KAIROS를 부를 때 쓰는 주소"
  value       = aws_instance.app.private_ip
}

output "flow_log_group" {
  description = "거부 기록을 확인할 로그 그룹"
  value       = aws_cloudwatch_log_group.flow_logs.name
}

output "verification_commands" {
  description = "검증 시나리오를 그대로 실행할 수 있는 명령 모음"
  value = {
    "A. 업무망에서 인터넷 시도 (차단되어야 함)" = "aws ssm start-session --target ${aws_instance.workload.id} --document-name AWS-StartInteractiveCommand --parameters command='curl -sS --max-time 8 https://api.openai.com; echo exit=$?'"
    # 지표 포트가 아니라 서비스 포트를 부른다. 게이트웨이가 실제로 요청을 처리하는지 봐야 하기 때문이다.
    # API key 없이 부르면 KAIROS가 AI_001을 돌려준다. 그 응답 자체가 게이트웨이가 살아 있다는 증거다.
    "B. 업무망에서 KAIROS 호출 (성공해야 함)"       = "aws ssm start-session --target ${aws_instance.workload.id} --document-name AWS-StartInteractiveCommand --parameters command='curl -sS --max-time 8 -o /dev/null -w %%{http_code} http://${aws_instance.app.private_ip}:8080/api/v1/chat/completions'"
    "C. KAIROS에서 허용 목록 밖 도메인 (차단되어야 함)" = "aws ssm start-session --target ${aws_instance.app.id} --document-name AWS-StartInteractiveCommand --parameters command='curl -sS --max-time 8 https://www.google.com; echo exit=$?'"
    "D. Squid 판정 기록"                    = "aws ssm start-session --target ${aws_instance.nat.id} --document-name AWS-StartInteractiveCommand --parameters command='tail -20 /var/log/squid/access.log'"
    "E. VPC Flow Log 거부 기록"             = "aws logs filter-log-events --log-group-name ${aws_cloudwatch_log_group.flow_logs.name} --filter-pattern REJECT --max-items 20"
  }
}
