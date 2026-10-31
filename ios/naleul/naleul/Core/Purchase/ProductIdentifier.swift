//
//  ProductIdentifier.swift
//  naleul
//
//  Created by 황유림 on 7/6/26.
//

// Core/Purchase/ProductIdentifier.swift
import Foundation

enum ProductIdentifier: String, CaseIterable {
    case premiumMonthly = "com.naleul.pro.monthly"
    case premiumYearly = "com.naleul.pro.yearly"

    static var allIDs: [String] {
        allCases.map { $0.rawValue }
    }
}
