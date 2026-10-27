// swift-tools-version:5.3
// Cubiq Launcher – Push-Benachrichtigungen auf iOS: Abholen im Hintergrund (BGAppRefreshTask) + lokale Hinweise.

import PackageDescription

let package = Package(
  name: "tauri-plugin-cubiq-push",
  platforms: [
    .iOS(.v14),
  ],
  products: [
    .library(
      name: "tauri-plugin-cubiq-push",
      type: .static,
      targets: ["tauri-plugin-cubiq-push"])
  ],
  dependencies: [
    .package(name: "Tauri", path: "../.tauri/tauri-api")
  ],
  targets: [
    .target(
      name: "tauri-plugin-cubiq-push",
      dependencies: [
        .byName(name: "Tauri")
      ],
      path: "Sources")
  ]
)
