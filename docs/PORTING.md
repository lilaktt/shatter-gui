# Портування на інші версії Minecraft і завантажувачі

Стан на 3.11.2026, гілка `multiversion`. Опублікована версія — 26.1.2 Fabric; її поведінка не змінювалась (перевірка —
в кінці). Перша ціль портування — 1.21.1 Fabric: у роботі (закриття зі склом, захоплення, рендер, зіткнення, «поверх блоків» і
власний тестовий харнес готові; решта ефектів, редактор, інтеграції — далі; NOTES.md, «1.21.1 Fabric»).

## Структура

```
core/              чиста логіка: без Minecraft, без завантажувача (Java 21, лише Gson і SLF4J)
  src/main/java      пакети net.shattergui.* (ті самі імена, що й раніше), інтерфейси платформи — net.shattergui.platform
  src/main/resources assets/shatter_gui: мови, ефекти, шаблони, приклади, themes.json, іконка
  src/contract/java  контракт PlatformScreen: компілюється лише з core, у jar не потрапляє
  src/test/java      юніт-тести (усі 85 колишніх)
platform-26/       Minecraft 26.1.2 + Fabric: усе, що залежить від гри; game tests (src/gametest)
platform-1.21.1/   Minecraft 1.21.1 + Fabric: порт у роботі; тести — власний харнес (src/gametest)
```

Core не має власного jar: кожен platform-модуль компілює `core/src/main/java` ще раз разом зі своїм кодом (своя Java,
свої бібліотеки гри) і пакує у свій jar. Окрема збірка `:core` гарантує, що core компілюється без Minecraft узагалі
(на класшляху лише Gson 2.10.1 і SLF4J 2.0.9 — найстаріші серед підтримуваних версій), а задача `checkNoMinecraft`
падає, якщо в core з'явиться `net.minecraft`, `com.mojang`, `net.fabricmc`, `org.spongepowered`, `org.lwjgl`,
`net.irisshaders` навіть у повному імені чи коментарі.

Як core знаходить платформу: `Platform.get()` (ліниво, `ServiceLoader`, файл
`META-INF/services/net.shattergui.platform.Platform` у platform-модулі). Тому неважливо, хто перший торкнеться core
(точка входу, mixin, Mod Menu).

Екрани. Ігрові тести (і Mod Menu, і ваніль) працюють з екранами мода як з `Screen` гри (`instanceof`,
`mouseClicked(MouseButtonEvent)`), тож `UiScreen`, `ShatterConfigScreen`, `FxEditorScreen` мусили лишитись справжніми
екранами гри з тими самими іменами. Рішення — «expect/actual»: core-класи наслідують `net.shattergui.platform.PlatformScreen`,
якого в core немає. Кожна платформа дає свій клас з цим іменем поверх свого `Screen`
(`platform-26/.../PlatformScreen.java`, `platform-1.21.1/.../PlatformScreen.java`), що перекладає кадр і введення гри
на мову core (`renderUi(GuiDrawing…)`, `uiMouseClicked(x, y, button, doubleClick)`, `uiKeyPressed(KeyInput)` …).
Що саме core може використовувати з цього класу, задає копія-контракт у `core/src/contract/java`: core компілюється
лише проти неї.

## Збірка

```
./gradlew build                      # core (+ юніт-тести, checkNoMinecraft) і 26.1.2: build/libs/shatter-gui-0.1.0-beta+mc26.1.2-fabric.jar
./gradlew build -Pmc=1.21.1          # core і заготовка 1.21.1:          build/libs/shatter-gui-0.1.0-beta+mc1.21.1-fabric.jar
./gradlew build -Pmc=all             # усі по черзі (на слабкій машині — краще по одній)
./gradlew :platform-26:runClientGameTest [-PspatialGui=1.8] [-Pshaders -PshaderPack=…] [-PtestOnly=…]
```

`settings.gradle` підключає лише вибрані platform-модулі (`-Pmc`, типово 26.1.2), тож Loom ніколи не налаштовує дві
версії Minecraft за один запуск. Ім'я jar: `shatter-gui-<mod_version>+mc<minecraft_version>-<mod_loader>.jar`, версія
мода всередині (`fabric.mod.json`, «Про мод») лишається `mod_version`. Версії гри, завантажувача, Fabric API і Java —
у `platform-*/gradle.properties`; спільні (мод, Loom) — у кореневому `gradle.properties`.

