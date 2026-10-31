package com.naleul.naleul.domain.work.support;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class WorkTaskTextParserTest {

    @Test
    void 머리표를_지우고_빈줄과_중복을_뺀다() {
        String text = """
                - 와이어프레임
                • API 명세 정리
                1. QA 시트
                2) 배포 체크
                [ ] 회고 쓰기
                [x] 회고 쓰기

                ☐ 스토어 스크린샷
                - 와이어프레임
                """;
        assertThat(WorkTaskTextParser.parse(text, List.of("  추가 항목  ")))
                .containsExactly("와이어프레임", "API 명세 정리", "QA 시트", "배포 체크", "회고 쓰기", "스토어 스크린샷", "추가 항목");
    }

    @Test
    void 너무_긴_이름은_100자로_자른다() {
        assertThat(WorkTaskTextParser.parse("a".repeat(150), null).get(0)).hasSize(100);
    }

    @Test
    void 아무것도_없으면_빈목록() {
        assertThat(WorkTaskTextParser.parse(null, null)).isEmpty();
        assertThat(WorkTaskTextParser.parse("\n - \n", null)).isEmpty();
    }
}
