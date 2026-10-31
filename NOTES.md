# Нотатки розробки

Версії: MC 26.1.2 (немає обфускації, Mojang-імена), Loader 0.19.3, Loom 1.15, Fabric API 0.155.3+26.1.2,
Gradle 9.2.1, Java 25.

Перехід 26.1 → 26.1.2: `javap -p` усіх класів MC, які використовує мод, однаковий; байткод `GameRenderer#render`,
`#extractGui`, `Screen#extractBackground`, `Minecraft#setScreen`, `AbstractContainerMenu#getType`, read-back у
`Screenshot` теж однаковий. Нове в Fabric/MC для клавіші: `KeyMappingHelper`, `KeyMapping.Category.register`,
action bar — `Player#sendOverlayMessage` (не `displayClientMessage`).

## Коли спрацьовує

`MinecraftMixin`: на HEAD `setScreen(null)` запам'ятовується старий екран, рішення — на RETURN, бо ваніль перетворює
`null` на інше: `TitleScreen` без світу, `DeathScreen` для мертвого гравця, відновлений `ChatScreen`. Умови: після
виклику `screen == null`, `level` і `player` є, гравець живий. Ніколи: `LevelLoadingScreen` (його закривають
`setScreen(null)` після входу в світ), `ProgressScreen`, `ConnectScreen`, `GenericMessageScreen`,
`DisconnectedScreen`. Далі `applyTo` і `disabledScreens` (`ScreenRules`).

## Що змінилось у 26.1 і як це враховано

- `GuiGraphics` → `GuiGraphicsExtractor`. GUI тепер у два етапи: `Screen#extractRenderState…` наповнює
  `GuiRenderState`, а `GuiRenderer#render` малює його в головний `RenderTarget` (у `GameRenderer#render`, після світу).
- Фон екрана у світі: `Screen#extractBackground` → `extractBlurredBackground` (`blurBeforeThisStratum`) +
  `extractMenuBackground`. Панелі контейнерів малюють перевизначення `extractBackground` у підкласах.
- Рендер світу: Fabric `LevelRenderEvents` (замість `WorldRenderEvents`). Використано `COLLECT_SUBMITS` +
  `SubmitNodeCollector#submitCustomGeometry`. `poseStack()` там одинична, обертання камери в model-view, тож вершини
  задаються відносно позиції камери (`levelState().cameraRenderState.pos`).

## Захоплення (розділ 3.2) — реалізовано основний варіант, з обхідним маневром

Окремого «зарендерити екран у свою текстуру» в 26.1 немає: `GuiRenderer` завжди малює в головний target, а сам
`GuiRenderer` складно створити вдруге (PIP-рендери, атлас предметів, ring-буфери). Тому:

1. `MinecraftMixin` (див. «Коли спрацьовує»): запам'ятати екран, камеру, ефект і межі GUI з геометрії екрана
   (`AbstractContainerScreen`: `leftPos/topPos/imageWidth/imageHeight` × GUI scale; інші екрани — усе вікно).
2. Наступний кадр, `GameRendererMixin` (HEAD `extractGui`): замість HUD екстрагується старий екран, мишка далеко за
   екраном (без підсвітки й тултипа). `ScreenMixin` гасить розмитий/затемнений фон.
3. Навколо `GuiRenderer#render`, усе на GPU: головний колір → перевикористовуваний backup, очистити ціль у
   `0x00000000`, GUI малюється на прозоре, потім `copyTextureToTexture` у слот `CapturePool` і повернення світу з
   backup. Уламки семплюють слот напряму, ділянку GUI задають UV (v інвертовано: рядки framebuffer-а знизу вгору).
4. Слоти (`CapturePool`, макс. 8) зареєстровані в `TextureManager` один раз і перевикористовуються; GPU-текстура
   слота перестворюється лише при зміні розміру вікна. Слот звільняється з останнім уламком партії або при виході зі
   світу; пам'ять тримає щонайбільше 1 вільний слот + backup.

Історія фризу: спершу читання GPU→CPU (`copyTextureToBuffer` + попіксельний `setPixelABGR`) — 202–272 мс на кадр
1920×1080. Масове `memCopy` по рядках дало ~30–35 мс: вузьке місце — читання з відображеної (некешованої) GPU-пам'яті,
`USAGE_HINT_CLIENT_STORAGE` не допоміг. Тому читання прибрано зовсім; копія в слот + відновлення < 1 мс.

Наслідки, які треба знати:
- У кадр захоплення HUD (хотбар, приціл) не малюється — 1 кадр, непомітно.
- Екран екстрагується вже після `removed()`. Предмети, які клієнт прибирає з меню при закритті (напр. сітка крафту),
  можуть бути відсутні на уламках. Не перевірено в грі; якщо так — варіант: захоплювати кадр ще до закриття.
- Якщо за 5 кадрів кадр захоплення не відбувся або змінився розмір вікна — захоплення скасовується.

Іконка: `assets/shatter_gui/icon.png` (128×128). Без поля `icon` Mod Menu пише «has a broken icon».

## Площина (3.4)

Відстань 1.0 блока, розмір = рівно поле зору (`Camera#getFov`, вертикальний FOV у градусах), тож у першому кадрі уламки
лягають туди, де був GUI.

## Рендер (3.6)

`ShardRenderTypes`: копія ванільного `entity_translucent` (той самий `ENTITY_SNIPPET`, `ALPHA_CUTOUT 0.1`, без
culling), але з `BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA`, бо GUI намальований на прозорому тлі має
премультипліковані кольори. Кольори вершин теж премультиплікуються. Замість `PER_FACE_LIGHTING` — define
`NO_CARDINAL_LIGHTING` (є в `entity.vsh`): з `PER_FACE_LIGHTING` світло залежить від `gl_FrontFacing`, тобто від
порядку вершин. Дифузне затінення рахує `ShardRenderer` на CPU тією ж формулою, що `light.glsl`
(`0.4 + 0.6·Σmax(0, L·n)`, `L` = `Lighting.DIFFUSE_LIGHT_0/1`), і перші 10 тіків плавно вводить його з 1.0 — тож перший
кадр уламків такий же яскравий, як неосвітлений GUI. (У Незері ванільні напрямки інші — не враховано.) Пайплайн реєструється через
`RenderPipelines#register` (accessor), тож `ShaderManager` компілює його заздалегідь разом з ванільними. Тип малює
квадами, тому трикутник іде як квад з повтореною вершиною. Нормаль розвертається до камери.

Без CPU-копії картинки повністю прозорі трикутники не відкидаються (вони просто нічого не малюють).

## Ефекти

`effect/ShatterEffect`: `createShards` (геометрія з `ShardMesh` + початковий рух), `tick` (фізика й побічні дії,
напр. частинки), `tint` (колір центру й країв уламка за `partialTick`). Уламок малюється віялом із 3 трикутників
від центроїда, тож центр і край можуть мати різний колір.
Ефект глобальний (`effect`), `overrides` — лише ручні винятки. Ключ екрана: id типу меню
(`BuiltInRegistries.MENU.getKey(menu.getType())`; `InventoryMenu#getType` кидає `UnsupportedOperationException`,
тому ключ `minecraft:inventory`), потім повне й просте ім'я класу.

Розширення для `burn` (`glass` їх не використовує, поведінка не змінилась):
- `ShatterEffect#createWave` → `ShatterWave`, один об'єкт на хвилю з власним `tick`. Хвиля живе, поки є уламки
  або поки `tick` повертає `true` (так дим іде після того, як від екрана нічого не лишилось).
- `ShatterEffect#countsTowardShardLimit`: хвиля `burn` (~2900 трикутників) не враховується в `maxShards` і не
  витісняє інші хвилі.
- `ShardTint`: `scale` (уламок стискається до центроїда), `unlit` (без CPU-затінення), `glow` + `glowColor`.
- `ShardInstance`: `effectData` (у `burn` це тік займання), змінний `lifetime`, `tickStatic()` (без фізики).

## Burn

- Сітка `ShardMesh.build(source, 48·30)` з джитером, ~2900 трикутників. Із прогрітим JIT `spawn` триває ~2 мс,
  перший кадр ~1.4 мс; перше `burn` за сесію — до ~13 мс на спавн (холодний код), далі ні.
- Тік займання клітинки: `(1−y)·D·0.8 + (fbm(x,y)−0.5)·D·0.45 ± 0.5`, де `D = burnDurationTicks`, `y` — висота
  центроїда в картинці (0 зверху). Шум із двох октав value noise робить фронт рваним.
- Стадії від займання: прогрів 5 тіків (білий → підпалений), жар 3 (жовтий → помаранчевий), обвуглювання 7
  (→ `CHARCOAL`), осипання 7 (альфа → 0, масштаб → 0.1). Центроїд обганяє кути на 0.5 тіку лише кольором; альфа
  в клітинці одна, інакше при зникненні проступали б краї трикутників сіткою.
- Сяйво: окремий шар ванільного `RenderTypes.eyes(texture)` (emissive, без lightmap, без запису глибини) поверх тих
  самих клітинок, піднятий на 0.004 до камери. Розмір той самий, що в клітинки: зі збільшенням (було 1.08)
  сусідні трикутники перекривались, і подвоєні краї малювали сітку.
- Частинки (`BurnWave`): бюджет на тік (полум'я 2.5, жарини 2, іскри 0.3, дим від обвуглених клітинок 1, × 
  `burnFlameDensity`, ×0.5 при «Зменшено», лише стовпи диму при «Мінімум»), випадкова точка в клітинці, що горить.
  Ванільні частинки мають фіксований світовий розмір, а площина без Spatial GUI стоїть за 1 блок від камери. Тому
  частинки створюються через `ParticleEngine#createParticle` (повертає частинку, перевірено javap: makeParticle →
  add) і зменшуються `Particle#scale` разом зі швидкістю на `clamp(відстань/4, 0.15, 1)`. `LAVA` пропускається при
  множнику < 0.5, бо її гравітацію не змасштабувати. 6 стовпів `CAMPFIRE_COSY_SMOKE` (×0.6 розміру) у випадкових
  клітинках починають диміти з їхнім займанням.
- Кінець: `smokeDurationTicks` — повний хвіст після згоряння (`smokeEnd = ceil(burnEnd) + smokeDurationTicks`), до
  якого зникає все. Ванільні тривалості життя (javap, конструктори): `CAMPFIRE_COSY_SMOKE` 80–129 тіків (CosyProvider,
  alpha 0.9; згасає лише в останні 60 тіків на 0.015/тік), `FLAME`/`SMALL_FLAME` (RisingParticle) 12–44, `LAVA`
  16–80, `SMOKE` 8–40 (×2.5 для `LARGE_SMOKE`). Тому кожній нашій частинці `setLifetime` обрізається до `smokeEnd`,
  а в останні 20 тіків її життя альфа лінійно йде до 0 (`SingleQuadParticleAccessor`, поле `alpha` protected;
  мінімум із ванільним згасанням). Нові клуби перестають з'являтися за 25 тіків до кінця, інтенсивність падає до
  нуля до цього моменту. Хвиля живе, поки живе хоч одна її частинка, тож у тесті «0 хвиль» = «диму немає».
- Шейдерпаки: ті самі `entity_cutout`/`entity_translucent` + `eyes`; перевірено з Complementary Reimagined.
- Дим зменшено (`burnSmokeDensity`, 0.35 за замовчуванням): `SMOKE` лише з клітинок самого фронту (стадія жару,
  0 ≤ час від займання < 3 тіки), а не з тих, що обвуглюються; базові частоти вдвічі менші (фронт 0.5/тік, стовпи
  0.125/тік на стовп) і множаться на `burnSmokeDensity / 0.35` (×0.5 при «Зменшено»); не більше 24 живих димових
  частинок на хвилю. Обрізання тривалості й згасання до `smokeEnd` не змінилися.

## Профілювання фризу

`debug/ShatterProfiler`, вмикається `"profiling": true`. Логує кожен крок (`trigger`, `setScreen(null) total`,
`capture.*`, `readback.*`, `spawn.*`, `mesh.*`, `render.firstFrame.*`) і для 8 кадрів після закриття час
`GameRenderer.render` та інтервал між кадрами. Інтервал ловить і те, що поза кодом моду (очікування драйвера,
буфер-своп).

## Перевірено / не перевірено

- `./gradlew build` проходить; `runClient` стартує до завантаження ресурсів, усі mixin-и застосовуються
  (`defaultRequire: 1`, тож невдалий таргет упав би одразу).
- `./gradlew runClientGameTest` проходить (див. `src/gametest`): закриття паузи/чату/інвентаря/печі, головне меню,
  екран завантаження, `applyTo`, `disabledScreens`, клавіша, 30× відкрити/закрити (пул текстур обмежений).

## Spatial GUI: розслідування (1.8, звірено з 1.9)

Джерела: jar `spatial-gui-1.8+26.1.2-fabric.jar` і `1.9` з Modrinth (javap) + вихідний код коміту `c91dc9988e`
(«1.8 release», з нього зібрано jar 1.8) і `main` (1.9+). Mod id `spatial_gui` (`provides: spatial-gui`), залежить від
YACL. Ліцензія MIT. Код Spatial GUI не копіювався, лише читався.

### Де площина
- `render/InventoryRenderer#computeScreenPose`: yaw = `renderer.anchoredYaw()` (під час закриття `closeYaw`),
  pitch = `anchoredPitch()` лише в першому режимі (у третьому 0), обрізаний `firstPersonPitchClamp`.
  `RenderUtil.applyScreenTransform` кладе в `WORLD_POSE_STACK` зсув (1-й режим: `look * distance + side + height`;
  3-й: по yaw `distance/side/height`) і поворот `rotateY(-yaw+yawOffset).rotateX(-pitch+pitchOffset)`.
  Параметри — `RenderUtil.getScreenTransformConfig(firstPerson)`: `firstPersonScreen*`/`screen*` із конфігу ×
  множник від FOV (`autoScaleByFov`). Масштаб: `AnimationUtil.scaleForElapsed(...)` (анімація відкриття від
  `animationStartScalePercent`), аспект = ширина/висота їхньої текстури.
- Результат: `RenderUtil.QuadBasis(centerOffset, right, up, normal, halfWidth, halfHeight)` через публічний
  `InventoryRenderer#getQuadBasis()`. `centerOffset` — **відносно позиції камери, в осях світу** (так його
  використовує `getInventoryMousePositionRay`: промінь із (0,0,0) у світових осях). `halfWidth = aspect*0.5*scale`,
  `halfHeight = 0.5*scale`. 1.9 додає `SpatialGUIClient.getScreenAnchorDelta()` у ту саму матрицю — сенс не змінився.
- Квад: (-hw,-hh) → uv (0,0), (hw,hh) → uv (1,1), тобто v=0 внизу, як у render target (під VulkanMod v
  перевернуто).
- Текстура: `TextureTargetManager#getInventoryTarget()` — `TextureTarget` розміром вікно × `renderScalePercent`,
  екран намальований з повною розкладкою (`screen.width/height` у їхньому GUI scale), прозоре тло. Тож межі панелі
  контейнера в текстурі = `leftPos/width … (leftPos+imageWidth)/width`.

### Прив'язка до камери
- `mixin/render/LevelRendererMixin` (TAIL `renderLevel`): pose stack = поворот камери, площина малюється відносно
  камери. У 3-му режимі `CameraMixin#alignWithEntity` переносить камеру в точку від гравця (`cameraDistance`,
  `cameraSideOffset`, `cameraHeightOffset`) і тримає її, поки екран відкритий; у 1-му камера в очах.

### Закриття і що змушує площину злітати
- `SpatialGUIRenderer#hookScreen` реєструє Fabric `ScreenEvents.remove(screen)` → `onScreenRemoved(true)` →
  `InventoryRenderer#beginClosing()` (якщо `enableCloseAnimation`): `closing=true`,
  `closeStartElapsed = min(animTotal, now-openTime)`, `closeDurationMs = max(анімація, перехід камери в 3-му режимі)`.
  Це відбувається всередині `Minecraft#setScreen` (з `Screen#removed`), тобто ДО нашого RETURN.
- Під час закриття `getAnimationElapsedMs() = max(0, closeStartElapsed - (now-closeStart))` рахує назад → масштаб і
  прозорість відкриття грають у зворотний бік; `canDraw()` = `elapsed > 0`.