## Вибір інструмента

| | Stonecutter (± Stonecraft) | Architectury | Простий multi-module (вибрано) |
|---|---|---|---|
| Що це | Gradle-плагін: один `src/`, на кожну версію — підпроєкт, код перемикається коментарями `//? if >=1.21.5 {` (Stonecraft — надбудова з готовим налаштуванням Loom/NeoForge і публікацією) | Свій форк Loom + `common`/`fabric`/`neoforge` модулі (і, за бажанням, Architectury API) для кількох завантажувачів однієї версії | Gradle-підпроєкти: `core` без гри, `platform-<версія>[-<завантажувач>]` з грою; Fabric Loom 1.15 з двома id плагіна |
| Відмінності версій | Розкидані умовними блоками по спільних файлах (саме те, чого ця задача уникає) | Не розв'язує: одна версія гри на збірку, мультиверсія — гілками або разом зі Stonecutter | Ізольовані в `platform-*`, спільне — в `core`, межа — інтерфейси |
| 26.1.2 ↔ 1.21.1 | Різниця тут — не рядки, а цілі підсистеми: `GuiGraphicsExtractor` + `GuiRenderState` проти негайного `GuiGraphics`, `GpuDevice`/`CommandEncoder` проти OpenGL-`RenderTarget`, `SubmitNodeCollector`, `KeyEvent`-записи проти чисел. Умовні блоки в `ShardRenderer`, `ScreenCapture`, `OnTopPass` зробили б їх нечитабельними | Не допомагає (питання версій, а не завантажувачів) | Кожна версія має власні `render/`, `capture/`, `mixin/` |
| Ремап | Сам по собі ні; Loom усередині | Свій Loom; підтримка 26.x без обфускації не перевірялась | Loom 1.15.5 має і `net.fabricmc.fabric-loom` (26.x без обфускації), і `net.fabricmc.fabric-loom-remap` (1.21.1 з мапінгами Mojang) — перевірено в `META-INF/gradle-plugins` jar-а Loom |
| Нові залежності | Плагін (останній 0.9.8 на maven.kikugie.dev) | Форк Loom (останній 1.17-SNAPSHOT на maven.architectury.dev), можливо API як залежність для гравців | Жодних |
| NeoForge згодом | Через Stonecraft | Природно | Модуль `platform-26-neoforge` (ModDevGradle) з тим самим `core`; mixin-и й події — свої |

Вибрано простий multi-module: він єдиний ізолює відмінності версій у модулях, а не в коментарях, не додає залежностей і
працює з тим Loom, що вже є. Якщо колись знадобиться багато сусідніх версій (1.21.1, 1.21.4, 1.21.5 …), де платформи
відрізняються на кілька рядків, Stonecutter можна додати *всередині* одного platform-модуля, не чіпаючи core.

## (a) Чиста логіка — `core`

Жоден клас нижче не імпортує Minecraft, завантажувач, Iris чи Spatial GUI (перевіряє збірка). Те, що їм треба від гри,
вони отримують через `net.shattergui.platform`.

