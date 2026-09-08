#!/usr/bin/env bash
# destroy 후 실제로 과금 대상이 남지 않았는지 확인한다.
# terraform 상태만 믿으면 backend 설정이 어긋났을 때 남은 리소스를 놓친다.
set -u
fail=0
check() {
  local label="$1" n="$2"
  if [ "$n" = "0" ]; then printf '  OK   %-16s 0\n' "$label"
  else printf '  WARN %-16s %s\n' "$label" "$n"; fail=1; fi
}
check "EC2" "$(aws ec2 describe-instances --filters 'Name=tag:Project,Values=kairos' 'Name=instance-state-name,Values=running,pending,stopping,stopped' --query 'length(Reservations[].Instances[])' --output text)"
check "VPC endpoints" "$(aws ec2 describe-vpc-endpoints --filters 'Name=tag:Project,Values=kairos' --query 'length(VpcEndpoints[?State!=`deleted`])' --output text)"
check "Elastic IP" "$(aws ec2 describe-addresses --query 'length(Addresses)' --output text)"
check "NAT gateways" "$(aws ec2 describe-nat-gateways --filter 'Name=state,Values=available,pending' --query 'length(NatGateways)' --output text)"
check "EBS volumes" "$(aws ec2 describe-volumes --filters 'Name=status,Values=available,in-use' --query 'length(Volumes)' --output text)"
check "VPC" "$(aws ec2 describe-vpcs --filters 'Name=tag:Project,Values=kairos' --query 'length(Vpcs)' --output text)"
[ "$fail" = "0" ] && echo "과금 대상 없음" || echo "남은 리소스가 있다. 콘솔에서 확인할 것."
exit "$fail"