- «Злітає»: у 3-му режимі `CameraMixin` (гілка `renderer.isClosing() && !isClosingFirstPerson()`) лерпить камеру
  назад до гравця за `getCloseBlend()`, а площина малюється відносно камери — тож летить разом із камерою.
- Кінець: `SpatialGUIRenderer#updateFrame` → `isCloseFinished()` → `finishClosing()` → `clearTarget()`.

### Curved screen
`curvedScreenEnabled` (типово false), лише в 1-му режимі, `curvedScreenArcDegrees` 5–150 (типово 30),
`RenderUtil.CylinderBasis`, 32 сегменти, вигин по горизонталі. **Відкладено**: для вигнутого екрана уламки
кладуться на дотичну площину (`QuadBasis`) того ж розміру — по краях буде видно невеликий стрибок.

### API
У 1.8 публічного API немає. `api/SpatialGUIAPI` з'явився в 1.9 (коміт `9679ee4`), але дає лише стан
(`isActive`, `isClosingScreen` …) — без геометрії, текстури й способу скасувати анімацію. Отже потрібен прямий доступ.

### План доступу (переживає дрібні оновлення)
- Публічні методи, однакові в 1.8 і 1.9 (перевірено javap): `SpatialGUIClient.renderer()`,
  `SpatialGUIRenderer#getHookedScreen/getInventoryRenderer/getTargetManager`, `InventoryRenderer#getQuadBasis/
  getCylinderBasis/isClosing/isClosingFirstPerson`, `TextureTargetManager#getInventoryTarget`, record-аксесори
  `QuadBasis`. Викликаються напряму (компіляція проти jar 1.8, `compileOnly`), лише з класу, який вантажиться тільки
  коли мод є.
- Приватні поля `InventoryRenderer.closeStartElapsed/closeDurationMs` (однакові в 1.8/1.9) — `@Accessor`-mixin в
  окремому конфігу `shatter_gui.spatial.mixins.json` з `IMixinConfigPlugin`, що вмикає його лише коли `spatial_gui`
  встановлений і версія `>=1.8 <2`; конфіг `required: false`, тож невдалий mixin не валить гру.
- Усе в `try/catch (Throwable)`: перша помилка → одне попередження в лозі, інтеграція вимикається до кінця сесії,
  мод працює як без Spatial GUI.

### Як забрати площину й погасити анімацію
- HEAD `setScreen`: якщо `renderer.getHookedScreen() == старий екран` → Spatial GUI реально показує цей екран.
- RETURN `setScreen` (наш тригер): `getQuadBasis()` + позиція камери з останнього кадру → світові координати
  площини; GPU-копія `getInventoryTarget()` у наш слот пулу (до того, як `finishClosing` її очистить).
- Скасування лише для цього екрана: `closeStartElapsed = 0` → `canDraw()` false → площина зникає з першого кадру.
  `closing` лишається true, тож у 3-му режимі камера все одно плавно повертається (і стан Spatial GUI прибирає
  його власний `finishClosing`). У 1-му режимі переходу камери немає → `closeDurationMs = 0`, закриття завершується
  на наступному кадрі. Відкриття й інші режими не чіпаються.

## Spatial GUI: реалізація

Пакет `compat/spatial`:
- `SpatialGuiDetector` — лише Fabric Loader: встановлено `spatial_gui`/`spatial-gui` версії `>=1.8 <2`? Інакше одне
  попередження («знайдено X, підтримується …»).
- `SpatialGuiMixinPlugin` + `shatter_gui.spatial.mixins.json` (`required: false`) — `InventoryRendererAccessor`
  (`@Mixin(targets = "...InventoryRenderer")`, сеттери `closeStartElapsed/closeDurationMs`) застосовується лише тоді.
- `SpatialGuiCompat` — фасад без імпортів Spatial GUI; кожен виклик у `try/catch (Throwable)`; перша помилка → одне
  попередження, інтеграція вимикається до кінця сесії, далі звичайна (пласка) поведінка. Вимкнення:
  `"spatialGuiIntegration": false`.
- `SpatialGuiBridge` — єдиний клас з імпортами Spatial GUI.
- `SpatialPlane` — площина Spatial GUI як `ShardPlane` (уламки й без Spatial GUI тепер будуються через `ShardPlane`:
  `CameraPlane` для пласкої картинки, межі GUI — частки зображення `ImageRect`).

Порядок:
1. HEAD `Minecraft#setScreen(null)`: `isShowing(старий екран)` (до того, як Spatial GUI його відчепить).
2. RETURN: якщо екран треба розбити і Spatial GUI його показував — `queue` (лише CPU: межі панелі, ефект); своя
   пласка зйомка не запускається.
3. Наступний кадр, після рендеру світу (наш хук перед `GuiRenderer#render`): `complete` — Spatial GUI щойно намалював
   свою площину цього кадру; читаємо `QuadBasis` + камеру цього ж кадру, GPU-копія `getInventoryTarget()` у слот
   пулу, скасування анімації, спавн уламків. З наступного кадру — уламки замість площини.
   Копія в тіку (а не в кадрі) коштувала ~12 мс (`glBlitFramebuffer` + `glGetError` чекали GPU); у кадрі — 0.15–0.3 мс.

Пастка, знайдена тестом: `getQuadBasis()` перераховує площину з `SpatialGUIClient.getEffectiveFirstPersonMode()`,
а їхня камера скидає його в false, щойно екран закрито — у 1-му режимі виходила площина 3-го режиму (не там і
вдвічі більша). Тому на час читання ставимо режим закриття (`setEffectiveFirstPersonMode`), `updateQuadBasis()`,
і повертаємо як було (обидва методи публічні й однакові в 1.8/1.9).

Рядки профілю: `spatial.showingThisScreen`, `spatial.queue (tick)`, `spatial.readPlane (frame +0, режим, flat/curved,
центр, розмір)`, `spatial.copyTexture`, `spatial.cancelCloseAnimation`, `spatial.complete total`.

Обмеження / відкладено:
- Curved screen: уламки на дотичній пласкій площині того ж розміру (у профілі `curved -> flat fallback`).
- Тінь Spatial GUI (`dropShadowEnabled`, типово ввімкнена) зникає разом із площиною одразу; уламки без тіні.
- Закриття під час fade-in відкриття: площина ще напівпрозора, уламки одразу непрозорі (легкий стрибок прозорості).
  Так само не переносяться `screenAlpha` і `enableWorldLightingTint` Spatial GUI (конфіг між 1.8/1.9 переїхав).
- VulkanMod: v інвертується як у Spatial GUI, але не перевірено.
- Dev-запуск зі Spatial GUI: YACL 3.9.7 падає з MixinExtras 0.5.4 (Loader 0.19.3) — пише `@Redirect(at = [..])`
  масивом. У `build.gradle` для dev взято YACL 3.9.6. До нашого мода не стосується.

Перевірено автотестом (`runClientGameTest -PspatialGui=1.8` і `=1.9`, скріншоти в
`build/run/clientGameTest/screenshots`): 3-й режим (інвентар, піч), 1-й режим, закриття під час анімації відкриття
(обидва режими), curved, пауза (Spatial GUI її не показує → звичайний ефект), `spatialGuiIntegration=false` (Spatial GUI
грає свою анімацію), кадри «до/після» із замороженою фізикою — уламки точно на місці площини. Без Spatial GUI —
та сама поведінка, що й до змін, mixin-и для Spatial GUI не застосовуються, попереджень немає.

## Політ без джерела, dissolve, загальні налаштування

Політ до краю / від краю (екрани без джерела):
- `fly/FlyTarget` — ціль польоту: точка контейнера або напрямок до краю (`edgeX, edgeY` ∈ {−1, 0, 1}, y униз;
  `random` — 4 краї + 4 кути). Іде замість `Vec3` через `ScreenCapture`/`SpatialGuiBridge` → `ShardSource.target`.
  Відкриття й закриття вибирають напрямок окремо (`FlyController` і `MinecraftMixin`).
- Закриття: джерело є й живе → точка; джерела не було (`FlyController.hadSource() == false`) → край; джерело було,
  але зникло → `glass`, як і раніше.
- Відкриття (`FlyOpen`): плаский GUI — зсув на один розмір екрана за край × (1 − e), поворот 0.25 рад × (1 − e) (без
  нього з `reduceMotion`), масштаб 0.85 → 1, ease-out. Spatial GUI — зсув на дві півширини/піввисоти видимої області
  на відстані площини (`|centre| · tan(fov/2)`, аспект основного таргету) вздовж `-left`/`-up` камери, поворот навколо
  `forward`, проявлення як для контейнера. Прозорість плаского GUI не регулюється (у `GuiGraphicsExtractor` немає
  загальної альфи) — він влітає з-за краю.
- Закриття (`FlyEffect`, `effectData = 1`): центр летить до `plane.toWorld(0.5 + 1.2·edgeX, 0.5 + 1.2·edgeY)` (зображення
  = увесь екран), ease-in, масштаб 1 → 0.6, альфа 1 → 0 (smoothstep 0.35..1), без пуху.
- Знахідка тесту: «картинка в картинці» (`PictureInPictureRenderState`, модель гравця в інвентарі) має фіксовані
  `x0..y1` і `pose()` за замовчуванням тотожний, тож під час 2D-польоту стояла б на місці. Обгорнути не можна (рендерер
  обирається за класом стану) — `GuiRenderStateMixin` не додає її, поки летить плаский GUI.

`dissolve` (`effect/DissolveEffect`):
- `ShardMesh.buildSquares` — правильна сітка без джитера, квадрат = 2 трикутники поруч у списку; розмір клітинки 8
  GUI-пікселів (частка меж × `getGuiScaledWidth/Height`), не більше 2500 квадратів (інакше квадрати більші).
- Час відриву квадрата (у `effectData` обох трикутників): відстань від початку хвилі (край — лінійно, кут/центр — радіально,
  0..1) × D × 0.7 + fbm × D × 0.3 + ±0.8 тіка, D = `dissolveDurationTicks` × масштаб тривалості. Стадії як у burn:
  ціла → світиться (±2 тіки від відриву, шар `eyes`, (205, 230, 255), до 0.55) → відірвана (12 тіків: вгору 0.25 від
  розміру GUI, убік ±0.12, турбулентність sin/cos 0.02, масштаб 1 → 0.15, альфа 1 → 0 з 45%) → зникла.
- Обидва трикутники тримають зсув від центру квадрата у `vel`, `DissolveWave` ставить їх навколо центру, що дрейфує, ×
  масштаб, тож квадрат зменшується цілим. `tint.scale` — той самий масштаб у часі рендера (`age − 1 + partialTick`).
  Не враховується в `maxShards`. Профіль: `dissolve.squares (23x21 (966 triangles), wave 20 ticks from …)`, спавн ≈ 1–4 мс.

Загальні:
- `reduceMotion`: `durationScale()` 0.65 (життя уламків glass, фронт і дим burn, політ, dissolve), без мерехтіння
  жару, glass — розліт ×0.5 і обертання ×0.3, без повороту при вильоті, dissolve — менший дрейф і без турбулентності,
  частинки ×0.5 (`particleScale()`).
- `particleDensity`: множник частинок burn (полум'я, жарини, іскри, дим) і пуху fly.
- Спам: `ShardManager.hurryOldWaves` перед кожною новою хвилею — якщо хвиль > 4 або уламків > 8000, найстаріші
  `hurry()`: зникають за 6 тіків (`Batch.fade` множить альфу всього, що малюється), `ShatterWave.hurry` — burn
  перестає створювати частинки, обрізає живим тривалість до кінця і гасить їх за ці 6 тіків. Рядок профілю
  `spam.hurried`. Тест: 12 закриттів burn поспіль → не більше 3 хвиль, усе зникло після звичайного часу.
- Старі конфіги: Gson заповнює відсутні поля значеннями за замовчуванням (конструктор без аргументів), `save()` їх
  дописує; невідомі `edgeDirection`/`dissolveOrigin` → `random` з попередженням.

## Область ефекту (обрізаний інтерфейс): розслідування

Симптом: у креативному інвентарі горить/розбивається лише середина; вкладки, смуга прокрутки, стрілки, книга рецептів
лишаються поза ефектом.
- Звідки область: `ScreenCapture.guiBounds(screen)` → `ImageRect` у частках зображення. Для `AbstractContainerScreen`
  це `leftPos/topPos/imageWidth/imageHeight` (через `AbstractContainerScreenAccessor`), для інших екранів — увесь кадр.
  `opaqueBounds` знімка не існує: після переходу на GPU-копію (без readback) знімок на CPU не читається.
- Чи весь вміст у знімку: так. `extractPendingScreen` викликає `extractRenderStateWithTooltipAndSubtitles`, тобто фон
  (без затемнення/blur, їх прибирає `ScreenMixin`), `extractRenderState` і відкладені елементи — у текстурі є все:
  вкладки креативу (`CreativeModeInventoryScreen` малює їх вище й нижче `topPos/imageHeight`), смуга прокрутки й
  стрілки, книга рецептів (`RecipeBookComponent`, ліворуч від панелі; з відкритою книгою `leftPos` зсувається, але
  `imageWidth` книгу не включає), панелі інших модів.
- glass і burn беруть однакову область: обидва `ShardMesh.build(source…)` з `ShardSource.bounds`; у режимі Spatial GUI
  `SpatialGuiBridge.queue` бере ту саму `guiBounds`. Отже причина одна для всіх ефектів: область = контейнер, а вміст
  ширший.
- Чим виміряти без readback: `GuiRenderState` після витягання екрана має всі елементи з межами (javap):
  `forEachElement(…, TraverseRange.ALL)` (`GuiElementRenderState extends ScreenArea`, `bounds()` — `ScreenRectangle`
  у GUI-координатах після pose і scissor, null якщо обрізано повністю), `forEachItem`, `forEachText`,
  `forEachPictureInPicture` (усі `ScreenArea`). Об'єднання = реальні межі непрозорого вмісту.

Виправлення: `ScreenCapture.contentBounds` — об'єднання меж усіх елементів знімка + 2 GUI-пікселі запасу, у частках
`screen.width/height`; якщо нічого не знайдено — стара `guiBounds`. Плаский режим рахує його зі стану, який і так
витягується для знімка; Spatial GUI — окремим витяганням того ж екрана в тимчасовий `GuiRenderState` (лише CPU,
текстура в них намальована з тією ж розкладкою, тож частки збігаються). Рядок профілю
`capture.contentBounds (…% x …%, panel …% x …%)`.

## Політ у контейнер (open «fly_from_block», close «fly»)

Пакет `fly`:
- `FlySource` — блок (позиція + блок; точка: центр, для `AbstractChestBlock` висота 0.8 під кришкою, для подвійної
  скрині +0.5 у бік `ChestBlock.getConnectedDirection`) або сутність (центр хітбоксу зараз). `isValid()`: той самий
  блок на місці / сутність жива, не прибрана, той самий рівень.
- `FlyController` — Fabric `UseBlockCallback`/`UseEntityCallback` (обидва викликаються на клієнті: javap
  `fabric-events-interaction-v0` 5.2.3, `MultiPlayerGameModeMixin#interactBlock`, `MinecraftMixin#injectUseEntityCallback`;
  з інтегрованим сервером вони ж ідуть і на серверному потоці — беремо лише `level.isClientSide()`), повертають
  `PASS`. Екран, відкритий над світом протягом 1000 мс після використання, отримує джерело (крім `InventoryScreen` і
  креативного інвентаря). Джерело живе, поки екран не закрито у світ.
- `FlyOpen` — стан вильоту: час = ігровий (лічильник тіків + partial tick), старт на першому кадрі, ease-out
  (квадратичний), `flyDurationTicks` (6). Плаский GUI: точка проєктується камерою (`forward/up/left`, `getFov()`,
  аспект основного таргету; позаду камери чи далеко поза екраном — на краю, |NDC| ≤ 1.5), поза
  `Matrix3x2fStack`: translate(lerp(P, C, e))·scale(lerp(0.05, 1, e))·translate(−C), C — центр панелі контейнера
  (або екрана). `ScreenMixin`: push на HEAD `extractRenderStateWithTooltipAndSubtitles`, pop на RETURN; у фонових
  методах (`extractBlurredBackground/TransparentBackground/MenuBackground`) push identity, тож затемнення не летить;
  під час вильоту `mouseX/Y` → далеко за екраном (нічого не підсвічено, без підказок).
- Введення: Fabric `ScreenMouseEvents.allowMouseClick/Release/Drag/Scroll` (реєструються в `ScreenEvents.AFTER_INIT`)
  повертають false, поки `FlyOpen.isAnimating(screen)`. Вибрано найпростіше — ігнорувати мишу ~0.3 с, а не
  перераховувати координати (у Spatial GUI вони йдуть через його промінь). Клавіатура не блокується.
- Spatial GUI: на RETURN `setScreen` екран уже прив'язаний (їхній `BEFORE_INIT`), `SpatialGuiBridge.cancelOpenAnimation`
  → `setScreenOpenTime(now − 60 с)`; `compat/spatial/mixin/InventoryRendererFlyMixin` (RETURN `computeScreenPose`:
  P' = M·P, draw = draw·(P⁻¹·M·P); RETURN `getFadeAlpha`: × min(1, 2·progress)). Камеру Spatial GUI (перехід у 3-й
  режим) не чіпаємо; M рахується від поточної позиції камери щокадру, тож площина стартує точно в контейнері.
- Закриття: `MinecraftMixin` на RETURN `setScreen(null)` спершу `FlyController.onScreenClosed()` (зупиняє виліт,
  щоб захоплення не вийшло трансформованим; повертає джерело, якщо воно ще існує). Ефект `fly` без джерела → `glass`
  (рядок профілю `fly.fallback to glass: <причина>`). Ціль іде через `ScreenCapture`/`SpatialGuiBridge` →
  `ShardManager.spawn(..., target)` → `ShardSource.target`.
- `effect/FlyEffect`: 12 клітинок (≈ 24 трикутники) рухаються як одна площина: зсув від центру — у `vel` (для
  статичного ефекту не використовується), `FlyWave` щотіку ставить pos = lerp(C0, T, e) + vel·s, e = t² (ease-in),
  s = lerp(1, 0.03, e); `tint.scale` = той самий s у часі рендера (`age − 1 + partialTick`, бо рендер інтерполює
  prevPos→pos). Життя = duration + 1, у кінці 6 `POOF` (×0.5 і менше біля камери). Unlit (як GUI), з шейдерами — ті
  самі `entity_cutout/translucent`.
- Профіль: `open #n of this session: screen=… effect=… source=…`, `open #n open.trigger (Spatial GUI plane… / flat GUI,
  N ticks): ms`, `open.skipped`, `close #n fly.target`, `fly.flight (відстань, тіки)`, `fly.fallback to glass`.
  Тригер відкриття 0.2–2 мс, перший кадр площини 0.1–0.3 мс.

Автотест `FlyScenario` (справжня скриня й вагонетка зі скринею, відкриття клавішею use): без джерела (інвентар) —
без вильоту; виліт стартує, на t1 ще летить (миша заблокована), на t8 приземлився; закриття — `fly`, після польоту
0 хвиль; скриню прибрано, поки відкрита — `glass`. Пройдено без Spatial GUI, зі Spatial GUI 1.8 (3-й режим) і з
Complementary Reimagined. У gametest кожен крок «скріншот + запит» займає ~2 тіки, тож скріншоти — через ~2 тіки.
Обмеження: зі Spatial GUI перший кадр вильоту може бути без площини (їхній таргет ще готується); віслюк відкривається
Shift+ПКМ — працює як сутність, але не перевірено тестом.

Розслідування (javap, Spatial GUI 1.8 і 1.9, MC 26.1.2):
- Spatial GUI чіпляє екран у Fabric `ScreenEvents.BEFORE_INIT` → `SpatialGUIRenderer#hookScreen` →
  `InventoryRenderer#setScreenOpenTime(currentTimeMillis)`. Отже на RETURN `Minecraft#setScreen(new)` екран уже
  прив'язаний; `setScreenOpenTime(now - 1_000_000)` скасовує їхню анімацію відкриття (масштаб і fade рахуються від
  `getAnimationElapsedMs() = now - screenOpenTime`).
