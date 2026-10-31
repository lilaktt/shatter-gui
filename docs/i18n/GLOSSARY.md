# Translation glossary

The words the mod uses again and again, and how each language says them. Keep to these so that the same thing has
the same name in every screen. `en_us.json` is the source of truth: a new key is written there first, and every other
file must get the same key (the unit test `LangFilesTest` checks keys and `%s`/`%d` placeholders).

English and Ukrainian are written by hand. The other ten languages are machine-assisted and need review by native
speakers; if a word below sounds wrong in your language, change it here **and** in the language file in the same pull
request.

## Rules

- Short and plain: labels are buttons and tabs, often only 60–120 px wide at GUI scale 4.
- Use the words below; do not mix synonyms (e.g. de "Teil" everywhere, not "Teil" here and "Stück" there).
- Keep `%s`, `%d`, `%1$s` exactly as in English; the order of numbered ones may change.
- Do not translate: `.shatterfx`, `JSON`, `Spatial GUI`, `Iris`, `K`, file names, effect ids.
- Units stay short: `bl`, `bl/s` (ja `bl/秒`, zh `格/秒`, ko `블록/초`, ru/uk `бл/с`).
- Check long labels in game: `./gradlew runClientGameTest -PtestOnly=lang -Plang=<code>` fails if a translated
  label is cut with "…" at GUI scales 1–4.
- Descriptions (`….desc`) are one short sentence ending with a full stop (CJK: 。).

## Terms

| English | Meaning | uk_ua | de_de | es_es | fr_fr | pt_br | pl_pl | zh_cn | ru_ru | ja_jp | ko_kr | it_it |
|---|---|---|---|---|---|---|---|---|---|---|---|---|
| effect | a whole open/close animation | ефект | Effekt | efecto | effet | efeito | efekt | 效果 | эффект | エフェクト | 효과 | effetto |
| piece | one part a screen is cut into | уламок | Teil | pieza | morceau | pedaço | kawałek | 碎块 | осколок | 破片 | 조각 | pezzo |
| shard | a piece of the glass effect, also "Most shards" | скалка / уламок | Splitter | fragmento | éclat | caco | odłamek | 碎块 | осколок | 破片 | 조각 | pezzo |
| layer | one step of an effect in the editor | шар | Ebene | capa | calque | camada | warstwa | 层 | слой | レイヤー | 레이어 | livello |
| module | what a layer does (spin, fade, launch…) | модуль | Modul | módulo | module | módulo | moduł | 模块 | модуль | モジュール | 모듈 | modulo |
| target | which pieces a layer acts on | ціль | Ziel | objetivo | cible | alvo | cel | 目标 | цель | ターゲット | 대상 | bersaglio |
| trigger | the order in which pieces start (wave, from a point, noise) | фронт | Reihenfolge | orden | ordre | ordem | kolejność | 顺序 | фронт | 開始順 | 시작 순서 | fronte |
| fracture | how the screen is cut (grid, strips, radial) | розбиття | Zerbrechen | rotura | fracture | quebra | pękanie | 碎裂 | разлом | 割れ方 | 깨짐 | rottura |
| timeline | the time strip at the bottom of the editor | таймлайн | Zeitleiste | línea de tiempo | frise | linha do tempo | oś czasu | 时间轴 | таймлайн | タイムライン | 타임라인 | timeline |
| inspector | the right panel with the fields | інспектор | Inspektor | inspector | inspecteur | inspetor | inspektor | 检查器 | инспектор | インスペクター | 인스펙터 | ispettore |
| preview | playing the effect without closing the editor | перегляд | Vorschau | vista previa | aperçu | prévia | podgląd | 预览 | предпросмотр | プレビュー | 미리보기 | anteprima |
| emitter | a source of particles | емітер | Emitter | emisor | émetteur | emissor | emiter | 发射器 | эмиттер | エミッター | 방출기 | emettitore |
| particle | a small dot or sprite flying out | частинка | Partikel | partícula | particule | partícula | cząsteczka | 粒子 | частица | パーティクル | 입자 | particella |
| sprite | a small picture used by particles | спрайт | Sprite | sprite | sprite | sprite | sprite | 精灵 | спрайт | スプライト | 스프라이트 | sprite |
| texture | a picture drawn on pieces | текстура | Textur | textura | texture | textura | tekstura | 纹理 | текстура | テクスチャ | 텍스처 | texture |
| overlay | a texture drawn over the screen | накладка | Überlagerung | superposición | superposition | sobreposição | nakładka | 叠加层 | наложение | オーバーレイ | 오버레이 | sovrapposizione |
| mask | hides part of a piece | маска | Maske | máscara | masque | máscara | maska | 遮罩 | маска | マスク | 마스크 | maschera |
| curve | a value that changes over time | крива | Kurve | curva | courbe | curva | krzywa | 曲线 | кривая | カーブ | 곡선 | curva |
| keyframe | a point of a curve | ключовий кадр | Schlüsselbild | fotograma clave | image clé | quadro-chave | klatka kluczowa | 关键帧 | ключевой кадр | キーフレーム | 키프레임 | fotogramma chiave |
| duration | how long an effect plays | тривалість | Dauer | duración | durée | duração | czas trwania | 时长 | длительность | 長さ | 길이 | durata |
| spread | random scatter of a value | розкид | Streuung | dispersión | dispersion | dispersão | rozrzut | 散布 | разброс | ばらつき | 흩어짐 | dispersione |
| sound | a sound played by an effect | звук | Klang | sonido | son | som | dźwięk | 声音 | звук | 音 | 소리 | suono |
| template | a ready layer set to start from | шаблон | Vorlage | plantilla | modèle | modelo | szablon | 模板 | шаблон | テンプレート | 템플릿 | modello |
| package | a `.shatterfx` file with an effect and its textures | пакунок | Paket | paquete | paquet | pacote | paczka | 包 | пакет | パッケージ | 패키지 | pacchetto |
| reduce motion | setting: calmer, shorter effects | менше руху | Weniger Bewegung | reducir movimiento | réduire les mouvements | reduzir movimento | mniej ruchu | 减少动态效果 | меньше движения | 動きを減らす | 움직임 줄이기 | riduci movimento |
| thematic mode | setting: each screen gets its own fitting effect | тематичний режим | thematischer Modus | modo temático | mode thématique | modo temático | tryb tematyczny | 主题模式 | тематический режим | テーマモード | 테마 모드 | modalità a tema |

## Sending a correction

Open an issue or a pull request on the mod's source page with the changed `src/main/resources/assets/shatter_gui/lang/<code>.json`
(and this file, if a term changes). Run `./gradlew test` before sending: it fails if a key is missing, extra, or has
different placeholders than English.
