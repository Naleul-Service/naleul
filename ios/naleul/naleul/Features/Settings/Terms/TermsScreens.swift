// Features/Settings/Terms/TermsScreens.swift
//
// 웹 원본: app/(main)/settings/terms/page.tsx (약관 목록)
//          app/(main)/settings/terms/[type]/page.tsx (약관 상세)

import SwiftUI

/// 약관 목록 (설정 > 약관 동의)
struct TermsListScreen: View {
    init() {}

    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                ForEach(Array(TermsType.allCases.enumerated()), id: \.element.id) { i, type in
                    if i > 0 { Rectangle().fill(Color.borderSoft).frame(height: 1) }
                    NavigationLink(value: AppRoute.settings(.termsDetail(type.rawValue))) {
                        HStack(spacing: 12) {
                            Text(type.listTitle)
                                .font(.pt(15, .semibold))
                                .foregroundStyle(Color.ink)
                                .frame(maxWidth: .infinity, alignment: .leading)
                            NSymbol("chevron.right", size: 13, weight: .semibold)
                                .foregroundStyle(Color.ink4)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 16)
                        .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .simultaneousGesture(TapGesture().onEnded {
                        ST.track("terms.item_click", ["terms_type": type.rawValue, "entry_point": "settings"])
                    })
                }
            }
            .card()
            .padding(16)
        }
        .canvasBackground()
        .navigationTitle("약관 동의")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if !didTrack {
                didTrack = true
                ST.track("terms.pageview")
            }
        }
    }

    @State private var didTrack = false
}

/// 약관 상세 — "service" | "privacy" | "marketing"
struct TermsDetailScreen: View {
    let typeRaw: String

    init(type: String) { self.typeRaw = type }

    private var type: TermsType? { TermsType(rawValue: typeRaw) }

    /// 스크롤 깊이 — 보이는 높이 · 이미 보낸 구간
    @State private var viewport: CGFloat = 0
    @State private var scrollSent: Set<Int> = []

    /// [UX] 본문 아래 끝이 화면 아래로 들어온 비율 25 · 50 · 75 · 100% 마다 한 번
    private func sawScroll(minY: CGFloat, height: CGFloat) {
        guard let type, height > 0, viewport > 0 else { return }
        let pct = (viewport - minY) / height * 100
        for mark in [25, 50, 75, 100] where pct >= CGFloat(mark - 1) && !scrollSent.contains(mark) {
            scrollSent.insert(mark)
            ST.track("terms.detail_scroll_depth", ["terms_type": type.rawValue, "scroll_percent": mark])
        }
    }

    var body: some View {
        ScrollView {
            if let type {
                VStack(alignment: .leading, spacing: 16) {
                    // 본문은 줄바꿈이 들어간 평문이라 그대로 보여줘요
                    Text(type.body)
                        .font(.pt(14))
                        .foregroundStyle(Color.ink2)
                        .lineSpacing(7)
                        .textSelection(.enabled)
                        .frame(maxWidth: .infinity, alignment: .leading)

                    if type == .marketing {
                        TermsInfoTable(rows: TermsText.marketingRows)
                    }
                }
                .padding(20)
                .card()
                .background(GeometryReader { g in
                    let f = g.frame(in: .named("terms-scroll"))
                    Color.clear
                        .onAppear { sawScroll(minY: f.minY, height: f.height) }
                        .onChange(of: f.minY) { _, y in sawScroll(minY: y, height: f.height) }
                })
                .padding(16)
            } else {
                EmptyStateCard(icon: "doc.text", title: "약관을 찾을 수 없어요")
                    .padding(16)
            }
        }
        .coordinateSpace(name: "terms-scroll")
        .background(GeometryReader { g in
            Color.clear
                .onAppear { viewport = g.size.height }
                .onChange(of: g.size.height) { _, h in viewport = h }
        })
        .canvasBackground()
        .navigationTitle(type?.detailTitle ?? "약관")
        .navigationBarTitleDisplayMode(.inline)
    }
}

/// 마케팅 동의의 "수집 정보" 표 (항목 | 설명)
private struct TermsInfoTable: View {
    let rows: [TermsInfoRow]

    var body: some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                cell("항목", header: true).frame(width: 96, alignment: .leading)
                Rectangle().fill(Color.borderSoft).frame(width: 1)
                cell("설명", header: true).frame(maxWidth: .infinity, alignment: .leading)
            }
            .fixedSize(horizontal: false, vertical: true)
            .background(Color.subtle)

            ForEach(rows, id: \.self) { row in
                Rectangle().fill(Color.borderSoft).frame(height: 1)
                HStack(spacing: 0) {
                    cell(row.label, header: false).frame(width: 96, alignment: .topLeading)
                    Rectangle().fill(Color.borderSoft).frame(width: 1)
                    cell(row.content, header: false).frame(maxWidth: .infinity, alignment: .leading)
                }
                .fixedSize(horizontal: false, vertical: true)
            }
        }
        .clipShape(RoundedRectangle(cornerRadius: Radius.xl))
        .overlay(RoundedRectangle(cornerRadius: Radius.xl).stroke(Color.line, lineWidth: 1))
    }

    private func cell(_ text: String, header: Bool) -> some View {
        Text(text)
            .font(.pt(14, header ? .semibold : .regular))
            .foregroundStyle(header ? Color.ink : Color.ink2)
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .frame(maxHeight: .infinity, alignment: .topLeading)
    }
}

#Preview("목록") {
    NavigationStack { TermsListScreen() }
}

#Preview("마케팅") {
    NavigationStack { TermsDetailScreen(type: "marketing") }
}

#Preview("서비스") {
    NavigationStack { TermsDetailScreen(type: "service") }
}
