package com.naleul.naleul.domain.brainDump.client;

/**
 * Brain dump 문장 해석 + 목표 연결 AI.
 *
 * 구현체 (목표 생성과 같은 설정 goal.ai.client 를 따른다 — 로컬 fake, 운영 claude)
 *  - FakeBrainDumpAiClient   : 규칙 기반 (KoreanTaskTextParser + 목표 이름 키워드 매칭)
 *  - ClaudeBrainDumpAiClient : Claude Haiku tool use
 *
 * 실패 시 GoalAiException 을 던진다. 서비스는 잡아서 규칙 기반 해석으로 대체하고 aiFailed=true 로 응답한다.
 */
public interface BrainDumpAiClient {
    BrainDumpAiResult parse(BrainDumpAiRequest request);
}