| Пакет | Класи | Що це |
|---|---|---|
| `fx` | `FxDef`, `FxParser`, `FxSchema`, `FxProblems`, `FxLimits`, `FxIOException`, `FxPackage`, `Node`, `Value`, `Easing`, `Gradient`, `Fracture`, `FxSimulation` | Формат ефектів schema 2, парсер і валідація (шляхи JSON), криві й градієнти, розбиття (voronoi, grid, радіальне …), симуляція уламків як математика в площині зображення (рух, модулі, зіткнення, частинки-«спрайти», звуки як події) |
| `fx.game` | `FxTextures`, `FxRun`, `FxFiles` | Текстури ефекту (перевірка PNG, ліміти, яскравість масок; GPU — через `UiTextures`), запуск симуляції з налаштуваннями гравця (спільний для світу й прев'ю), теки ефектів і коди обміну |
| `params` | `Param`, `Params`, `ParamValues`, `ParamStore`, `Preset`, `Quality`, `OpenTiming`, `BundledParams` | Реєстр параметрів, пресети, якість, тривалість відкриття |
| `config` | `ShatterConfig`, `Themes`, `ScreenKeys` | Модель конфігу (`config/shatter_gui.json`, міграції), тематичні правила |
| `config.screen` | `ShatterConfigScreen`, `SettingRow`, `ParamUi`, `Thumb` | Екран налаштувань: вкладки, картки ефектів, екрани, «Про мод», власні ефекти |
| `custom` | `CustomEffectDef` | Власний ефект schema 1 (база + параметри + частинки/звуки), читання, запис, коди |
| `editor` | `FxEditor`, `FxDoc` (документ, undo/redo знімками), `Timeline`, `CurveEditor`, `GradientEditor`, `ColorPicker`, `Inspector`, `LayerPanel`, `EffectFiles`, `EditorState`, `EditorChecks`, `TextureLibrary`, `Templates`, … (31 клас) | Візуальний редактор ефектів |
| `ui` | `Widget`, `UiScreen`, `Ui`, `Draw`, `Container`, `ScrollView`, `TextField`, `Dropdown`, `Slider`, `Section`, `Theme`, `Icon`, `Anim`, `Reveal`, `MouseGuard`, … (30 класів) | Бібліотека UI: розкладка, фокус, оверлеї, підказки, стійкість до збоїв віджетів |
| `ui.preview` | `Sim`, `GlassSim`, `BurnSim`, `FlySim`, `DissolveSim`, `VortexSim`, `ReversedSim`, `FxSim`, `ExtrasSim`, `Sims`, `Scene`, `PreviewPlayer`, `SamplePainter`, `SampleImage` | 2D-прев'ю ефектів, намальовані зразки інтерфейсів |
| `debug` | `ShatterProfiler`, `CloseWatchdog` | Профіль і сторож часу закриття |
| — | `ShatterMod` (id і лог), `ModKeys` (що роблять клавіші, головний вимикач), `effect.EffectIds`, `effect.ShardTint`, `shatter.ImageRect` | |
| `platform` | інтерфейси (нижче) | Межа з грою |
| ресурси | `assets/shatter_gui/lang/*.json` (12 мов), `effects/*.json` (27), `editor/templates`, `examples`, `themes.json`, `icon.png` | Переклади й дані однакові для всіх версій |

## (b) Залежить від версії — `platform-26`

Позначки: **R** рендер (blaze3d, `RenderType`, `RenderPipeline`, `SubmitNodeCollector`), **G** малювання GUI
(`GuiGraphicsExtractor`, `GuiRenderState`), **C** захоплення екрана (GPU-копії), **M** mixin, **I** введення
(`KeyEvent`, `MouseButtonEvent`, `KeyMapping`, події Fabric), **P** частинки, **S** звуки, **Rg** реєстри (`Identifier`,
`BuiltInRegistries`), **Rs** ресурси (`ResourceManager`), **K** ключі керування, **W** світ (`ClientLevel`, блоки, `Vec3`,
камера), **F** Fabric API/Loader, **Ir** Iris, **Sp** Spatial GUI. «Висока» чутливість — змінювалось між 1.21.1 і 26.1
(перевірено за офіційними мапінгами 1.21.1 і javap 26.1.2), «середня» — ті самі ідеї, інші імена/сигнатури, «низька» —
стабільний API.

| Клас | Що робить | Залежності | Чутливість |
|---|---|---|---|
| `ShatterGuiClient` | точка входу: порядок ініціалізації, тік, `LevelRenderEvents.COLLECT_SUBMITS` | F, R, Rs, Rg | висока (події рендера Fabric інші в 1.21.1) |
| `ShatterKeys` | реєстрація `KeyMapping` у своїй категорії, опитування | K, I, F | середня (`KeyMapping.Category`, `KeyMappingHelper` — нові в 26.1; у 1.21.1 категорія-рядок і `KeyBindingHelper`) |
| `ScreenRules` | ключ екрана (тип меню, класи), які екрани ніколи не розбиваються | G, Rg | середня |
| `capture.ScreenCapture`, `capture.CapturePool` | кадр закриття: GUI на прозорий таргет, `copyTextureToTexture` у слот пулу | C, R, G, W | висока (`GpuDevice`, `CommandEncoder`, `GpuTexture` у 1.21.1 немає) |
| `render.ShardRenderer` | вершини уламків, освітлення на CPU, типи для Iris | R, W, F | висока |
| `render.ShardRenderTypes` | свій `RenderPipeline` (premultiplied) через accessor | R, M | висока (`RenderPipelines` у 1.21.1 немає; одноіменний `RenderPipeline` 1.21.1 — інший клас) |
| `render.OnTopPass`, `render.DecorRenderer`, `render.WaveDecor` | «поверх блоків» (свій прохід після світу), накладки schema 2 | R, W | висока |
| `mixin.*` (10) | `MinecraftMixin` (тригер на `setScreen`), `GameRendererMixin` (кадр захоплення, матриці, прохід), `ScreenMixin` (фон), `GuiRenderStateMixin`, accessor-и | M + R/G | висока: цілі й дескриптори свої в кожній версії |
| `compat.spatial.*` (8) | площина Spatial GUI (міст, детектор, mixin-plugin, accessor-и) | Sp, M, C, R | висока (Spatial GUI для 1.21.1 — інший jar; перевіряти javap) |
| `compat.iris.*` | `IrisApi.isShaderPackInUse` | Ir, F | низька (публічний API) |
| `compat.modmenu.ShatterModMenu` | кнопка налаштувань | Mod Menu API | низька (інша версія Mod Menu) |
| `effect.*` (13) | вбудовані ефекти (скло, горіння, політ, розчинення, вихор), реєстр `ShatterEffects`, `ReversedEffect` (запис і відтворення назад), `EdgeTransition` | W, P, S | середня (`Vec3`, `RandomSource`, `ClientLevel` стабільні; частинки й звуки — через `EffectParticles`/`EffectSounds`) |
| `effect.EffectParticles`, `effect.EffectSounds` | спавн частинок (масштаб за відстанню), UI-звуки | P, S | середня |
| `mixin.SingleQuadParticleAccessor` | альфа частинок горіння | M, P | висока (у 1.21.1 поле `alpha` у `Particle`, не в `SingleQuadParticle`) |
| `shatter.*` (8) | `ShardManager` (хвилі, ліміти, тік), `ShardInstance` (фізика, зіткнення), `ShardMesh`, `BlockCollision`, `CameraPlane`, `ShardPlane`, `ShardSource`, `EffectClock` | W, R (частково), JOML | середня |
| `fly.*` (5) | політ у блок/з блока, джерело (скриня, сутність), відкриття-збирання | W, I, G, F | середня–висока (`UseBlockCallback`, `ScreenEvents`) |
| `fx.game.DataEffect`, `fx.game.DataWave`, `fx.game.FxEffects` | schema 2 у світі (уламки, частинки, звуки з реєстрів), завантаження з тек/пакетів/ресурспаків | W, P, S, Rg, Rs, F | середня |
| `custom.CustomEffect`, `custom.CustomEffects` | schema 1 у світі, завантаження | W, P, S, Rg, Rs | середня |
| `config.ScreenCatalog`, `config.screen.ScreenIcons`, `config.screen.PreviewRunner` | бачені екрани (назви модів — Loader), іконки (предмети, іконки модів), прев'ю ефекту у світі | G, F, Rg | середня |
| `ui.preview.RealPreview` | справжні ванільні екрани в текстуру для прев'ю | C, G, R, W | висока |
| `debug.FrameAudit` | аудит кадрів для тестів | G | середня |
| `platform.PlatformScreen`, `platform.impl.*` (13) | реалізація інтерфейсів core для 26.1.2 (нижче) | усі | висока — це і є версійна частина UI |

## Інтерфейси платформи (`core/src/main/java/net/shattergui/platform`)

`Platform` — точка доступу; кожен метод повертає один сервіс. Реалізація 26.1.2 лише пересилає виклики класам, що
робили цю роботу до появи core (`platform-26/.../platform/impl`).

| Інтерфейс | Що покриває | 26.1.2 | Хто з core викликає |
|---|---|---|---|
| `GuiDrawing` (+ `UiPose`, `UiRect`, `UiItem`, `UiTexture`) | прямокутники, градієнти, рамки, текст, іконки предметів, матриця, scissor, шари, трикутники з текстурою | `GuiDrawing26` над `GuiGraphicsExtractor` (трикутники — власний `GuiElementRenderState`, як раніше в `Draw`) | `ui.Ui`, `ui.Draw`, усі віджети |
| `Text`, `Texts`, `UiFont`, `TextLine` | переклади (з аргументами, ліниво), літерали, шрифт: ширина, обрізання, перенос | `ComponentText` (обгортка `MutableComponent`), `Texts26` | увесь UI, редактор, налаштування |
| `KeyInput`, `CharInput`, `Keys` | клавіші й символи; коди GLFW (звірено з `InputConstants` 26.1.2) | `Input26` (кожна відповідь — від `KeyEvent` гри) | віджети, `UiScreen` |
| `PlatformScreen` (клас, контракт) | екран гри, на якому стоять екрани core | `platform/PlatformScreen` над `Screen` | `UiScreen` |
| `UiTextures` | намальовані зразки, PNG-декодер гри, завантаження на GPU, звільнення | `UiTextures26` (`NativeImage`, `DynamicTexture`, `TextureManager`) | `SampleImage`, `FxTextures` |
| `ScreenCapture` | захоплення (скасування очікуваного), справжнє прев'ю екранів, ефект у світі з редактора | `Capture26` → `capture.ScreenCapture`, `RealPreview`, `PreviewRunner` | `ShatterConfigScreen`, `FxEditor`, `PreviewPane`, `SampleImage`, `ModKeys` |
| `ShardRenderer` | уламки у світі (зараз: скинути всі) | `ShardManager::clear` | `ModKeys.setEnabled` |
| `InputHooks` | хуки екранів і використання блоків (зараз: зупинити виліт) | `FlyOpen::stop` | `ModKeys.setEnabled` |
| `SpatialGuiBridge` | Spatial GUI (зараз: скасувати очікуване розбиття площини) | `SpatialGuiCompat::cancelPending` | `ModKeys.setEnabled` |
| `ShaderCompat` | шейдерпак увімкнено | `IrisCompat::shaderPackInUse` | поки ніхто з core (рендер у platform-26 кличе `IrisCompat` напряму) — контракт для наступного кроку |
| `ParticleSpawner` | реєстр частинок без опцій, налаштування частинок гравця | `Registries26.Particles` (+ `EffectParticles.allowed`) | `CustomEffectDef`, `Inspector`, `EditorChecks` |
| `SoundPlayer` | клік кнопки, клік закриття, реєстр звуків, програвання | `Registries26.Sounds` | `Widget`, `Inspector`, `CustomEffectDef`, `EditorChecks` |
| `KeyBindings` | реєстрація клавіш (що вони роблять — `ModKeys` у core) | `ShatterKeys` | `ModKeys.register` |
| `ConfigPaths` | тека конфігу | `FabricLoader.getConfigDir()` | `ShatterConfig`, `FxFiles` |
| `EffectCatalog` | реєстр ефектів, як його бачать налаштування й редактор: списки, імена, schema 2 (дані, коди, імпорт, перезавантаження, тестова збірка), schema 1 | `Effects26` → `ShatterEffects`, `FxEffects`, `CustomEffects` | налаштування, редактор, прев'ю, `ModKeys` |
| `GameInfo` | бачені екрани, моди (метадані), іконки екранів, світ/екран є, показати екран, action bar | `Game26` | налаштування, `ModKeys` |
| `Desktop` | буфер обміну, відкрити теку, масштаб GUI | `Game26` | `TextField`, редактор, налаштування, `Ui.smallScale` |

Найважче розділялись:
- `ShatterConfigScreen` (1931 рядок, посилався на 17 ігрових класів: реєстр ефектів, schema 1/2, каталог екранів,
  іконки, справжнє прев'ю, прев'ю у світі, Loader-метадані, `ConfirmLinkScreen`, клавіші) — звідси `EffectCatalog`,
  `GameInfo`, методи `PlatformScreen` (`inWorld`, `runLater`, `showScreen`, `currentScreen`, `confirmLink`).
- `UiScreen`: щоб ігрові тести й Mod Menu й далі бачили справжній `Screen`, знадобився контракт `PlatformScreen`.
- `Draw.triangles` (власний елемент `GuiRenderState`, межі через матрицю, scissor) — переїхав у `GuiDrawing26` цілком;
  у core лишились премультиплікація й виклик.
- `DataEffect`/`FxTextures`: дані ефекту й текстури потрібні і світу, і прев'ю. Чисте (`FxTextures`, `FxRun.simulate`,
  `FxFiles`) пішло в core, `DataEffect` у platform-26 реалізує `EffectCatalog.Data`.
