package io.github.drawjustin.kairos.pii.service

import io.github.drawjustin.kairos.pii.repository.ProjectPiiPolicyRepository
import io.github.drawjustin.kairos.pii.type.PiiAction
import io.github.drawjustin.kairos.pii.type.PiiType
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
// project별 민감정보 처리 정책을 읽어온다.
// 설정하지 않은 타입은 PiiType이 들고 있는 기본값을 쓰므로, 정책을 한 건도 넣지 않아도 통제는 동작한다.
class PiiPolicyService(
    private val projectPiiPolicyRepository: ProjectPiiPolicyRepository,
) {
    // 민감정보가 하나도 검출되지 않은 요청에서는 호출되지 않도록 상위에서 순서를 잡는다.
    // 대부분의 요청이 여기까지 오지 않으므로 조회 부담이 크지 않다.
    @Transactional(readOnly = true)
    fun resolveActions(projectId: Long): Map<PiiType, PiiAction> {
        val configured = projectPiiPolicyRepository.findAllByProject_IdAndDeletedAtIsNull(projectId)
            .associate { it.piiType to it.action }
        return PiiType.entries.associateWith { configured[it] ?: it.defaultAction }
    }
}
