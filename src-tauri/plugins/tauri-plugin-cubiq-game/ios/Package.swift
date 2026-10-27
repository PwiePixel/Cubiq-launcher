// swift-tools-version:5.3
// Cubiq Launcher – Spiel-Engine für iOS (Amethyst-iOS). Wird vom iOS-Teil gefüllt.

import PackageDescription

let package = Package(
  name: "tauri-plugin-cubiq-game",
  platforms: [
    .iOS(.v14),
  ],
  products: [
    .library(
      name: "tauri-plugin-cubiq-game",
      type: .static,
      targets: ["tauri-plugin-cubiq-game"])
  ],
  dependencies: [
    .package(name: "Tauri", path: "../.tauri/tauri-api")
  ],
  targets: [
    .target(
      name: "tauri-plugin-cubiq-game",
      dependencies: [
        .byName(name: "Tauri")
      ],
      path: "Sources")
  ]
)
