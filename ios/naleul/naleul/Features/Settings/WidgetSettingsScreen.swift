// Features/Settings/WidgetSettingsScreen.swift
//
// 홈 화면 위젯 스타일 고르기 (main 브랜치 WidgetSettingView 복구 — 미션 위젯은 제외)
//
// 위젯을 홈 화면에 추가한 뒤 여기서 스타일을 바꾸면 바로 반영돼요.
// 위젯 데이터는 WidgetSync 가 App Group 에 채워요.

import SwiftUI
import WidgetKit

struct WidgetSettingsScreen: View {
    @State private var selected: WidgetDisplayType = WidgetDataStore.loadDisplayType()

    private let columns = [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)]

    init() {}

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 14) {
                VStack(alignment: .leading, spacing: 4) {
                    Text("나를 위젯 스타일 선택").font(.pt(17, .semibold)).foregroundStyle(Color.ink)
                    Text("위젯을 홈 화면에 추가한 후 여기서 스타일을 바꾸면 자동으로 반영돼요.")
                        .font(.pt(13)).foregroundStyle(Color.ink3)
                }

                LazyVGrid(columns: columns, spacing: 12) {
                    ForEach(WidgetDisplayType.allCases, id: \.self) { type in
                        styleCard(type)
                    }
                }

                ZStack {
                    RoundedRectangle(cornerRadius: 16).fill(Color.subtle)
                    Image(selected.previewImageName)
                        .resizable()
                        .scaledToFit()
                        .padding(16)
                }
                .frame(maxWidth: .infinity)
                .frame(height: 320)
                .clipShape(RoundedRectangle(cornerRadius: 16))
                .accessibilityLabel("\(selected.displayName) 미리보기")

                VStack(alignment: .leading, spacing: 6) {
                    Text("위젯 추가하는 법").font(.pt(14, .semibold)).foregroundStyle(Color.ink2)
                    Text("홈 화면 빈 곳을 길게 누르기 → 왼쪽 위 ‘편집’ → ‘위젯 추가’ → ‘나를’ 검색 → 큰 위젯 선택")
                        .font(.pt(13)).foregroundStyle(Color.ink3)
                        .lineSpacing(3)
                }
                .padding(.top, 4)
            }
            .pagePadding()
            .padding(.top, 12)
            .padding(.bottom, 24)
        }
        .canvasBackground()
        .navigationTitle("위젯")
        .navigationBarTitleDisplayMode(.inline)
        .task { await WidgetSync.refresh(force: true) }
        .onAppear {
            ST.track("widget_settings.pageview", ["widget_style": selected.rawValue])
        }
    }

    private func styleCard(_ type: WidgetDisplayType) -> some View {
        let isOn = selected == type
        return Button {
            guard selected != type else { return }
            ST.track("widget_settings.style_select", ["widget_style": type.rawValue, "previous_style": selected.rawValue])
            selected = type
            WidgetDataStore.saveDisplayType(type)
            WidgetCenter.shared.reloadTimelines(ofKind: WidgetDataStore.widgetKind)
        } label: {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 3) {
                    Text(type.displayName).font(.pt(14, .medium)).foregroundStyle(Color.ink)
                    Text(type.sizeLabel).font(.pt(12)).foregroundStyle(Color.ink3)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 18))
                    .foregroundStyle(isOn ? Color.brand : Color.ink4)
            }
            .padding(14)
            .background(Color.surface, in: RoundedRectangle(cornerRadius: 12))
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(isOn ? Color.brand : Color.line, lineWidth: isOn ? 2 : 1)
            )
            .contentShape(RoundedRectangle(cornerRadius: 12))
        }
        .buttonStyle(.plain)
        .accessibilityAddTraits(isOn ? .isSelected : [])
    }
}

#Preview {
    NavigationStack { WidgetSettingsScreen() }
}