- `InventoryRenderer#computeScreenPose(PoseStack drawStack, boolean)` (private, однаковий в 1.8/1.9, 1.9 додає
  `getScreenAnchorDelta` перед трансформом) застосовує трансформ+масштаб і до `drawStack` (= поворот камери R · P), і
  до `private static WORLD_POSE_STACK` (= P, відносно камери в осях світу), з якого `updateScreenBases` будує
  `QuadBasis`. План: inject на RETURN, M = translate(lerp(T, C, e))·scale(s)·translate(−C), де C = переклад P,
  T = контейнер − камера; WORLD: P' = M·P; draw: D' = D·(P⁻¹·M·P).
- `getFadeAlpha()` (private) → `COLOR_MODULATOR = (a,a,a,a)`; множити результат на свою альфу (RETURN).
- Ванільний `Screen#extractRenderStateWithTooltipAndSubtitles`: nextStratum → `extractBackground` → nextStratum →
  `extractRenderState` → `extractDeferredElements`. `GuiGraphicsExtractor#pose()` — `Matrix3x2fStack`. Для 2D:
  push+трансформ на HEAD, pop на RETURN; у `extractBlurredBackground/extractTransparentBackground/extractMenuBackground`
  push identity, щоб затемнення не масштабувалось.
- Fabric API у кеші: `fabric-screen-api-v1 5.1.0`, `fabric-events-interaction-v0 5.2.3` — ще не перевірено
  `ScreenMouseEvents.allowMouseClick` і клієнтський виклик `UseBlockCallback/UseEntityCallback`.

## Шейдери (Iris): розслідування

Звичайний клієнт: Iris `1.11.4+mc26.1.2`, Sodium `0.9.2+mc26.1.2`, пак `Bliss_v2.1.2_(Chocapic13_Shaders_edit).zip`.
У логах сеансів з увімкненими шейдерами:
`[ERROR] (Iris) Missing program shatter_gui:pipeline/shard in override list ...` (з `GlDevice ... iris$redirectIrisProgram`).

Відтворено автотестом (`ShaderScenarios`, Iris + Sodium + Spatial GUI 1.8, той самий рядок ERROR у лозі):
- Bliss v2.1.2: після закриття уламки майже повністю прозорі — ледь помітні клини кольору неба (t1, t10).
- Complementary Reimagined r5.8.1: на t10 уламків не видно взагалі (на t1 — лише залишок площини з TAA).
- Iris встановлений, шейдери вимкнені: усе нормально (наш пайплайн).

Як Iris вибирає програму (javap `net.irisshaders.iris.pipeline.IrisPipelines`): статична мапа `coreShaderMap`
`RenderPipeline → ShaderKey` (= програма паку `gbuffers_*`), окрема `coreShaderMapShadow` для shadow pass.
`getPipeline` для невідомого пайплайна повертає `FAKE_FUNCTION` → ERROR вище і запасна програма.

Гіпотези:
- (а) **Підтверджено** (лог + код): наш `shatter_gui:pipeline/shard` не в `coreShaderMap`.
- (б) Фаза: ми малюємо через Fabric `LevelRenderEvents.COLLECT_SUBMITS` → `submitCustomGeometry`, тобто разом з
  ентіті в основному проході, до composite — як ванільні ентіті; сама по собі фаза не причина (з ванільними типами
  уламки видно). Але напівпрозорі типи не пишуть глибину, і Bliss композитить хмари поверх них на тлі неба (так само
  виглядає ванільний напівпрозорий шар скіну на голові гравця) — тому для непрозорих уламків взято cutout, див. нижче.
- (в) Не причина: формат `NEW_ENTITY` має UV2 (ми пишемо 0xF000F0) і нормалі.
- (г) Частково: кольори вершин премультипліковані і blend наш `ONE, ONE_MINUS_SRC_ALPHA`; пак змішує з `SRC_ALPHA`, тож
  під час фейду альфа множилась би двічі, а наше CPU-затінення — поверх освітлення паку.

Публічний API Iris (`net.irisshaders.iris.api.v0.IrisApi`): `isShaderPackInUse()`, `isRenderingShadowPass()`,
`assignPipeline(RenderPipeline, IrisProgram)` (→ `ShaderKey.findBestMatch(pipeline, ProgramId.fromAPI(program))` →
`IrisPipelines.assignPipeline`), `assignPipelineShadow(RenderPipeline, IrisShadowProgram)`,
`registerShadowRenderCallback(...)`. `IrisProgram` має `ENTITIES`, `ENTITIES_TRANSLUCENT`, `EMISSIVE_ENTITIES` тощо.
Пропуск shadow pass — `isRenderingShadowPass()`.

## Шейдери (Iris): виправлення

Варіант 1 (ванільні типи), `assignPipeline` не знадобився:
- `render/ShardRenderer`: якщо `IrisCompat.shaderPackInUse()` — уламки йдуть ванільними типами, які Iris уже знає:
  повністю непрозорі (`alpha == 1`) — `RenderTypes.entityCutout(текстура)` (→ `gbuffers_entities`: освітлення паку,
  пишеться глибина, хмари Bliss не лягають поверх); ті, що плавно зникають, — `RenderTypes.entityTranslucent(текстура)`.
  Кольори вершин без премультиплікації і без CPU-затінення (освітлює пак), UV2 = 0xF000F0, нормаль до камери.
- Під час shadow pass (`isRenderingShadowPass()`) уламки не малюються.
- Без шейдерпака (і без Iris) — як раніше, наш пайплайн; вигляд не змінився.
- `compat/iris/IrisCompat` (без імпортів Iris, перевірка `FabricLoader.isModLoaded("iris")`, `try/catch (Throwable)`,
  одне попередження і вимкнення на сесію) + `IrisBridge` (єдиний клас з імпортом `IrisApi`). Iris — `compileOnly`.
  Конфіг `"shaderCompat": true`.

Перевірено автотестом (`runClientGameTest -Pshaders -PshaderPack=<zip> [-PspatialGui=1.8]`, скріншоти в
`build/run/clientGameTest/screenshots/shader_*`): **Bliss v2.1.2**, **Complementary Reimagined r5.8.1**, **BSL v10.1.3** —
glass (t1…t52, зокрема фейд), burn (жар, обвуглювання, частинки), Spatial GUI 3-й режим; ERROR «Missing program» зник.
Iris без шейдерів і без Iris — як до змін (повний набір тестів проходить).

Обмеження:
- Під шейдерами уламки освітлює пак (захід сонця, ніч, туман) — вони не «неосвітлені», як площина Spatial GUI чи GUI;
  зворотні боки темніші. Фейд (останні ~25% життя) іде через translucent, і в Bliss хмари можуть лягати поверх
  напівпрозорих уламків на тлі неба.
- Артефакт «небо замість голови гравця» у Bliss зі Spatial GUI в 3-му режимі є і до закриття — це ванільний
  напівпрозорий шар скіну, не наш мод.

Автотест з шейдерами: тека запуску тесту очищається щоразу, тому тест сам копіює пак (`-PshaderPack`) у
`Iris.getShaderpacksDirectory()` і вмикає його (`Iris.getIrisConfig()`, `Iris.reload()` — внутрішнє API, лише в тесті).
Скріншот фреймворку gametest рендерить зайвий кадр, на якому Sodium падає («Global terrain uniforms have not been
updated»), тож з Iris знімки робить ванільний `Screenshot.grab` з останнього кадру.

## Політ лише для fly: місця «просочування» і модифікатор краю

Знайдені місця, де політ із-за краю потрапляв у ефекти, які його не вибирали:
1. `FlyController.onScreenOpened` — головне. Ефект відкриття за замовчуванням `fly_from_block` для екрана без джерела
   завжди вмикав виліт із-за краю, хоч би який був ефект закриття (glass, burn, dissolve). Тепер виліт із краю лише
   тоді, коли ефект закриття цього екрана (з урахуванням `overrides`) має основу `fly` і `EdgeTransition.allows(screen)`;
   інакше екран просто відкривається (`edgeFlightAllowed`).
2. `FlyEffect.createWave` — без цілі (`target == null`) уламки летіли до верхнього краю «за замовчуванням». Тепер
   `IN_PLACE`: згасають на місці.
3. Чат, таблички, книги (читання/редагування/підпис), командний блок, jigsaw і structure block отримували виліт і
   відліт через край. Тепер `EdgeTransition.allows` їх виключає; з `fly` вони згасають на місці.

`effect/modifier/EdgeTransition` — запис `(dx, dy, roll)`: вибір сторони (`pick`), вхід плаского GUI
(`applyFlatEntry`), вхід площини Spatial (`worldEntry`), точка/масштаб/альфа виходу. Не налаштовується окремо, лише
через `fly/edgeDirection`. `FlyTarget` тепер `(Vec3 point, EdgeTransition edge)`: точка контейнера або край. У
`MinecraftMixin` точка джерела передається будь-якому ефекту (потрібно вихору з `targetPoint = source`), а край —
тільки ефектам з основою `fly` (`effect.base()`, тож і власним на основі fly).

## Головний вимикач

`enabled == false` перевіряється першим рядком у `MinecraftMixin` (HEAD `setScreen`), `FlyController` (use-колбеки,
`AFTER_INIT`, `onScreenOpened`), `SpatialGuiCompat.active()` (тож Spatial грає свої анімації). `ShatterKeys.setEnabled(false)`:
зберігає, зупиняє `FlyOpen`, скасовує відкладені захоплення (`ScreenCapture.cancelPending`,
`SpatialGuiCompat.cancelPending`) і `ShardManager.clear()` (хвилі згасають через `hurry`). `ShardManager.tick` при
вимкненому моді відкидає всі хвилі, крім перегляду редактора. Клавіші «вимкнути» й «налаштування» не призначені
(`InputConstants.UNKNOWN`).

## Реєстр параметрів

- `params/Param` (sealed: `Float`, `Int`, `Bool`, `Choice`): група, id, межі, типове значення, ключ перекладу
  `param.shatter_gui.<група>.<id>`, `sanitize` (обрізання до меж, нечислове → типове, невідомий вибір → типовий).
- `params/Params` — усі параметри по групах (`general` + id ефектів). Типові значення — ті, що були в моді.
- `ParamValues` — незмінний знімок значень + пресет + `reduceMotion`; ефекти читають лише його (`durationScale`,
  `particleScale`, `shardScale`, `volume`, `ticks`). `with(map)` — накладання (власні ефекти).
- `ShatterConfig` реалізує `ParamStore`: у JSON лише відмінні від типових значення в `general.params` і
  `effects.<id>.params`; `LEGACY_KEYS` переносить старі плоскі ключі. Звуки: `EffectSounds.play` (UI-звук, гучність ×
  `general/soundVolume`); у кожного ефекту свій `soundVolume`.
- `Preset`: множники частинок, тривалості й кількості уламків поверх параметрів.

## Редактор налаштувань (власні віджети)