- `CustomEffectDef`: записи частинок і звуків тримали `Identifier`/`SimpleParticleType`; тепер рядок id (як його пише
  гра) + непрозорий об'єкт типу частинки від `ParticleSpawner`.

Що лишилось у platform-26 і чому: ефекти у світі (`effect`, `shatter`, `fly`, `fx.game.DataEffect/DataWave`,
`custom.CustomEffect`) — це ~6 тис. рядків фізики, де `Vec3`, `RandomSource`, `ClientLevel` (форми блоків для зіткнень),
частинки й звуки гри йдуть через кожен метод. Їх перенесення в core означало б замінити генератор випадкових чисел і
векторну арифметику по всьому коду — ризик непомітно змінити поведінку (порядок викликів `random` визначає вигляд
кожного ефекту), а користі для 1.21.1 мало: `Vec3`, `RandomSource`, `Mth`, `ClientLevel` у 1.21.1 ті самі. Тому для
1.21.1 цей шар найпростіше скопіювати з platform-26 і правити точково; наступний крок — винести його в спільний модуль
`platform-common` для версій із тим самим API світу (або в core з `ShardRenderer`/`ParticleSpawner`/`SoundPlayer`
як повними інтерфейсами).

## 1.21.1: що відомо і що найризикованіше

Перевірено за офіційними мапінгами Mojang 1.21.1 (`client.txt` з piston-meta) і `javap` 26.1.2:

