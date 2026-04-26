package io.github.drawjustin.kairos.internaltool.service

import io.github.drawjustin.kairos.internaltool.dto.MockDocSearchDocument
import org.springframework.stereotype.Service

@Service
// 별도 MCP 서버를 붙이기 전, KAIROS 내부에서 tool calling 흐름을 검증하기 위한 임시 문서 검색기다.
class MockDocSearchService {
    fun search(query: String): List<MockDocSearchDocument> {
        val terms = query.trim()
            .lowercase()
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }

        return DOCUMENTS
            .mapNotNull { document ->
                val score = document.scoreFor(terms)
                if (score > 0) {
                    document.copy(score = score)
                } else {
                    null
                }
            }
            .sortedWith(compareByDescending<MockDocSearchDocument> { it.score }.thenBy { it.title })
    }

    private fun MockDocSearchDocument.scoreFor(terms: List<String>): Int {
        val searchable = "$title $content $source".lowercase()
        return terms.count { searchable.contains(it) }
    }

    private companion object {
        private val DOCUMENTS = listOf(
            MockDocSearchDocument(
                title = "휴가 및 연차 정책",
                content = "연차는 입사일 기준으로 계산하며, 휴가 신청은 최소 3영업일 전에 등록해야 한다.",
                source = "hr-policy-manual",
                score = 0,
            ),
            MockDocSearchDocument(
                title = "재택근무 규정",
                content = "재택근무는 팀 리드 승인 후 사용할 수 있으며, 보안 문서는 사내 VPN 환경에서만 열람한다.",
                source = "work-policy-manual",
                score = 0,
            ),
            MockDocSearchDocument(
                title = "보안 문서 취급 가이드",
                content = "기밀 문서는 외부 AI 서비스에 원문 그대로 전송하지 않고, 승인된 내부 검색 도구 결과만 활용한다.",
                source = "security-policy-manual",
                score = 0,
            ),
            MockDocSearchDocument(
                title = "장애 대응 프로세스",
                content = "서비스 장애 발생 시 온콜 담당자가 incident 채널을 열고 영향 범위와 복구 상황을 기록한다.",
                source = "incident-response-manual",
                score = 0,
            ),
        )
    }
}