Бібліотека `net.shattergui.ui` — без залежностей і текстур, усе через `GuiGraphicsExtractor`:
- `Theme` — єдиний файл стилів (кольори, розміри, швидкості анімацій), акцент із `Theme.Accent` (`uiAccent` у конфігу).
- `Draw` — заокруглені прямокутники рядками `fill` (кут — чверть кола, `inset(r, i)`), градієнти, тінь (2 шари),
  рамка як дві вкладені заливки (менше елементів, ніж обвідка по пікселях), піксельні іконки `Icon` (шаблони 9×9 у
  коді), і **трикутники**: власний `GuiElementRenderState` (`Draw.Triangles`), доданий через аксесор
  `GuiGraphicsExtractorAccessor.shatter$renderState()` → `GuiRenderState.addGuiElement`. Конвеєри GUI малюють QUADS і
  відсікають задні грані (`RenderPipeline` cull за замовчуванням true), тому трикутник — квад із подвоєною останньою
  вершиною, а порядок вершин вирівнюється під ванільний blit (від'ємний векторний добуток). Обрізання — через
  `Ui.pushClip/popClip`, які ведуть власний стек (приватний `ScissorStack` недоступний).
- Шари: `GuiRenderState` сам кладе елемент вище, якщо він перетинається з попередніми, тож порядок малювання
  зберігається; випадаючі списки, діалоги й підказки — у наступних стратах (`nextStratum`).
- `UiClock` — час кадру (dt ≤ 0.1 с), `Anim` — експоненційне наближення за dt: анімації від часу кадру, не від тіків.
- `Widget`/`Container`/`Column`/`Row`/`Grid` — розкладка зверху вниз щокадру; `UiScreen` — хост: hit-test (оверлеї
  зверху, модальні блокують), захоплення миші при натисканні, фокус (Tab/Shift+Tab, стрілки), підказка після 0.45 с,
  мінімум 400×230 GUI-точок (менше — повідомлення). Віджети: `Button`, `Toggle`, `Slider` (з полем точного значення),
  `Dropdown` (+ `Popup` оверлей), `TextField` (виділення, буфер обміну), `TabNav` (анімований індикатор),
  `ScrollView` (інерція: швидкість від колеса згасає як `SCROLL_FRICTION^dt`), `Card`, `ConfirmDialog`, `Swatch`, `Label`.
- Події 26.1: `mouseClicked(MouseButtonEvent, boolean)`, `mouseReleased/Dragged`, `mouseScrolled(x, y, h, v)`,
  `keyPressed(KeyEvent)`, `charTyped(CharacterEvent)`; `shouldCloseOnEsc` false — Esc обробляє `UiScreen`.

Живий перегляд — `ui.preview`: `SampleImage` (скриня 176×166 у `NativeImage`, `DynamicTexture`, `setPixel` — ARGB),
`Sim` + `GlassSim`/`BurnSim`/`DissolveSim`/`FlySim`/`VortexSim`/`ExtrasSim` — 2D-відтворення з тими самими
параметрами й формулами, що й справжні ефекти (скло — з перспективою: глибина росте з `burst·0.5` за тік, екранне
зміщення й розмір діляться на неї). `PreviewPlayer` — фази відкриття → показ → закриття → пауза, перезапуск за
`Scene.signature()` (усі значення реєстру в тексті), один крок на кадр навіть якщо мініатюра показана двічі.
Мініатюри — ті самі програвачі в `Thumb` і в пунктах списків.

`config/screen/ShatterConfigScreen` (на `UiScreen`): копія конфігу, Save/Cancel (з діалогом при змінах), вкладки
Загальне/Ефекти/Екрани/Власні, `ParamUi` — одиниці (тіки → секунди тощо, ввід у тих самих одиницях), `SettingRow`.
`ScreenCatalog` (`config/shatter_gui/screens.json`) запам'ятовує для кожного побаченого екрана заголовок першої зустрічі,
мод (простір імен меню або мод, з jar якого завантажено клас — `ModOrigin.getPaths()`) і клас; `ScreenIcons` — предмет
для ванільних меню й екранів, іконка мода з `ModMetadata.getIconPath(32)`, інакше загальна. «У світі» — старий
`PreviewRunner`. Редактор не перехоплюється Spatial GUI (звичайний `Screen`).
## Власні ефекти

- `custom/CustomEffectDef` — дані й перевірка: схема 1, id `[a-z0-9_]{1,32}`, основа з `BASES`, параметри через
  `Params.find(основа, id)` + `sanitize`; частинки — лише зареєстровані `SimpleParticleType` (`PARTICLE_TYPE.getOptional`),
  звуки — `SOUND_EVENT.containsKey`; невідомі пропускаються з попередженням, решта помилок робить файл недійсним
  (`InvalidEffectException`, один рядок у лозі). Параметри — накладання на налаштування основи (зберігаються навіть
  рівні типовим). Код експорту: gzip(JSON) → base64; імпорт обмежений 16 384 символами коду і 64 КБ JSON.
- `custom/CustomEffect` — обгортка основи: `prepare` накладає параметри, `base()` повертає основу (тому край/джерело
  працюють і для власних), `Extras`-хвиля додає частинки (start/during/end, ≤12 за тік, ≤300 на ефект, ≤40 у сплеску) і
  звуки кінця; звуки початку — у `playSound`.
- `custom/CustomEffects` — завантаження: тека конфігу + ресурспаки (`listResources("shatter_gui/effects")`), конфіг
  переважає; `ResourceLoader.get(CLIENT_RESOURCES).registerReloadListener(...)` → F3+T. Приклад пишеться лише коли теки
  ще немає.
- `ShatterEffects` — вбудовані + власні; `ids()` (порядок для клавіші K і всіх списків), `displayName` (власні —
  буквально їхня назва). Невідомий id у конфігу → `glass` з одноразовим попередженням.

## Вихор (`effect/VortexEffect`)

- Сітка 14×9 з джитером (`ShardMesh.build`, × `shardScale`). Затримка відриву = відстань від центру × D × 0.25 + до 1.5
  тіка, політ кожного уламка = D × 0.75, тож увесь вихор ≈ D (24 тіки ≈ 1.2 с; D × `durationScale`).
- `VortexWave`: орбіта кожного уламка в осях площини (`right`, `up`, `normal` з `ShardPlane`, тобто у світових
  координатах і для Spatial, і для плаского GUI): кут + `spiralTurns`·2π·e, радіус × (1 − e)^`pullStrength`, висота над
  площиною × (1 − e); e = (t/політ)^1.6 — «швидше й ближче». Обертання навколо нормалі, розтягнення вздовж дотичної
  (максимум посередині; без нього з `reduceMotion`, де також оберти ×0.5).
- Колір (`tint`): білий → фіолетовий (до e = 0.6) → білий, unlit, світіння шаром `eyes` зростає як 0.7·e², альфа гасне
  з e = 0.85, масштаб 1 → 0: уламок зменшується до нуля в точці.
- Кінця «після засмоктування» немає: хвиля просто закінчується, коли помирає останній уламок. Спалах
  (`ColorParticleOption(FLASH)`), кільце `END_ROD` і звук `ENDER_EYE_DEATH` прибрано, разом із параметрами
  `flashIntensity` і `ringParticles`; ці ключі в `Params.RETIRED` — `ShatterConfig` і `CustomEffectDef` пропускають
  їх без попередження. Звук лише на старті (`PORTAL_TRIGGER`). Вихор не створює жодних частинок (частинки власних
  ефектів на його основі — окремо, через `CustomEffect`).
- Точка: `center` — центр уламків; `source` — точка джерела з `FlyTarget` (без нього як `player`); `player` — 0.8 блока
  перед очима (трохи нижче). Без власних шейдерів; з Iris працює через ті самі ванільні типи рендера.
- Тест: розкид уламків від їхнього центру на t22 (за клієнтськими тіками) < 60% від t3; для `source` центр уламків
  < 0.8 блока від центру скрині (звичайний 0.40, Spatial 0.19, Complementary 0.46).

## Ефекти schema 2 (`fx/`, `fx/game/`)

- **Чисте ядро `net.shattergui.fx`** (лише Gson, без класів гри — юніт-тести в `src/test`): `Node` (читання JSON зі
  шляхом поля, обрізання діапазонів, «unknown field, ignored» у `finish()`), `FxProblems`, `Value` (стала, діапазон,
  крива, Безьє, ключі + jitter), `Easing`, `Gradient`, `FxParser` → `FxDef`, `Fracture` (grid/voronoi через відсікання
  півплощинами/strips/radial/pixels → шматки з трикутниками), `FxSimulation` (по тіках: фронт → старт шматка, шари-модулі,
  край, емітери, звуки; `Host` дає точки, маски, гравітацію, ванільні частинки й звуки).
- Координати площини: x праворуч, y вгору, z до камери, у блоках, центр GUI в 0. `DataWave` переводить їх у світ осями
  `ShardPlane` (`right`, `up`, `normal`), тож Spatial GUI працює без окремого коду; обертання =
  `rotateAxis(normal, angle) · rotateAxis(tiltAxis, tilt)`. Попередні pos/rot копіюються перед тіком → інтерполяція рендера.
- Шматок, що закінчився, прибирається через `setLifetime(age)`; хвиля живе `duration + 40` тіків або доки є спрайти.
- `fx/game/FxTextures`: розмір PNG із заголовка IHDR до декодування, `NativeImage.read`, `DynamicTexture` (крім масок),
  для масок і накладок — масив яскравості (білінійна вибірка на процесорі); ліміти ролей, 2 МБ на ефект, 64 одночасно;
  `TextureManager.release` при перезавантаженні. Текстури вантажаться лише в перезавантаженні ресурсів (потрібен GPU).
- Рендер оздоб (`render/WaveDecor` + `DecorRenderer`): накладки — копії геометрії уламків, трохи підняті над ними
  (0.006 · (k+1)), `normal` → `entityTranslucent`, `additive` → `eyes`, `multiply` → власний конвеєр
  `BlendFunction(DST_COLOR, ONE_MINUS_SRC_ALPHA)` з кольором, помноженим на непрозорість (з Iris — `entityTranslucent`);
  спрайти — квадрати за `cameraRenderState.orientation`, FULL_BRIGHT.
- Ліміти: `FxLimits`; глобальний лічильник спрайтів `FxSimulation.liveSpritesTotal` (перегляд у редакторі —
  `untracked()`), ванільних частинок ≤24 за тік і ≤600 на хвилю, усе × щільність частинок. `ShardManager.dispose`
  викликається на кожному шляху видалення хвилі (спам-захист, кінець, вимикач) → спрайти звільняються.
- Завантаження (`FxEffects.reloadAll`): ресурспаки → конфіг → пакунки (пізніший перекриває); старі текстури звільняються.
  `.shatterfx` читається з обмеженням кількості й розпакованого розміру записів, лише `effect.json` і `textures/*.png`,
  імена з `..`/`:`/`/` на початку відкидаються. `CustomEffects` (schema 1) пропускає файли зі `schema: 2`.
- Перегляд у редакторі — `ui/preview/FxSim`: та сама `FxSimulation` у 2D з перспективою 1/(1 − z); накладки
  наближено (не-normal × 0.6), ванільні частинки — кольорові крапки.
- Профіль: `[ShatterProfile] fx.<id>.fracture (режим, шматки, трикутники)`, `fx.<id>.setup`, `fx.<id>.done` (тіки,
  ванільні частинки, пік спрайтів).
- Обмеження: маска вигляду й фронт беруться за центром шматка (для дрібного візерунка — `pixels`); 14 різних текстур на
  практиці (1 + 1 + 4 + 8 слотів).
## Візуальний редактор ефектів (`editor/`)

- **Модель:** `FxDoc` — JSON-дерево ефекту (Gson), а не `FxDef`: редактор змінює самі дані, тож поля, яких він не
  знає, зберігаються. Undo/redo — знімки тексту (до 120); правки одного поля з одним ключем злиття, що йдуть менш ніж
  за 0.9 с одна за одною (повзунок, набір тексту), — один крок; `breakMerge()` при відпусканні миші. Вердикт парсера
  кешується до наступної зміни. Шляхи — ті самі, що в повідомленнях парсера (`layers[2].params.strength.keys[1]`),
  тож помилки знаходять своє поле (`FieldRow`, `FxEditor.problemsAt`). Чисте ядро (`FxDoc`, `ValueForms`,
  `EffectFiles`, `Templates`) покрите юніт-тестами.
- **Формат:** шари, емітери й звуки отримали `name` (лише для редактора) і `enabled` (`false` — не грає, «око»).
  Діапазони чисел — в одному місці, `fx/FxSchema` (парсер бере звідти параметри модулів; тест звіряє діапазони).
- **Додаткові перевірки** (`EditorChecks`) — мовою гравця: id зайнятий вбудованим ефектом чи іншим файлом, текстури
  (немає в теці, непридатні, завеликі для спрайта, понад 2 МБ разом), невідомі частинки й звуки.
- **Перегляд:** `EditorPreview` веде годинник (пауза, перемотка, швидкість, цикл) і той самий `FxSim`, що й мініатюри
  (`FxSim` тепер бере `FxDef` і функцію текстур замість `DataEffect`). Насінина фіксована, тож той самий момент
  виглядає однаково; перемотка назад перебудовує прогін і доганяє момент не довше 6 мс за кадр (без фризів).
  Зразки скрині, печі й інвентарю малюються кодом (`SampleImage.Kind`, `Sim.Stage.sample`).
- **Бібліотека текстур:** `TextureLibrary` перечитує теку що 2 с: PNG-заголовок до декодування, до 512 px, до 2 МБ,
  до 64 файлів; текстури завантажує `FxTextures.decodeLoose` (GPU + яскравість, id `fx/editor-lib/…`, що не може
  збігтися з id ефекту), звільняє `releaseLoose` при закритті налаштувань.
- **Розкладка:** `FxEditor.arrange` раз на кадр спершу робить покадрову роботу (перегляд, перебудова інспектора), тож
  нові віджети розкладаються до малювання. Інспектор перебудовується лише при зміні вибору чи форми; між тим
  контролі читають JSON наживо (undo, таймлайн і перегляд узгоджені).
- **Екрани:** редактор — один віджет; у вкладці «Власні» його розміщує `ShatterConfigScreen` (якщо місця менше за
  440×220 — підказка з кнопкою «На весь екран»), на весь екран — `FxEditorScreen` з тим самим екземпляром.
  `UiScreen.shortcut` (Ctrl+…, пробіл) питають раніше за сфокусований віджет, тож текстове поле не «з'їдає» Ctrl+Z.
  `Widget.doubleClicked` — перейменування. `ScreenRules` виключає з ефектів будь-який `UiScreen`.
- **Файли:** `FxPackage.writeAtomic` (тимчасовий файл поруч і `ATOMIC_MOVE`, інакше `REPLACE_EXISTING`) для
  ефектів редактора, імпорту кодів і класичних власних ефектів; експорт — у `exports/` (звідти нічого не
  завантажується, тож експорт не дублює ефект); імпорт перевіряє парсер, PNG-заголовки за роллю й 2 МБ до запису,
  зайнятий id → вільний, зайнята назва текстури з іншим вмістом → `<ім'я>_2` з переписаними посиланнями.
- **Вступ** (`Tour`) — 4 кроки, зберігається в `config/shatter_gui/editor.json` (окремо від налаштувань мода, щоб не
  робити їх «незбереженими»); закривається сам, якщо редактор зник з екрана.
## Стійкість UI і обхідник (UI crawler)

- **Краш зі зразком «Інвентар»** (`NativeImage.fillRect` поза межами 176×166): слот результату малювався з x = 151 шириною
  26. Тепер усе малювання зразків іде через `SamplePainter.fill` — прямокутник обрізається межами полотна, порожній чи
  повністю зовнішній нічого не малює, кожне обрізання рахується. Юніт-тест вимагає 0 обрізань для всіх зразків і
  перевіряє пікселі «Скрині». `SampleImage.texture` — у try/catch: при помилці сірий замінник, лог один раз, повторна
  спроба не частіше ніж раз на 5 с (помилка не кешується назавжди).
- **Віджети:** `Widget.render`, `layout`, `setBounds` ловлять винятки: віджет стає «зламаним» (лог один раз на вид
  збою — `FailureLog`/`UiFailures`, з назвою віджета й контейнера), замість нього плашка «Помилка тут» з підказкою,
  стан малювання відновлюється (`Ui.save/restore`: глибина кліпу й матриця), решта екрана працює. Увесь ввід у
  `UiScreen` (клік, подвійний клік, відпускання, перетягування, прокрутка, клавіші, символи, фокус, Esc, накладки)
  проходить через `guard`; зламаний віджет вводу не отримує. Накладка, яку не вдалося розмістити, закривається.
- **Миша:** координати NaN/Infinity (Spatial GUI поза площиною дає `Infinity`) — «миші немає»
  (`MouseGuard`): нічого не підсвічується, перетягування скасовується, клік/прокрутка ігноруються.
- **Обхідник** (`UiCrawlerScenario`, `-PtestOnly=crawler`, з `-PspatialGui=1.8` — зі Spatial GUI): вкладки налаштувань
  (і класичні ефекти), редактор усередині налаштувань і на весь екран: кожен список відкривається й обирається кожен
  пункт (до 16), повзунки на мінімум/максимум і сміття в поле точного значення ("1e99", "abc", "-0,5", ""), перемикачі,
  текстові поля (кирилиця, лапки, emoji, 300 символів), кнопки (крім позначених `leavesScreen`: тест у світі, теки,
  зберегти/скасувати налаштувань, повний екран), а все інше — клік, перетягування, правий і подвійний клік, прокрутка
  через справжній шлях вводу `UiScreen`. Далі: кожен модуль додано/змінено/видалено, емітери ванільний і спрайт, звук,
  бібліотека текстур (з непридатними файлами: погана назва, не PNG, 4000×4000), усі шаблони, діалоги (каталог,
  галерея, імпорт, меню, вступ), клавіатура (Tab, стрілки, Ctrl+Z/Y, F2, Delete…), 80 Undo/Redo, експорт → видалення →
  імпорт, код із буфера (сміття й справжній), зламана миша, GUI scale 1–4 × вікна 1920×1080, 1280×720, 854×480, 700×420
  з відкриттям кожного списку на кожній сторінці. Критерій — жодного збою віджета. Наостанок «канарка»: віджет, що падає
  при малюванні й при кліку, — перевіряє, що збій записано рівно раз на вид, на місці плашка, редактор далі працює.
- **Результат (26.10.2026):** без Spatial GUI — 2645 дій, 0 збоїв; зі Spatial GUI 1.8 — 2645 дій, 0 збоїв, канарка 2/2.
  Знайдено й виправлено: краш зразка «Інвентар» (вище); прогалини самого обходу (вкладка «Ефекти» після кліку на картку,
  «Формат 1» ховав вбудований редактор) — тепер обхід відновлює вкладку. У лозі лише навмисні попередження тестових
  ефектів (зламані файли з тестового ресурспаку).

## Візуальне розділення

- `ui/Section` — картка групи: світліша поверхня (`Theme.CARD` на темнішій панелі), рамка 1 px, заокруглення, заголовок
  з іконкою, лінія під ним, тонкі лінії між рядками, згортання стрілкою (плавне), стан згортання на сесію за ключем.
  Рідкісне («Додатково»: назва елемента, «грає», дублювати/видалити; у ефекті — рваний край, темп, зона) згорнуте.
- `ui/Hint` — підказки (опис модуля, примітки): дрібніший приглушений текст у плашці з акцентною рискою, переноситься.
- `ui/Segmented` — сегмент-кнопки за шириною тексту з переносом рядків, рядок розтягується на всю ширину; обрізається
  лише мітка, ширша за весь рядок, і тоді має підказку з повним текстом. Кнопки з обрізаною міткою показують її повністю
  в підказці.
- Дрібний текст масштабується кратно пікселю (`Ui.smallScale`: GUI 1–2 → 1, 3 → 2/3, 4 → 3/4), бо піксельний шрифт з
  дробовим масштабом розмивається; мітки SMALL і підписи полів переносяться, а не обрізаються. Вузькі місця: «Через
  край» → «Край», лічильник у заголовку картки ховається, якщо назва не вміщується, час під переглядом коротшає.
- Те саме в бічній панелі редактора (картки «Частини / Шари / Частинки / Звуки» з лічильниками) і на вкладках
  «Загальне», «Ефекти», «Екрани», «Власні» (картки з іконками, пояснення — підказками).
- Скріншоти до/після всіх вкладок при GUI scale 1–4: `EditorShotsScenario` (`-PtestOnly=shots -PshotsLabel=before|after`).
## 27–28.10.2026: зіткнення, списки, реальний перегляд, підказки, ліцензія

Збірка: потрібна Java 25 (`JAVA_HOME`). Якщо проєкт лежить у теці, яку синхронізує хмара (OneDrive тощо), і
`processResources` падає з «not a regular file» у `build/resources`, хмара вивантажила згенеровані файли — теку
`build/resources` можна просто видалити.

### Зіткнення у schema 2 (модуль `collide`)
- Один код зіткнень: `shatter/BlockCollision.move` (вісь x, z, потім y; відскок, тертя на землі, `REST_SPEED`),
  винесений зі `ShardInstance`; glass ним і користується (тертя 0.4 = старі ×0.6, ті самі множники обертання). Нове
  для обох: при ударі тіло притискається до грані бокса, в який влучило (`hitBox` + одна перевірка), а не зависає за
  крок до неї. `halfSize` — точка перевірки винесена вперед на радіус шматка.
- `FxSimulation`: модуль `collide` (`bounce`, `friction`, `rest: lie|fade`) у вікні шару; рух шматка через
  `Host.move` / `Host.inside` (типово — вільний рух, тож перегляд і тести без світу працюють). Шматок, що почав у
  блоці, ігнорує блоки, поки не вийде. Повільніші за `CHECK_SPEED` (0.003 бл/тік) не перевіряються і цей тік стоять
  (рух без перевірки проштовхував їх у підлогу — знайдено тестом). Приземлений і повільний «засинає» (`asleep`):
  гравітація в землю його не будить, поштовх > `WAKE_SPEED` — будить. `rest: fade` — альфа до 0 за 10 тіків
  (`restFactor`, і для накладок). Не більше `FxLimits.MAX_COLLIDING_PIECES` (600) шматків хвилі з зіткненнями (вибір за
  хешем), спільний бюджет `BlockCollision.CHECKS_PER_TICK` (4000 пошуків блока за тік, скидається в
  `ShardManager.tick`); понад бюджет шматок чекає наступного тіку (не проходить крізь блок), порядок шматків щотіку
  зсувається. Запис відкриття (reverse) має власний бюджет 40 000, далі — вільний рух.
- `DataWave` переводить площинні координати у світові осями площини (`right/up/normal`) — Spatial GUI і шейдери без
  окремого коду. Перегляд (`FxSim`) — умовна підлога `stage.floor()`.
- Сумісність: модуля немає — поведінка як раніше (тест `withoutTheModulePiecesFallThroughAsBefore`). Шаблони
  `glass` і `rain` мають шар «Land».
- Тести: `FxCollideTest` (8), `CollideScenario` (`-PtestOnly=collide`): кам'яна підлога, щотіку центр кожного уламка
  ≥ верхньої грані (найнижчий −59.91 при підлозі −60.0), 100 % на підлозі; з відскоком 0.7 — підйом 0.16–0.20 бл/тік.

### Випадні вікна (`ui/Reveal`)
- Причина: клік по заголовку відкритого списку закривав оверлей як «клік поза ним», а потім той самий клік доходив до
  заголовка, і `mouseDown` бачив `popup == null` → відкривав знову з нуля.
- `Reveal`: closed/opening/open/closing, прогрес зберігається при розвороті; відкриття 0.16 с, закриття 0.09 с,
  тихіший клік (`Widget.closeClick`, гучність 0.35). `Overlay.ownsClick` (клік по власнику — не «поза»),
  `Overlay.closing` (малюється, але без вводу, не «верхній»; `hasOverlay`/Esc/фокус дивляться на `topOverlay`).
- Те саме виправлено в меню ефектів (`EffectsMenu.toggle` з кнопки «…»). Діалоги модальні (клік поза ними не
  проходить), вибір кольору й картки вбудовані — помилки там не було.
- Тести: `RevealTest` (6), реальні кліки в `EditorScenario`.

### Реальний перегляд (`ui/preview/RealPreview`)
- Екрани: `ContainerScreen(ChestMenu.threeRows)`, `FurnaceScreen`, `CraftingScreen`, `InventoryScreen(player)`
  (меню гравця). Розкладка — захищений `Screen#init()` через `ScreenAccessor` (не `init(int,int)`, на якому події
  Fabric і через них Spatial GUI); витягання — кроки `extractRenderStateWithTooltipAndSubtitles` по одному
  (`ScreenCapture.extractQuietly`), бо сам метод — точка подій Fabric і падає для екрана без `setScreen`.
  `removed()` не викликається (віддав би предмети сітки крафту). `MinecraftMixin` блокує будь-який `setScreen` під час
  побудови. У творчому режимі «Інвентар» не створюється: `InventoryScreen.init` перетворюється на творчий, а
  конструктор `CreativeModeInventoryScreen` пише `player.containerMenu` (javap) — для нього зразок.
- Кадр: той самий шлях, що захоплення закриття (`GameRendererMixin`). Кадр N — звичайний, у кінці копія в `hold`;
  кадр N+1 — замість GUI витягується екран перегляду, ціль очищається, після `GuiRenderer#render` весь кадр
  копіюється в текстуру виду, а `hold` повертається в ціль (редактор показує попередній кадр двічі, без блимання).
  Один екран на два кадри, лише поки перегляд просить (`image()` за останні 0.5 с).
- Пастка: у GL-реалізації `copyTextureToTexture(src, dst, mip, dstX, dstY, srcX, srcY, w, h)` останні два числа йдуть
  у `blitFrameBuffers` як кінцеві координати, тож копія частини кадру в кут текстури розтягується. Тому текстура
  розміру вікна і UV області (`SampleImage.Image` u0/v0/u1/v1, v перевернуте), область = `contentBounds`, розширена
  до пропорцій 176:166. Малюється `GUI_TEXTURED_PREMULTIPLIED_ALPHA` (кольори вершин премультиплікуються в `Draw`).
- Налаштування `realPreview` (Загальне → Вигляд), швидка кнопка в панелі перегляду (на сесію). Без світу — зразки.
  Помилка → `failed`, спрощений режим, один WARN, банер на 8 с. Текстури звільняються в `disposeEditor`.
- Новий вид `CRAFTING` і в намальованих зразках. Тест: `RealPreviewScenario` (`-PtestOnly=realpreview`).

### Підказки й довідка
- `UiScreen.tooltipOwner`: підказка віджета або найближчого предка (рядок поля); картка (`Section`) свою не
  поширює. Заголовок жирний і переноситься, затримка 0.4 с, показ після Tab/стрілок біля сфокусованого, рамка завжди
  в межах екрана. Числа редактора — `EditorUi.withRange` (діапазон і типове в одиницях), перемикачі й списки —
  типове значення. `SettingRow` тепер теж несе підказку.
- `Section.help` — «?» у заголовку (розгорнута підказка, клік → `HelpDialog` на сторінці); `HelpDialog` — 8 сторінок
  (`PAGES`), кнопка «Довідка» в `TopBar`.
- Тести: `EditorLangTest.everyControlHasAShortTooltip` (en_us, uk_ua, довжина); `TooltipScenario`
  (`-PtestOnly=tooltips`) обходить усі вкладки, редактор з кожним модулем, емітер, звук, довідку — 1101 контрол,
  без підказки 0. Обхідник UI після змін: 3188 дій, 0 збоїв.

### Ліцензія й автор
- Apache-2.0: `LICENSE` — офіційний текст з apache.org (SHA-256 cfc7749b…3d30), `NOTICE` (Copyright 2026 lilaktt,
  подяка Spatial GUI / Iris); LICENSE, NOTICE і THIRD_PARTY.md кладуться в jar. `gradle.properties`: `mod_author`,
  `mod_license`, `mod_sources`/`mod_issues` (https://github.com/lilaktt/shatter-gui — перевірити адресу перед
  публікацією). `fabric.mod.json`: автор, contact, `contributors` (Spatial GUI — для «Credits» у Mod Menu).
- Spatial GUI: код не копіювався (лише виклики API), тож умова MIT про копії не діє; у його jar немає LICENSE, а
  `authors` порожній — вказано власника репозиторію tastytrash, без вигаданого рядка copyright (`THIRD_PARTY.md`).
- «Про мод» — останній розділ вкладки «Загальне»: назва, версія, автор, ліцензія, посилання (з метаданих мода),
  примітка про переклади, сторонні матеріали.

## Швидкі ефекти (28.10.2026): що вже було

Перед додаванням — повний список, щоб не дублювати:
- Вбудовані в код (Java): `glass` (скло: уламки розлітаються, крутяться, падають і відскакують), `burn` (горить знизу,
  жар, обвуглювання, дим), `fly` (летить у блок-джерело або за край), `dissolve` (хвиля розсипає на квадрати, що
  світяться й дрейфують угору), `vortex` (спіраль у точку, фіолетовий).
- Файли schema 2 (`assets/shatter_gui/effects`, тривалість у тіках): `frost` 46 (іній від країв, шматки відколюються),
  `sand` 44 (пісок, вітер убік), `portal` 40 (вихор Енду до центру), `runes` 46 (світні гліфи піднімаються), `lightning`
  34 (іскри по краях, по одному спалаху), `glitch` 26 (зсуви смуг, кольорове мерехтіння), `paper` 36 (складається
  смугами й летить), `tiles` 40 (квадратні плитки по черзі падають), `explosion` 30 (спалах і ударна хвиля), `confetti`
  44 (конфеті), `bubbles` 46 (бульбашки), `soul_fire` 46 (синє полум'я душ), `pixelate` 40 (великі пікселі
  осипаються), `blinds` 28 (жалюзі: горизонтальні смуги роз'їжджаються).
- Схоже на ідеї зі списку: «Гармошка» ≠ `blinds` (там смуги роз'їжджаються, тут складаються до центру);
  «Обертання» ≠ `vortex` (там сотні уламків по спіралі, тут уся площина одним шматком). Дублів немає.

## Швидкі ефекти: що зроблено
- 13 файлів schema 2 (`pop` 9, `slide` 10, `flip` 10, `recede` 11, `door` 12, `ripple` 16, `twirl` 11, `drop` 18,
  `accordion` 12, `peel` 13, `spring` 12, `tear` 13, `iris` 14 тіків). Усі ≤ 60 уламків, ≤ 40 частинок, без спалахів,
  диму й мерехтіння; кожен має `reduced`-варіант і грає назад для відкриття. Список — `BundledParams.QUICK` (оголошений
  до статичного блоку, інакше `null` під час ініціалізації); `Params`: для них тривалість 4–40 тіків замість 4–400.
- Рушій: лише один новий параметр `spin.axis` (кут осі нахилу 0–π, типово π/2; `FxDef.Layer.axis`, NaN = як раніше,
  `FxSimulation.tiltAxis`). Потрібен для «Дверей» (поворот навколо вертикального краю). У шарах, що тільки
  нахиляють, треба `"speed": 0`, бо типова швидкість обертання spin — 0.2 рад/тік.
- `themes.json`: 6 нових правил (точило → `twirl`, ткацький верстат → `accordion`, стіл картографа → `peel`,
  каменетес → `tear`, аналой → `flip`, лійка → `drop`). `ThemesTest` очікує 15.
- Тести: `BundledEffectsTest.quickEffectsStayLight` (тривалість, уламки, частинки, без спалахів); у грі
  `-PtestOnly=bundled` — і звичайний, і `-PspatialGui=1.8`. (Застаріло: тоді зі Spatial GUI збирання
  пропускалось — виправлено, див. «Ефекти відкриття: що зроблено».)
- Скріншоти: `-PtestOnly=quickshots` у `build/run/clientGameTest/screenshots` (для кожного два кадри без Spatial GUI і один зі Spatial GUI).

## Переклади (28.10.2026)
- 12 мов: en_us (джерело), uk_ua (вручну), de_de, es_es, fr_fr, pt_br, pl_pl, zh_cn, нові ru_ru, ja_jp, ko_kr,
  it_it — машинні, потребують перевірки. Глосарій термінів — `docs/i18n/GLOSSARY.md`; уніфіковано pt «pedaço»,
  zh «碎块» і «层», pl «paczka», uk «емітер».
- Нові мовні файли збирались скриптами з таблиць (порядок ключів як в en_us, перевірка ключів і `%s`); у репозиторії
  скриптів немає — перевірку робить `LangFilesTest` (усі ключі, без зайвих, ті самі плейсхолдери, без невикористаних).
- Помилки імпорту/експорту тепер перекладаються: `FxIOException(key, english, args)` у `EffectFiles`/`FxPackage`,
  `FxEditor.localized` перетворює їх на `ui.shatter_gui.fx.io.<key>`. Англійський текст лишається для логу.
- `HardcodedTextTest`: рядок, що йде лише в лог, позначається `// not shown`.
- Гра: `LangScenario` для кожної мови з `-Plang=` (типово de_de,ja_jp,ru_ru; запускати по одній — пам'ять), усі вкладки й
  частини редактора на масштабах 1–4, скріншоти `lang_<xx>_s<scale>_…`; провал, якщо перекладений текст обрізано «…».
- «Про мод» і README: примітка про машинні переклади й як надіслати правку.
- Перший прогін знайшов обрізані тексти: de 3, ja 7, ru 8. Виправлено: коротші de/ru/uk підписи (Blockkollision,
  Reihenfolge, Wie Allgemein; Столкновения, В контейнер, Наложения, «альфа %s %»…), у ja одиниці `bl/秒` замість
  `ブロック/秒`, мінімальна ширина списку зразків у `PreviewPane` 56 → 64 px (ієрогліфи ширші). Після цього de, ja, ru:
  0 обрізаних перекладів. Скріншоти — у `build/run/clientGameTest/screenshots` (папка
  перезаписується кожним прогоном).

## Ефекти відкриття (28.10.2026): розслідування

Скарга: у грі (Spatial GUI 1.8 + Iris) працює лише `fly_from_block`, решта ефектів «Коли відкриваєш інтерфейс» нічого
не роблять. Що відбувається при відкритті (код, не здогадки):

- Шлях: `MinecraftMixin` RETURN `setScreen(new)` (лише якщо до того екрана не було) → `FlyController.onScreenOpened` →
  `ScreenRules.openEffectFor(keys, config)` (спершу `overrides`, потім глобальний `openEffect`) → `none` — нічого;
  `open:<id>` → `gather(...)`; інакше (лише `fly_from_block`) — виліт.
- Значення доходять: редактор пише `work.openEffect` з `openOptions()`, `ShatterConfig.isOpenEffect` приймає `none`,
  `fly_from_block` і будь-який `open:<id>` з правильним id (`FxParser.ID`), `overrides` розбираються тим самим
  `isOpenEffect`. Тобто `openEffect` приймає будь-який id, але **у вибірнику є лише ефекти schema 2** (`DataEffect` з
  `"open": true`, усі 27 вбудованих файлів): `glass`, `burn`, `dissolve`, `vortex` і власні schema 1 обрати для
  відкриття неможливо — для них коду відтворення назад немає зовсім.
- `gather`: перевіряє, що ефект — `DataEffect` з `openable`, і **першим же рядком після цього виходить, якщо екран
  показує Spatial GUI** (`open.skipped: Spatial GUI plane: gathering is only drawn on the flat GUI`). Зі Spatial GUI
  він показує інвентар і всі контейнери, тож у користувача збирання не запускалось ніколи — звідси «не працює
  взагалі». `fly_from_block` має окремий шлях для площини (`InventoryRendererFlyMixin`), тому працював.
- Друга причина зі Spatial GUI, яка вилізла б одразу після прибирання пропуску: `ScreenMixin` ховає екран, скасовуючи
  `Screen#extractRenderStateWithTooltipAndSubtitles` на HEAD. Spatial GUI (javap 1.8, `GameRendererMixin`) витягує
  екран саме цим викликом з `GameRenderer#extractGui` (`@ModifyArg` підміняє `GuiGraphicsExtractor` на ізольований), а
  малює його у свою текстуру в `beforeGuiRender` (той самий INVOKE `GuiRenderer#render`, що й у нас) і там же —
  площину (`renderInWorldPost`). Тобто скасування залишило б їхню текстуру порожньою; ховати треба площину (альфа
  `getFadeAlpha`), а не витягання.
- Тест `BundledEffectsScenario` для Spatial GUI збирання просто пропускав, а без нього перевіряв лише стан
  (`OpenAssemble.hides`, id хвилі), не кадр.
- Без Spatial GUI шлях повний: `ScreenCapture.requestCapture(екран, fx.reversed())` → наступний кадр екран
  витягується в очищену ціль і копіюється в слот → `DataWave` з `sim.reverse()` (вся симуляція записана наперед, кадри
  назад) → `OpenAssemble` ховає екран і блокує мишу до `waveDone`.

План: (1) відтворення назад для будь-якого ефекту — schema 2 як було, вбудовані й schema 1 через запис уламків
(`ReversedEffect`); (2) Spatial GUI: на час збирання альфа площини 0, текстура їхнього таргета копіюється після того,
як вони її намалювали, уламки лягають на `QuadBasis`, наприкінці площина з'являється на тому ж місці; (3) у списку
лише ефекти, що справді відтворюються назад; (4) тест на кадрах у трьох режимах.

## Ефекти відкриття: що зроблено

Знахідка тесту, якої не було в розслідуванні вище: **і без Spatial GUI збирання ламалось** — `OpenAssemble` рахував
кінець у мілісекундах (`тіки × 50 мс + 300 мс`), а хвиля йде в ігрових тіках. Щойно тіки йдуть повільніше за 20/с
(лаг, перший тік із записом ефекту наперед, ігровий тест), справжній екран з'являвся посеред збирання й закривав
уламки. Тест на кадрах показав це для всіх 33 ефектів (у середині кадр = фінальний). Тепер межі в тіках
(`OpenAssemble.tick` на END_CLIENT_TICK, без паузи): 10 тіків на старт, довжина хвилі + 6; 15 с реального часу —
лише остання страховка.

- Будь-який ефект з відкриттям: `ShatterEffects.opening(id)` → schema 2 з `"open": true` — `DataEffect.reversed()`;
  код (glass, burn, dissolve, vortex) і schema 1 на них — `effect/ReversedEffect`: уламки ріжуться як завжди, ефект
  проганяється наперед (частинки й звуки вимкнені — `EffectParticles.mute`, перевіряють `EffectParticles.spawn`,
  `EffectSounds.play`, burn), зберігаються ≤ 31 кадр (позиція, поворот, світло, кольори, масштаб, сяйво), далі хвиля
  грає їх від кінця до початку; ≤ 40 тіків запису (скло 22: далі воно лежить на підлозі), ≤ 30 тіків показу, перші
  30 % уламки проявляються. Останній крок — точно нерозрізана картинка, тримається 1 тік. `fly` і ефекти на ньому в
  список не потрапляють (`fly_from_block` — їхнє відкриття). Перегляд у налаштуваннях — `ui/preview/ReversedSim`.
- Burn назад — «Проявляється з жару» (`ui.shatter_gui.open.effect.burn`): попіл → обвуглене → жар → інтерфейс, без
  диму. Інших ефектів «без відкриття» серед вбудованих немає: усі 27 файлів мають шматки, що збираються.
- Затінення: `ShardTint.lightAge` — скільки тіків затінення граней «входило». Назад воно виходить до нуля (інакше в
  кінці стрибок від затінених уламків до неосвітленого GUI). Для schema 2 назад — `duration − час`.
- Spatial GUI: `FlyController.gather` → `SpatialGuiCompat.tryGather` → `SpatialGuiBridge.queueOpen`: їхня анімація
  відкриття скасовується, межі вмісту — `measureContentBounds`; `OpenAssemble.start(..., true)`: екран **витягується**
  (з нього Spatial GUI малює текстуру), а площина прозора (`InventoryRendererFlyMixin`: `getFadeAlpha ×
  OpenAssemble.spatialAlpha()`). На 2-му кадрі після відкриття, ПІСЛЯ `GuiRenderer#render` (Spatial GUI уже намалював
  текстуру в своєму `beforeGuiRender`), `completeOpen`: `updateQuadBasis` + `getQuadBasis`, GPU-копія таргета в слот,
  `ShardManager.spawn(..., cameraAnchor)`. Їхня площина висить на сталому зміщенні від камери, яка в 3-й особі ще
  їде ~300 мс, тому хвиля збирання малюється відносно камери кадру захоплення (`Batch.camera`) — уламки їдуть разом із
  камерою й лягають туди, де з'явиться площина. Розмиття світу Spatial GUI (`blurWorldBackground`, типово вимкнене) на
  час збирання не малюється (`WorldBlurMixin`), інакше розмило б уламки.
- Лишилось стрибком: затемнений/розмитий фон ванільного екрана з'являється в кінці разом з екраном (симетрично до
  закриття). З шейдерами уламки освітлює пак — легка різниця яскравості в кінці.
- Профіль: `open.gather (…)`, `open.record [id] (уламки, кадри)`, `spatial.queueOpen`, `spatial.completeOpen`,
  `spatial.openSkipped`.

Тест `GatherScenario` (`-PtestOnly=gather`; `-PspatialGui=1.8` — перша й третя особа; `-Pshaders -PshaderPack=…`):
кожен ефект зі списку відкриття (33: вбудовані + тестовий ресурспак), кадри читаються з самого кадру (`FrameGrab`,
`Screenshot.takeScreenshot` → сітка 192×108). Перевірки: хвиля стартувала; на 35/50/65 % екран схований і намальовано
> 0 уламків збирання (`ShardRenderer.gatherShardsDrawn`); кадр 50 % відрізняється від фінального в області панелі і
від світу до відкриття (крім 3-ї особи Spatial — там рухається камера); в кінці екран показано, кадр кінця = фінальний
(без стрибка), фінальний ≠ світ. Відкриття, що нічого не робить, падає на «середина ≠ фінал».

## Переворот, Віддалення, Двері: розслідування й виправлення

Чому виглядали однаково. У грі це 3D-уламки у світі (і зі Spatial GUI), але:
1. **Рух відкладено на кінець, а кінець з'їдало згасання.** `spin.tumble` — швидкість (рад/тік), не кут. У flip —
   `easeIn` 0.06 → 0.3 рад/тік: на половині часу ~0.35 рад (20°); у door — сталі 0.117 рад/тік. Прозорість спадала з
   50–70 % часу, тобто вся помітна зміна форми припадала на вікно, де ефект зникав. Тест
   `EffectFramesTest.quickEffectsMoveBeforeTheyFade` (найбільша зміна форми в першій половині, 1 − IoU з цілим
   інтерфейсом): старий flip — 0.10.
2. **Попередній перегляд (2D) зводив поворот до масштабу.** `FxSim`/`Sim.render`: нахил = `lx · cos(tilt)`
   (стискання по ширині), перспектива `1/(1 − z)` лише для центру шматка. У перегляді flip — звуження, door —
   звуження + зменшення, recede — зменшення, і все згасає.
3. Recede як «віддалення на осі погляду без нахилу» оптично й є масштабом, тому дублював pop/twirl.

Виправлення:
- Рушій: `spin.turn` — точний кут нахилу за вікном шару (крива, не інтегрується); `spin.pivot [u, v]` — вісь іде через
  точку інтерфейсу (петлі): центр шматка повертається навколо неї тим самим обертанням (`FxSimulation.rotate` =
  світове `rotateAxis(angle, normal)·rotateAxis(tilt, axis)`), зсув зберігається окремо й додається до `follow-path`.
- Перегляд: `FxSim` повертає кожну вершину в 3D і ділить на її глибину (камера за 1 блок, як у грі);
  `Sim.Piece.sx/sy` — спроєктовані вершини.
- `flip`: `turn` 0 → π/2 (easeInOut) навколо вертикалі через центр, згасання лише в останні 10 %. `door`: 5 смуг,
  хвиля згори (spread 0.18 — різне запізнення), `turn` 0 → 1.745 (100°), `pivot [0, 0.5]`, 14 тіків. `recede`:
  1.6 ширини вглиб і трохи вгору до горизонту, нахил назад 0.65 рад, масштаб до 0.12 (з перспективою ≈ 5 % ширини),
  колір до #6e7078, 12 тіків.
- Інші швидкі: `spring` («стискається як пружина») стискав рівномірно — тепер стискання перспективою (нахил навколо
  нижнього краю, `pivot [0.5, 1]`), потім виліт угору. `peel` до половини часу майже не рухався (0.07) — кут куточка
  точний з початку, від нижнього лівого кута. Решта рухаються як описано (≥ 0.18, поріг 0.15).

Тест `EffectFramesTest` (юніт): кожен вбудований ефект малюється програмним растеризатором так само, як гра/перегляд
(камера за 1 блок, поворот вершин, перспектива), 120×80; моменти 20/40/60/80 %. Для пари: маска (1 − IoU), картинка
(візерунок інтерфейсу × колір ефекту, середня різниця по об'єднанню), рамка (зсув краю / зміна розміру щодо більшої);
береться найбільша, і середня по моментах. Поріг «майже однакові» 0.3: найближча реальна пара — 0.48 (confetti/drop,
pixelate/tiles), копія — 0. Ще: швидкі ефекти рухаються до згасання, і flip/recede/door роблять, що в описі.
`SHATTER_FRAMES_DIR=<тека>` друкує таблицю для інших файлів зі старою проєкцією.

| ефект | 20 % | 40 % | 60 % | 80 % | найближчий |
|---|---|---|---|---|---|
| flip | 50×75/36 | 47×90/35 | 28×100/23 | 8×100/7 | pop 0.62 |
| recede | 47×66/31 | 32×40/12 | 18×23/4 | 8×9/1 | frost 0.68 |
| door | 50×70/32 | 43×70/21 | 31×70/13 | 13×50/4 | flip 0.64 |
| pop | 53×75/40 | 43×63/27 | 33×48/16 | 12×15/2 | twirl 0.49 |
| slide | 52×74/35 | 53×76/35 | 56×79/32 | 0×0/10 | ripple 0.63 |
| ripple | 51×78/34 | 45×83/29 | 32×83/20 | 19×76/7 | runes 0.58 |
| twirl | 53×78/33 | 53×78/27 | 40×60/13 | 18×28/4 | pop 0.49 |
| drop | 50×60/30 | 50×15/8 | 0×0/0 | 0×0/0 | confetti 0.48 |
| accordion | 48×70/33 | 40×68/23 | 30×65/13 | 13×55/3 | pop 0.59 |
| peel | 55×79/34 | 55×78/29 | 52×76/25 | 52×78/8 | flip 0.64 |
| spring | 50×58/25 | 50×36/14 | 57×75/40 | 0×0/7 | ripple 0.70 |
| tear | 75×79/38 | 92×78/38 | 100×58/22 | 100×38/5 | portal 0.90 |
| iris | 47×63/23 | 35×50/14 | 28×40/7 | 18×30/1 | recede 0.70 |
| frost | 50×70/35 | 67×86/32 | 56×71/11 | 25×36/2 | runes 0.57 |
| sand | 46×80/28 | 33×75/19 | 32×81/10 | 28×80/4 | ripple 0.58 |
| portal | 50×70/30 | 91×100/22 | 98×99/4 | 0×0/0 | pixelate 0.54 |
| runes | 53×70/35 | 61×76/29 | 59×85/14 | 53×69/3 | frost 0.57 |
| lightning | 50×70/35 | 76×98/34 | 100×100/22 | 74×100/6 | frost 0.60 |
| glitch | 53×70/33 | 56×70/27 | 56×70/16 | 61×66/8 | runes 0.58 |
| paper | 73×70/31 | 79×73/21 | 43×49/10 | 42×56/7 | runes 0.73 |
| tiles | 57×85/33 | 53×85/22 | 68×85/9 | 0×0/0 | pixelate 0.48 |
| explosion | 99×100/31 | 0×0/0 | 14×11/1 | 63×35/2 | confetti 0.95 |
| confetti | 71×98/21 | 100×91/9 | 0×0/0 | 0×0/0 | drop 0.48 |
| bubbles | 56×61/28 | 61×56/19 | 58×24/7 | 0×0/0 | pixelate 0.56 |
| soul_fire | 50×70/34 | 53×83/26 | 55×85/11 | 40×80/2 | runes 0.60 |
| pixelate | 50×70/35 | 54×60/28 | 55×29/9 | 0×0/0 | tiles 0.48 |
| blinds | 68×70/35 | 100×70/35 | 100×70/19 | 100×24/2 | slide 0.74 |

(рамка ш×в у % поля / покриття %). Старі версії зі старою проєкцією перегляду: flip 50×70 → 47×70 → 40×70 → 27×68
(лише звужується), door 45×65 → 33×58 → 26×53 → 0×0, recede 48×68 → 43×63 → 30×43 → 0×0 — усі три «стискаються й
зникають». Збігів (< 0.3) немає ні до, ні після: старі три відрізнялись не формою кадру, а відкладеним рухом під
згасанням і перевіркою «рух до згасання» (flip 0.10 → 0.22).

Скріншоти (`-PtestOnly=quickshots`): `<режим>_<ефект>_<close|open>_<10..90>` (flat, spatial_first; spatial_third —
відкриття; закриття в 3-й особі малюється, поки камера Spatial GUI їде назад до гравця, як і раніше) і аркуші
`sheet_*.jpg`. Сценарій `QuickShotsScenario` (`-PtestOnly=quickshots`).

## Вибір ефекту в меню замість клавіші (28–29.10.2026)

Причина скарги: призначити ефект можна було лише двома випадними списками на вкладці «Загальне» (і в «Екранах»);
картки вкладки «Ефекти» тільки обирали, що налаштовувати (`selectEffect`), без жодної дії «використати». Клавіша
`cycle_effect` (типово K) гортала `ShatterEffects.ids()` — усі 30+ ефектів по колу, тож потрібний ефект знаходився
лише випадково.

- `ShatterConfig.favorites` (список id; старі конфіги без поля → порожній, Gson + `sanitize`).
- `ShatterKeys`: клавіша типово **не призначена** (`InputConstants.UNKNOWN`); `nextFavorite` — наступний улюблений
  у порядку списку ефектів; без улюблених — action bar `message.shatter_gui.no_favorites`; назва — `displayName`
  (переклад або назва власного ефекту), не id.
- Вкладка «Ефекти» (`ShatterConfigScreen`): під вибраною карткою `addAssignRow` — «Для відкриття» (`open:<id>`; для
  fly — `fly_from_block`; без відкриття вимкнена з поясненням у підказці), «Для закриття», «Для обох», «Прибрати»
  (відкриття → `none`, закриття → `glass`; вимкнена, якщо ефект не використовується). На картках: `FavoriteStar`
  (клік / Enter / пробіл; подвійний клік по зірці — два перемикання, не «для обох») і `Badges` «Відкриття» /
  «Закриття» за поточним вибором. Подвійний клік по картці (`Card.onDoubleClick`) → `ConfirmDialog` «Використати для
  обох?» (для ефекту без відкриття — лише закриття). Статус унизу — що змінилось.
- Перегляд: `Segmented` «Відкриття / Закриття / Туди й назад» (лише на вкладці «Ефекти»), сцени — `openScene`,
  `closeScene(id, null)`, `closeScene(id, open)`. Для ефекту без відкриття в режимі «Відкриття» — напівпрозора плашка
  з поясненням замість порожнього перегляду (`ui.shatter_gui.preview.no_open`).
- Переклади: 33 нові ключі у всіх 12 мовах (en, uk — вручну, решта машинні), підказки контролів додано в
  `EditorLangTest.everyControlHasAShortTooltip`. `TooltipScenario`: 1165 контролів, без підказки 0.
- Тест `ChooseScenario` (`-PtestOnly=choose`, також у повному прогоні замість старої перевірки K): справжня миша по
  кнопках (з `ScrollView.reveal`), бейджі лише на одній картці, «Прибрати», fly → `fly_from_block`, три режими
  перегляду (`describePreview`), зірка вкл/вимк, подвійний клік через `UiScreen.mouseClicked(…, true)` (справжня миша в
  тесті повільніша за час подвійного кліку гри) → діалог → Enter; клавіша (тест сам призначає K): без улюблених нічого
  не змінює, з [flip, glass, vortex] — vortex → flip → glass → vortex; старий конфіг без `favorites` читається.

Прогони 29.10.2026 (по одному, `--stop` після кожного): `gather` — плаский, Spatial GUI 1.8 (1-ша і 3-тя особа),
Iris + Complementary Reimagined r5.8.1, Iris + Complementary + Spatial GUI 1.8 — усі 33 ефекти, 0 збоїв; `bundled`
плаский і зі Spatial GUI; `quickshots` обидва; `choose`; `tooltips`; `./gradlew build` (82 юніт-тести).

## Другий інтерфейс після закриття і площина Spatial GUI в тематичному режимі (29.10.2026): розслідування

Скарга (відео, Spatial GUI 1.8, 3-тя особа): у перші кадри закриття поряд дві однакові площини зі скринею, одна висить
нерозбитою ~1 с і зникає; лише коли ефект відкриття ≠ ефекту закриття. У тематичному режимі (піч) позаду ефекту
закриття блимає звичайний інтерфейс, що «їде назад».

Де живуть екземпляри відкриття (код):
- `OpenAssemble` — лише стан «ховати екран» (екран, Spatial, дедлайн). `FlyController.onScreenClosed` робить `stop()`.
- **Хвиля збирання — звичайна партія `ShardManager`** (`Batch` з `effect.opens()`, текстура в слоті `CapturePool`,
  на Spatial — з `cameraAnchor`). Її **ніхто не прибирає при закритті чи заміні екрана**: ні `onScreenClosed`, ні
  `MinecraftMixin`. Вона доживає свій час. Закриття «скасовує» лише своє: `OpenAssemble`, а партії не чіпає взагалі
  (не за id — ніяк).
- **Головна причина — хвіст schema 2 назад.** `DataWave`: `lifetime = sim.duration + 40`, а `sim.gone(p)` при
  відтворенні назад ніколи не true. Хвиля закінчується (`sim.alive()` false на `time > duration` → `waveDone` → екран
  показано), але шматки живуть ще **40 тіків (2 с)**: повна нерозрізана копія інтерфейсу лишається у світі. У пласкому
  GUI її закриває сам GUI, зі Spatial GUI вона збігається з площиною — тому тест збирання («кінець = фінал») її не
  бачив. Закрити екран у ці 2 с → копія висить поруч з уламками закриття; у 3-й особі вона прив'язана до камери
  кадру захоплення (`cameraAnchor`), а камера Spatial GUI їде назад до гравця — копія «їде назад» разом з нею. Звідси
  і «друга площина ~1 с», і «звичайний інтерфейс без ефекту, що іде назад» у тематичному режимі (тематичний режим
  якраз робить закриття іншим, ніж відкриття).
- Чому «лише коли ефекти різні»: з типовим відкриттям `fly_from_block` партії немає (виліт рухає сам GUI/площину);
  копія з'являється лише з відкриттям збиранням. З однаковим ефектом копія теж є, але на тлі тих самих уламків її
  важко помітити.
- Те саме для `ReversedEffect` (скло, горіння…), якщо закрити екран посеред збирання (до 1.5 с): партія дограє до кінця
  поруч з ефектом закриття. Відкладене збирання теж не скасовується: `ScreenCapture` (спільний `pending` — перезапише
  закриття), але `SpatialGuiBridge.pendingOpen` спрацює вже після закриття, якщо `getHookedScreen()` збігся.
- Spatial GUI, тематичний режим: тематичний вибір іде тим самим кодом (`ScreenRules.effectFor` → `MinecraftMixin` →
  `SpatialGuiCompat.tryShatter`), окремої гілки немає. Але в мосту є гілки, де наш ефект **здається, а їхню
  анімацію закриття не скасовує**: `queue` без текстури → пласка копія + їхня площина грає своє закриття позаду;
  `complete`: «plane or texture gone», `slot == null`, понад 5 кадрів очікування → взагалі ні нашого ефекту, ні
  скасування. Ці гілки треба звести в одне місце: якщо Spatial GUI показував екран, його площина ховається завжди, а
  ефект іде або на площині, або пласкою копією.

План: (1) при будь-якому закритті й заміні екрана — `ShardManager.endOpenings()` (партії відкриття негайно
звільняються з текстурами), скасування відкладених захоплень відкриття; партія відкриття пам'ятає свій екран і не
малюється й прибирається, щойно `Minecraft.screen` інший; schema 2 назад живе рівно до показу екрана. (2) Один метод
«взяти площину Spatial GUI» з гарантованим скасуванням їхнього закриття у всіх гілках. (3) Покадровий облік видимих
площин (`FrameAudit`) для тестів.

## Другий інтерфейс і площина Spatial GUI: що зроблено

- **Хвіст schema 2 назад прибрано**: `DataWave` назад живе `length() + 1` тік — шматки зникають у тому самому тіку,
  де `waveDone` показує справжній екран (раніше `duration + 40`, ~2 с копії в світі під GUI/площиною).
- **Відкриття не переживає свій екран**: `Batch.owner` — екран, який збирає хвиля; `FlyController.endOpening(why)`
  на HEAD `Minecraft#setScreen` при будь-якій зміні екрана (закриття або заміна) і в `onScreenClosed`: `OpenAssemble.stop`,
  `ScreenCapture.cancelPendingOpening`, `SpatialGuiCompat.cancelPendingOpen`, `ShardManager.endOpenings()` (усі хвилі
  відкриття, незалежно від id ефекту, з текстурами). Захисти: `ShardManager.spawn` не створює хвилю відкриття, якщо її
  екран уже не відкритий; `tick` прибирає, а `ShardRenderer` не малює хвилю відкриття, чий екран не `Minecraft.screen`
  (`Batch.belongsToScreen`). Рядки профілю `open.ended (closed/replaced by …: N gathering wave(s) dropped)`,
  `open.dropped`.
- **Одне місце для площини Spatial GUI**: `SpatialGuiCompat.startClose(screen, onPlane, effect, target, params,
  preview)` — і `MinecraftMixin` (загальний вибір, overrides, тематичний режим, власні ефекти, ефект, обраний клавішею),
  і перегляд «у світі» йдуть лише через нього. Якщо Spatial GUI показував екран: площина розбивається на місці, а якщо
  ні — **все одно ховається** (`SpatialGuiBridge.hideClosingPlane`, той самий код, що й в успішній гілці) і ефект іде на
  пласкій копії. У `complete` кожна гілка збою («plane or texture gone», немає слота, понад 5 кадрів, немає рендерера)
  тепер `giveUp`: сховати площину + пласка копія (`spatial.fallback to the flat copy`). Раніше ці гілки лишали їхню
  анімацію закриття і не запускали нічого.
- Тематичний режим окремої гілки не мав: «звичайний інтерфейс, що іде назад» — це та сама копія з хвоста збирання,
  прив'язана до камери (`cameraAnchor`), яку в 3-й особі камера Spatial GUI везла назад до гравця. Тематичний режим
  лише робив закриття іншим, ніж відкриття.

Облік кадрів `debug/FrameAudit` (вмикає тест): за кадр — справжній плаский екран (витягнутий для малювання, не
захоплення й не для текстури Spatial GUI), прозорість площини Spatial GUI (`getFadeAlpha` питається лише з
`drawInventory`), хвилі відкриття й закриття, що щось намалювали. `instances()` — скільки разів інтерфейс на екрані.

Тести:
- `DuplicateScenario` (`-PtestOnly=duplicates`, `-PdupOnly=a,b` — підмножина): кожне відкриття збиранням із закриттям
  glass (для glass — pop) і кожен ефект закриття після `fly_from_block` і після pop; закриття під час відкриття, одразу
  після, через 2 с. У кожному кадрі `instances() ≤ 1`, після ефекту — 0 і жодної хвилі відкриття. Перевірено, що тест
  падає на старому коді: з тимчасово поверненими `duration + 40` і без `endOpening` — 10 з 18 комбінацій
  (`door,glass`): «frame … shows the interface 2 times» і під час збирання (хвиля дограє поруч із закриттям), і після
  нього (копія під пласким GUI).
- `ThematicPlaneScenario` (`-PtestOnly=thematic`): тематичний режим, піч, скриня, ковадло, верстак, стіл зачарувань,
  інвентар — як у таблиці (burn, fly, explosion, glass, runes, загальний) і з fly/pop; з 2-го кадру після закриття
  площина Spatial GUI не малюється, ≤ 1 інтерфейс на кадр, ефект малює й закінчується. Поки екран відкритий —
  рівно 1 інтерфейс (зі Spatial GUI — саме площина), тобто облік бачить площину.

## Швидша поява (29.10.2026)

- `general.openSpeed` (`Params.OPEN_SPEED`, 0.5–3, типово 1.5, «Швидкість появи» в «Загальне → Відтворення», у конфігу
  `general.params.openSpeed`, коли відрізняється від типового). Діє лише на відкриття, `effectSpeed` діє й далі.
- `params/OpenTiming`: довжина відкриття = min(довжина ефекту після загального масштабу, 10 тіків для швидких / 15 для
  великих) / openSpeed, не менше 3. Типово ≤ 7 тіків (0.35 с) і ≤ 10 (0.5 с) **до показу справжнього екрана**: шматки
  рухаються `playback = ticks − 2`, тік спокою в зібраному стані, далі екран (див. доповнення нижче). reduceMotion укорочує ще (0.65 у
  загальному масштабі), мінімум 3 тіки.
- Schema 2: `FxSimulation.reverse(ticks)` — запис усього прогону грається за `ticks` (вибірка й змішування кадрів,
  `forwardAt`), рух той самий. Код-ефекти: `ReversedEffect` (довжина з `OpenTiming`, без тіку утримання). Перегляди:
  `ReversedSim`, `FxSim` через `DataEffect.simulate`; редактор ефектів — кнопка «Відкриття» в перегляді
  (`EditorPreview.setOpening`): ефект назад тієї самої довжини, тож openSpeed там видно як множник. `fly_from_block`:
  `flyDurationTicks / openSpeed`, не менше 2.
- Введення: як і раніше блокується лише поки екран схований/летить (`FlyController.busy`), а екран тепер показується в
  тому ж тіку, де шматки стали на місце (раніше +1 тік утримання; у schema 2 до того ж 2 с копії). Профіль:
  `open.timing.start`, `open.timing.shown (мс, тіки, чому)`, `open.timing.inputUnlocked` — в одному тіку.
  Виміряно в тесті: великі 10 тіків, швидкі 7 (було 12+ і ~16–30).
- Тести: `OpenTimingTest`, `BundledEffectsTest.aQuickerOpeningEndsGatheredToo` (усі 27 файлів назад за 6 тіків
  закінчуються зібраними), `GatherScenario`: довжина ≤ 7/10 тіків; перевірка «уламки видно» тепер і на 80 % — `drop`,
  стиснутий до 6 тіків, у середині ще піднімається з підлоги поза кадром.

Доповнення (30.10.2026), після прогонів:
- Перевірка стрибка в `GatherScenario` тепер порівнює **останній кадр збирання з першим кадром справжнього екрана**
  (сусідні кадри), а не «кінець» із кадром через 10 тіків. Вона знайшла два справжні стрибки, які пришвидшення
  внесло й стара перевірка не бачила: (1) затінення schema 2 назад рахувалось як `duration − час` — після стиснення до
  7–10 тіків наприкінці уламки були повністю затінені, темніші за інтерфейс (16 % кадру); тепер `lightAge` =
  показаний момент прогону (`FxSimulation.shownForward`), у кінці 0; (2) без тіку утримання останній кадр ще в русі.
  Тож: `OpenTiming.playback = ticks − 2`, один тік спокою в зібраному стані (`alive` до `playback + 1`, `ReversedEffect`
  `length + 2`) — справжній екран усе одно на `ticks` (0.35/0.5 с), що й видно в профілі (`open.timing.shown 7/10
  ticks`). Порівняння з допуском зсуву на одну клітинку сітки (`FrameGrab.changedBeyondShift`: субпіксельне
  семплювання далекої площини в 3-й особі, камера Spatial GUI, що ще доїжджає) і всередині панелі на клітинку від краю
  (ванільний затемнений фон з'являється з екраном); курсор відводиться з центру (підсвічений слот — не стрибок).
- **Відоме, не виправлене: з шейдерпаком колір у кінці відкриття стрибає.** Уламки малюються у світовому проході, пак
  їх освітлює й тонує (Complementary на заході сонця — рожевий, уночі — темний), а справжній GUI/площина Spatial GUI —
  після обробки паку. Різниця в тесті — ~98 % панелі, у всіх 33 ефектів. Тест це міряє й пише
  `KNOWN (shader pack colours the pieces)`, але не валить прогін. Виправлення — малювати хвилю відкриття після
  світу (як Spatial GUI малює свою площину: захоплена проєкція + власний конвеєр перед `GuiRenderer#render`).
- Прогони: `duplicates` — плаский 282/0, Spatial GUI перша особа 282/0, третя 282/0 (`-PspatialMode=third` —
  повтор лише третьої), Iris + Complementary 282/0; `thematic` — Spatial GUI 1-ша і 3-тя
  особа, плаский, Iris + Spatial GUI; `gather` — плаский, Spatial GUI, Iris, Iris + Spatial GUI; `tooltips` — 1198
  контролів, без підказки 0.

## Esc: картинка застигає ~10 с (31.10.2026): розслідування

Скарга: Esc з ефектом — картинка застигає на ~10 с, потім з'являється меню; друге Esc закриває його з ефектом одразу.

Відтворено автотестом `EscScenario` (`-PtestOnly=esc`, справжня клавіша Esc через `TestInput.pressKey`): скриня
(клавішею use) й інвентар (клавішею інвентаря) — усталені, через 1–2 тіки після відкриття (під час ефекту відкриття),
під час ефекту закриття попереднього екрана; Esc у світі під час ефекту закриття (меню паузи) і Esc одразу після
відкриття паузи; відкриття `fly_from_block`, `open:pop`, `open:glass`, `none`, тематичний режим вимк./увімк. Плаский
режим, старий код: 48 випадків, **падають рівно ті, де меню паузи відкривається збиранням** (`open:pop`, `open:glass`,
з тематичним режимом і без): «the pause menu must be shown within 15 ticks, still hidden by its opening after 16 (game
paused: true)». Скриня, інвентар, Esc під час відкриття чи закриття — закриваються в тому самому виклику (0 тіків).

Виміряно (`"profiling": true`, новий рядок `close.latency` — від HEAD `setScreen(null)`, тобто від самого натискання,
бо Esc закриває екран у тому ж виклику, до першого кадру ефекту): 29–35 мс у всіх сценаріях (кадр захоплення + кадр
уламків; у gametest ~60 к/с). У профілі паузи зі збиранням є `open.gather (… capture next frame)`, але немає
`open.timing.shown` — хвиля не долітає.

Причина (код, javap `Minecraft#runTick`, `DeltaTracker$Timer`):
- Меню паузи в одиночній грі ставить гру на паузу (`pause = singleplayer && screen.isPauseScreen() && !published`,
  оновлюється в кінці `runTick`). `Minecraft#tick` і Fabric `END_CLIENT_TICK` на паузі **йдуть** далі (20/с), але
  `ShardManager.tick` виходить на `mc.isPaused()`, а `OpenAssemble.ticks` рахує лише тіки без паузи. Тож хвиля збирання
  меню паузи стоїть на першому кадрі (застигла картинка — це її шматки поверх застиглого світу), `waveDone` не настає,
  а тіковий дедлайн (`deadline = ticks + length + 6`) не наближається. Екран ховається (`OpenAssemble.hides`) до
  запасного дедлайну реального часу `MAX_MS = 15 000` мс — це і є «~10 с».
- `DeltaTracker.getGameTimeDeltaPartialTick(false)` на паузі повертає застиглий `pausedDeltaTickResidual` (javap), тож
  і `FlyOpen` (виліт паузи з краю для fly) на паузі йде ривками по тіках, хоча й закінчується.
- Друге Esc працює одразу: клавіатура під час ефекту не блокується (`FlyController` блокує лише мишу), а
  `setScreen(null)` → `FlyController.endOpening` прибирає хвилю. Esc нічого не «з'їдав» — меню вже було відкрите, але
  невидиме.

Що перевірено й не є причиною:
- Блокуючих очікувань на render thread у моді немає: немає `sleep/wait/join`, fence чи readback (`copyTextureToBuffer`
  прибрано давно), черги Spatial GUI (`SpatialGuiBridge.queue/complete`) виконуються в наступному кадрі з межею 5 кадрів
  і запасною пласкою копією; захоплення (`ScreenCapture`) теж скасовується через 5 кадрів.
- Mixin-ів на `keyPressed`/`onClose` немає; `MinecraftMixin` на HEAD `setScreen` нічого не скасовує, крім екранів
  реального перегляду редактора (`RealPreview.blocksSetScreen`), які ніколи не показуються.
- Тайм-аутів ~10 с, крім `OpenAssemble.MAX_MS`, немає.

План: (1) відкриття — анімація інтерфейсу, а не світу: хвилі відкриття, `OpenAssemble` і `FlyOpen` ідуть за власним
годинником, що рахує всі клієнтські тіки й на паузі бере частку тіку з реального часу (`EffectClock`); хвилі закриття
й ванільні частинки, як і раніше, стоять на паузі разом зі світом. (2) `MAX_MS` — 5 с (запасний, тепер не має
спрацьовувати). (3) Watchdog закриття: понад 50 мс на крок — один рядок попередження з назвою кроку. (4) Тест падає
при затримці (так, див. вище), проганяється пласко, зі Spatial GUI (1-ша і 3-тя особа) і з шейдерпаком.

## Esc: що зроблено

- `shatter/EffectClock`: годинник відкриттів. Тіки — усі клієнтські (`END_CLIENT_TICK` іде й на паузі), частка тіку —
  ігрова без паузи, на паузі — реальний час від останнього тіку. `ShardManager.tick` на паузі тікає лише хвилі
  відкриття (закриття й ванільні частинки стоять разом зі світом, як і раніше); `OpenAssemble.ticks` рахує й тіки
  паузи; `ShardRenderer` бере для хвиль відкриття `EffectClock.partialTick()`; `FlyOpen` — теж (на паузі виліт тепер
  плавний, а не по тіках). `OpenAssemble.MAX_MS` 15 → 5 с (запасний).
- `debug/CloseWatchdog` (завжди ввімкнений, не залежить від `profiling`): від HEAD `setScreen(null)` і 10 кадрів після
  нього кроки (`end the opening`, `stop the fly-in`, `pick the effect`, `start the close`, `capture: …`, `Spatial GUI:
  copy its plane …`) і весь виклик з ванільною частиною; понад 50 мс — один рядок `Closing <екран> took N ms in step
  "<крок>"` на закриття.
- Профіль: `close.latency (setScreen(null) -> first frame of the effect)`.
- Клавіатура під час ефектів не блокується ніде (лише миша, поки GUI летить/збирається), тож Esc завжди доходить до
  екрана в тому ж виклику.

Прогони `-PtestOnly=esc` (по одному, `--stop` після кожного): плаский 48/48, Spatial GUI 1.8 перша й третя особа
96/96, Iris + Complementary Reimagined r5.8.1 48/48. Меню паузи зі збиранням з'являється через 6 (`pop`) / 10
(`glass`) тіків, з вильотом `fly` — 4. Esc → перший кадр ефекту 12–35 мс (з шейдерами 17–27). Попереджень watchdog
немає. На старому коді той самий тест падає (3 випадки, див. вище).

## Ефекти поверх блоків (31.10.2026)

Було: уламки малювались у світовому проході (`COLLECT_SUBMITS` → `submitCustomGeometry`) з тестом глибини світу —
стіна чи підлога між камерою і точкою польоту обрізали ефект.

Зроблено (`"alwaysOnTop": true` за замовчуванням, редактор «Загальне → Відтворення → Малювати поверх блоків»):
- `render/OnTopPass`: геометрія будується там же, де й раніше (`ShardRenderer.submit` у `COLLECT_SUBMITS`, та сама
  камера й частка тіку), але замість колектора кладеться в список і малюється в `GameRendererMixin` на INVOKE
  `GuiRenderer#render` — після всього світу (напівпрозорі блоки, хмари, рука, композит шейдерпака) і до GUI, тож HUD
  і екрани зверху. Ваніль там щойно очистила глибину головного таргету (javap `GameRenderer#render`:
  `clearDepthTexture` → `Lighting.setupFor(ITEMS_3D)` → `FogRenderer.getBuffer(NONE)` → `GuiRenderer#render`), тож
  уламки тестують глибину лише між собою; квади з `sortOnUpload` сортуються від камери (`MeshData.sortQuads`,
  `DISTANCE_TO_ORIGIN` — вершини відносно камери), партії — від дальньої до ближчої, накладки schema 2 після уламків
  своєї партії. Після проходу глибина знову очищається.
- Матриці кадру: проєкція рівня з похитуванням — `@ModifyArg` на `ProjectionMatrixBuffer#getBuffer(Matrix4f)` у
  `GameRenderer#renderLevel`; поворот камери — 5-й аргумент `LevelRenderer#renderLevel` (= `viewRotationMatrix`).
  На час проходу: своя `ProjectionMatrixBuffer`, `backup/restoreProjectionMatrix`, модель-вид = поворот камери,
  туман `NONE`, освітлення `LEVEL` (для `entity_translucent` накладок), після — `ITEMS_3D`, як лишила ваніль.
- З шейдерпаком уламки йдуть нашим конвеєром (після композиту Iris нічого не перехоплює; «Missing program» у лозі
  немає) — без освітлення паку, як GUI, з якого вони вирізані. `alwaysOnTop: false` — старий шлях без змін (зокрема
  ванільні типи для Iris). Ванільні частинки лишаються у світі в обох режимах.
- Порядок у кадрі захоплення: прохід малюється до `ScreenCapture.beforeGuiRender` (резервна копія кадру вже з
  уламками).

Тест `OnTopScenario` (`-PtestOnly=ontop`): гравець під блоком, скриня перед ним; відкрити, поставити стіну 3×2 між
гравцем і скринею, закрити `fly` (20 тіків) — уламки летять крізь стіну; частка стіни з уламками на t12/15/18
порівняно з кадром після ефекту; `glass` — лише знімки. Прогони: плаский (on 0.85/0.50/0.26, off 0.30/0/0),
Spatial GUI 1.8 перша (on 0.07/0.04/0.02, off 0) і третя особа (on 0.32/0.24/0.10, off 0.15/0.04/0), Iris +
Complementary (on 0.07/0.03/0.02, off 0.02/0/0). На знімках HUD, приціл і хотбар — над уламками.

## Продуктивність: ліміт одночасних ефектів і якість (31.10.2026)

- Конфіг (верхній рівень, як `alwaysOnTop`): `effectQuality` (`low`/`medium`/`high`), `maxConcurrentEffects` (1–8),
  `concurrentPolicy` (`finishOldest`/`skipNew`). Старі конфіги без ключів: якість і ліміт — з пресету (`minimal` →
  `low`/1, `normal` → `medium`/2, `cinematic` → `high`/3), політика `finishOldest`; невідомі значення — з пресету /
  `finishOldest`, ліміт обрізається до 1–8. Перевіряє `EffectScenarios.configMigration`.
- `params/Quality`: множники кількості (частинки, уламки) переїхали з `Preset` у якість з тими самими числами
  (`low` = колишній `minimal` 0.35/0.5, `high` = колишній `cinematic` 1.5/1.5), тож старий конфіг з `minimal` має ті самі
  кількості. `Preset` лишив тривалість і задає якість та ліміт, коли його обирають у редакторі (`applyPreset`);
  `ParamValues.withPreset` (картки пресетів) бере якість пресету. `low`: без зіткнень (`ShardInstance.tick(..., collide)`,
  `DataWave.move/inside`), без шару сяйва (`ShardRenderer`) і без текстурних накладок schema 2 (`DecorRenderer`).
  Schema 2: `FxDef.Fracture.withDensity` змінює лише розбиття на багато однакових шматків (voronoi і grid за `count` ≥ 24,
  grid ≥ 24 клітинок, pixels); задумані розбиття (двері, жалюзі, смуги, радіальне) не чіпаються.
- Ліміт (`ShardManager.admit` / `admitAtSpawn`): ефект = хвиля, що грає не «на завершенні» (відкриття, закриття або
  лише її частинки/дим), плюс виліт `FlyOpen`; перегляди редактора не рахуються. Перевірка двічі: коли ефект
  вирішено запустити (`MinecraftMixin` перед `startClose`, `FlyController` для вильоту й збирання; захоплення, що чекає
  кадру, рахується як ефект) і коли хвиля справді стартує (два захоплення в одному кадрі, копія Spatial GUI).
  `finishOldest`: найстаріші `hurry(4)` — згасають за 0.2 с разом із частинками; `skipNew`: новий не запускається
  (закриття — екран просто закривається, Spatial GUI тоді грає своє закриття; відкриття — екран показується одразу).
  Жорстка межа (> 4 хвиль або > 8000 уламків → згасання за 0.3 с) лишилась.
- Тест `BudgetScenario` (`-PtestOnly=budget`): 20 відкриттів/закриттів інвентаря поспіль (відкриття `open:pop`, закриття
  glass і burn), обидві політики, ліміт 2 і 1. Пік рахує сам мод при кожному старті хвилі (`peakPlaying`) + щотіку
  тест: 2/2 і 1/1. `finishOldest`: усі 20 закриттів запущено, «завершувані» живуть ≤ 3 тіків; `skipNew`: 36–38
  пропусків, нічого не обрізано.
- Редактор: «Загальне → Продуктивність» — «Якість ефектів», «Ефектів одночасно», «Коли їх більше» (16 ключів × 12 мов).

## Посилання (31.10.2026)

- `gradle.properties`: `mod_modrinth`, `mod_curseforge` (+ `mod_sources`, `mod_issues`) → `fabric.mod.json`:
  `contact.homepage` = Modrinth, `sources`/`issues` = GitHub, `contact.modrinth`, `contact.curseforge`;
  `custom.modmenu.links` — `modmenu.modrinth`, `modmenu.curseforge` (Mod Menu 18.0.2 читає мапу «ключ перекладу → URL»
  через `CustomValueUtil.getStringMap("links")`, ці ключі є в його мовних файлах; Website/Source/Issues — з `contact`).
- «Про мод»: кнопки Modrinth, CurseForge, GitHub, «Повідомити про помилку» — URL з метаданих мода, відкриваються через
  ванільний `ConfirmLinkScreen.confirmLinkNow` (Esc / «Ні» повертає в налаштування). Старі ключі `about.open_link*`
  прибрано. `TooltipScenario` натискає кожну кнопку, перевіряє URL на екрані підтвердження, повернення й метадані.
- `profiling` тепер `false` за замовчуванням (тести вмикають його самі).

## Залежності для розробки з Modrinth Maven (31.10.2026)

`libs/` більше не потрібна: Spatial GUI 1.8/1.9, YACL 3.9.6, Iris 1.11.4, Sodium 0.9.2, Mod Menu 18.0.2 беруться з
`https://api.modrinth.com/maven` (`maven.modrinth:<проєкт>:<версія на Modrinth>`, `exclusiveContent`, `transitive =
false`). SHA-1 завантажених jar-ів збігається з колишніми файлами в `libs/` побайтово. Сторонні бінарники (Sodium —
PolyForm Shield, Iris/YACL — LGPL) у репозиторій не кладуться.

## Перевірка перед випуском 0.1.0-beta (31.10.2026)

Прогони по одному, `--stop` після кожного: `build`; повний `runClientGameTest` без Spatial GUI (13 хв 46 с), з
`-PspatialGui=1.8` (26 хв 49 с), з Iris + Complementary Reimagined r5.8.1 (16 хв 26 с); `crawler` без і зі Spatial GUI
(3237 дій, 0 збоїв UI); `lang` для 12 мов. Знайдено й виправлено два тести, що перестали встигати після «Швидшої
появи» (29–30.10): `FlyScenario` (виліт 4 тіки, знімок займає ~2 — тепер тест ставить 8 тіків і `openSpeed` 1) і
`ChooseScenario` (перегляд «Відкриття» спершу в паузі `HOLD` — тепер тест чекає фази `OPEN … ReversedSim`).
`LeakScenario` (`-PtestOnly=leak`, також у повному прогоні): 100 відкриттів/закриттів поспіль — у пулі не більше 8
текстур, після ефектів 2 (резервна + одна вільна), жодної хвилі.

Спостереження: у теці проєкту під OneDrive збереження стану редактора в `build/run/.../editor.json` інколи падає з
`AccessDeniedException` (OneDrive тримає файл під час атомарного перейменування) — лише попередження, тести не падають.

## Рука гравця над уламками (31.10.2026)

Скарга: уламки скла малюються поверх руки й хотбара. Хотбар уже був над ними (GUI малюється після проходу; уламки лише
просвічують крізь напівпрозорі слоти, як і світ). Рука — ні: `OnTopPass` малювався перед `GuiRenderer#render`, тобто
після `renderItemInHand`. Тепер прохід іде в `GameRenderer#renderLevel` на INVOKE `renderItemInHand` (javap: ваніль
саме перед ним очищає глибину головного таргету); запасний виклик перед GUI лишився для кадрів без руки (за кадр
малюється один раз). `renderItemInHand` не ставить освітлення сам, тож прохід зберігає й відновлює поточний буфер
освітлення (`RenderSystem.get/setShaderLights`), а не ставить `ITEMS_3D`.

Тест: `OnTopScenario` для glass порівнює ділянку руки на кадрах польоту з кадром після ефекту. Плаский і Spatial GUI
(1-ша/3-тя особа) — 0.000. **Відоме обмеження:** з шейдерпаком Iris малює руку у своєму конвеєрі й зводить її в кадр до
нашого проходу — уламки лишаються над рукою (0.304 ділянки руки); перевірка з Iris лише пише значення.