| Тема | 26.1.2 | 1.21.1 | Наслідок |
|---|---|---|---|
| Малювання GUI | `Screen#extractRenderState(GuiGraphicsExtractor…)`, `GuiRenderState`, страти, `GuiElementRenderState` | `Screen#render(GuiGraphics…)`, малює одразу; `GuiGraphics#flush` | `GuiDrawing121` готовий, крім трикутників з текстурою (прев'ю): потрібен `BufferBuilder` + `RenderSystem` |
| Захоплення | `GpuDevice`, `CommandEncoder`, `GpuTexture`, `copyTextureToTexture` | нічого з цього немає; `RenderTarget`/`TextureTarget` на OpenGL | `ScreenCapture`/`CapturePool`/`RealPreview` — переписати (блит між framebuffer-ами) |
| Свій конвеєр | `RenderPipeline` + `RenderPipelines#register`, `RenderSetup` | `RenderPipelines` немає; `com.mojang.blaze3d.pipeline.RenderPipeline` — інший клас (черга викликів); `RenderType.create` + шейдер | `ShardRenderTypes` — інакше; премультиплікований блендинг через `RenderStateShard` |
| Рендер світу | `LevelRenderEvents.COLLECT_SUBMITS`, `SubmitNodeCollector`, `ProjectionMatrixBuffer` | немає; `WorldRenderEvents` Fabric (є в 0.116.17, перевірено) | `ShardRenderer`, `OnTopPass` — переписати |
| Введення | `KeyEvent`, `MouseButtonEvent` (з подвійним кліком), `CharacterEvent` (кодова точка) | числа; подвійного кліку немає; `charTyped(char, int)` — UTF-16 одиниці | `Input121` готовий; подвійний клік і символи поза BMP — дописати |
| Клавіші | `KeyMapping.Category.register(Identifier)`, `KeyMappingHelper` | категорія — рядок, `KeyBindingHelper` (є в Fabric API 0.116.17+1.21.1, перевірено в jar) | заглушка |
| Ігрові тести | `fabric-client-gametest-api-v1` (усі 30 сценаріїв `src/gametest`) | у Fabric API 0.116.17+1.21.1 цього модуля немає (перевірено: жодного класу gametest у jar) | тести 1.21.1 — інший запуск (свій харнес на `ClientTickEvents` або ручна перевірка) |
| Ідентифікатори | `Identifier`, `Registry#getValue`, `ResourceKey#identifier` | `ResourceLocation`, `Registry#get`, `ResourceKey#location` | механічно (зроблено в заглушці) |
| Текстури | `NativeImage#getPixel/setPixel` — ARGB | `getPixelRGBA/setPixelRGBA` — ABGR | у `PlatformImpl` 1.21.1 — обмін R↔B |
| Частинки | `SingleQuadParticle.alpha` | `Particle.alpha` | ціль accessor-а інша |
| Повідомлення | `Player#sendOverlayMessage` | `displayClientMessage(…, true)` | зроблено |
| `Util` | `net.minecraft.util.Util` | `net.minecraft.Util` | зроблено |
| Spatial GUI | 1.8/1.9 для 26.1.x | на Modrinth є 1.8, 1.9, 1.9.1 для 1.21.1 (Fabric і NeoForge) — окремі jar-и | звірити поля й методи javap-ом, як для 26.1 (NOTES.md, «Spatial GUI: розслідування») |

Найризикованіше: захоплення кадру й рендер уламків. У 26.1 вони спираються на GPU-абстракцію (`GpuDevice`,
`CommandEncoder`, двоетапний GUI), якої в 1.21.1 немає зовсім — це не правка імен, а новий код з новими пастками
(порядок кадру, коли GUI вже намальовано, премультиплікований альфа, v-інверсія, Iris). Друге — mixin-и в
`Minecraft#setScreen` і `GameRenderer#render`: їхні точки ін'єкції треба знаходити заново по байткоду 1.21.1. Третє —
перевірка: у Fabric API для 1.21.1 немає клієнтських ігрових тестів, тож автоматична сітка безпеки, на якій тримається
26.1.2 (повний прогін, crawler, мови, скріншоти), для 1.21.1 відсутня, доки не буде свого харнесу.

Порядок портування 1.21.1: (1) `ShardRenderTypes` + `ShardRenderer` без «поверх блоків» → (2) `ScreenCapture`
(GL-блит) → (3) mixin-и тригера → (4) ефекти (копія з 26 + точкові правки частинок/звуків) → (5) `OnTopPass`, Spatial GUI,
справжнє прев'ю. Ігрові тести: спершу простий власний харнес (сценарії на `ClientTickEvents` + скріншоти через
`Screenshot`), бо API клієнтських тестів Fabric для 1.21.1 немає.

