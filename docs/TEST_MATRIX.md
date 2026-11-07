# Test matrix

Written by `./gradlew runVersionMatrix` (`gradle/version-matrix.gradle`, scenarios in `MatrixScenario`). Last run started 2026-11-06 22:47. Each row is one game started by Gradle with the mod built for that version.

| Version | Mode | Start | Glass: chest | Glass: inventory | Opening effect | Fly to container | Esc | Over blocks | Crawler | Time | Sheet |
|---|---|---|---|---|---|---|---|---|---|---|---|
| 26.1 | plain | OK | OK | OK | OK | OK | OK | OK | OK | 5 min | [sheet](../build/version-matrix/26.1_plain.png) |
| 26.1 | shaders | OK | OK | OK | OK | OK | OK | OK | OK | 4 min | [sheet](../build/version-matrix/26.1_shaders.png) |
| 26.1 | spatial | OK | OK | OK | OK | OK | OK | OK | OK | 5 min | [sheet](../build/version-matrix/26.1_spatial.png) |
| 26.1 | vulkan | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | 0 min | - |
| 26.1.1 | plain | OK | OK | OK | OK | OK | OK | OK | OK | 4 min | [sheet](../build/version-matrix/26.1.1_plain.png) |
| 26.1.1 | shaders | OK | OK | OK | OK | OK | OK | OK | OK | 4 min | [sheet](../build/version-matrix/26.1.1_shaders.png) |
| 26.1.1 | spatial | OK | OK | OK | OK | OK | OK | OK | OK | 4 min | [sheet](../build/version-matrix/26.1.1_spatial.png) |
| 26.1.1 | vulkan | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | 0 min | - |
| 26.1.2 | plain | OK | OK | OK | OK | OK | OK | OK | OK | 5 min | [sheet](../build/version-matrix/26.1.2_plain.png) |
| 26.1.2 | shaders | OK | OK | OK | OK | OK | OK | OK | OK | 6 min | [sheet](../build/version-matrix/26.1.2_shaders.png) |
| 26.1.2 | spatial | OK | OK | OK | OK | OK | OK | OK | OK | 4 min | [sheet](../build/version-matrix/26.1.2_spatial.png) |
| 26.1.2 | vulkan | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | 0 min | - |
| 26.2 | plain | OK | OK | OK | OK | OK | OK | OK | OK | 6 min | [sheet](../build/version-matrix/26.2_plain.png) |
| 26.2 | shaders | OK | OK | OK | OK | OK | OK | OK | OK | 8 min | [sheet](../build/version-matrix/26.2_shaders.png) |
| 26.2 | spatial | OK | OK | OK | OK | OK | OK | OK | OK | 5 min | [sheet](../build/version-matrix/26.2_spatial.png) |
| 26.2 | vulkan | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | SKIPPED | 0 min | - |
| 26.3 | plain | OK | OK | OK | OK | OK | OK | OK | OK | 5 min | [sheet](../build/version-matrix/26.3_plain.png) |
| 26.3 | shaders | OK | OK | OK | OK | OK | OK | OK | OK | 4 min | [sheet](../build/version-matrix/26.3_shaders.png) |
| 26.3 | spatial | OK | OK | OK | OK | OK | OK | OK | OK | 6 min | [sheet](../build/version-matrix/26.3_spatial.png) |
| 26.3 | vulkan | OK | OK | OK | OK | OK | OK | OK | OK | 3 min | [sheet](../build/version-matrix/26.3_vulkan.png) |

## Not OK

- 26.1 vulkan, every step: SKIPPED, this version has no Vulkan backend
- 26.1.1 vulkan, every step: SKIPPED, this version has no Vulkan backend
- 26.1.2 vulkan, every step: SKIPPED, this version has no Vulkan backend
- 26.2 vulkan, every step: SKIPPED, this version has no Vulkan backend
- 26.3 spatial: the game was restarted 1 time(s) after a native crash before its first result

## What ran

- 26.1 plain: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.1
- 26.1 shaders: GPU: NVIDIA GeForce RTX 3050 6GB Laptop GPU/PCIe/SSE2, Driver: 3.3.0 NVIDIA 610.62, GPU vendor: NVIDIA Corporation, Graphics backend: OpenGL, Minecraft: 26.1
- 26.1 spatial: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.1
- 26.1.1 plain: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.1.1
- 26.1.1 shaders: GPU: NVIDIA GeForce RTX 3050 6GB Laptop GPU/PCIe/SSE2, Driver: 3.3.0 NVIDIA 610.62, GPU vendor: NVIDIA Corporation, Graphics backend: OpenGL, Minecraft: 26.1.1
- 26.1.1 spatial: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.1.1
- 26.1.2 plain: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.1.2
- 26.1.2 shaders: GPU: NVIDIA GeForce RTX 3050 6GB Laptop GPU/PCIe/SSE2, Driver: 3.3.0 NVIDIA 610.62, GPU vendor: NVIDIA Corporation, Graphics backend: OpenGL, Minecraft: 26.1.2
- 26.1.2 spatial: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.1.2
- 26.2 plain: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.2
- 26.2 shaders: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.2
- 26.2 spatial: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.2
- 26.3 plain: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.3
- 26.3 shaders: GPU: NVIDIA GeForce RTX 3050 6GB Laptop GPU/PCIe/SSE2, Driver: 3.3.0 NVIDIA 610.62, GPU vendor: NVIDIA Corporation, Graphics backend: OpenGL, Minecraft: 26.3
- 26.3 spatial: GPU: Intel(R) RaptorLake-S Mobile Graphics Controller, Driver: 3.3.0 - Build 32.0.101.7026, GPU vendor: Intel, Graphics backend: OpenGL, Minecraft: 26.3
- 26.3 vulkan: GPU: NVIDIA GeForce RTX 3050 6GB Laptop GPU, Driver: 1.4.341 NVIDIA 610.62, GPU vendor: NVIDIA, Graphics backend: Vulkan, Minecraft: 26.3