## 26.2 і 26.3

Опублікований jar на них не запускається: `"minecraft": "~26.1.2"` зупиняє Fabric Loader, а з примусовим
перевизначенням залежності гра падає на старті (`MinecraftMixin`: поля `Minecraft#screen` уже немає). Core
компілюється проти 26.2 і 26.3 без змін; `platform-26` — ні (56 і 119 помилок): екран переїхав у `Minecraft#gui`,
головний таргет і камера — у `GameRenderer#mainRenderTarget()/mainCamera()`, у 26.3 GPU-API — у
`com.mojang.renderpearl.api.*`. Тож кожна з них — свій модуль `platform-26.2`, `platform-26.3` (копія `platform-26` з
точковими правками) з тим самим core. Подробиці й таблиця змін — у NOTES.md («26.2 і 26.3»).

## Перевірка рефакторингу (26.1.2)

`build` (85/85 юніт-тестів), повний `runClientGameTest` без Spatial GUI, з `-PspatialGui=1.8`, з Iris + шейдерпаком,
`crawler` (3237 дій, 0 збоїв), `lang` (uk_ua, ja_jp) — так само, як на незміненому `main`. Поодинокі провали
(`ontop_flat`, `spatial_collide_land`, `ontop_spatial_on_glass`) траплялись і до, і після змін — це перевірки за
випадковим розльотом уламків біля порогу; повтор проходить. Ресурси jar побайтово ті самі, класи з незміненого коду —
теж. Скріншоти чотирьох повних прогонів (база ×2, після ×2) порівняно попіксельно: усі відмінності пояснені —
випадкове розбиття, фаза анімацій, ванільні тости, стан світу (див. NOTES.md, «Мультиверсія»).
