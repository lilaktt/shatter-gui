# Shatter GUI: правила для роботи з кодом

- У коді не писати коментарів: ні `//`, ні `/* */`, ні javadoc, ні закоментованого коду, ні розділювачів. Це стосується `.java`,
  `.gradle` і `.properties`. Пояснення пишуть у `docs/`, README, CHANGELOG і повідомленнях комітів.
  `./gradlew check` (і `build`, і `dist`) падає на `:core:checkNoComments`, якщо коментар з'явився.
- Jar-и для гравців брати лише з `dist/` (`./gradlew dist`). Тести гри запускаються з класів, не з jar-а.
- Одна збірка й один тест гри за раз, після кожного `./gradlew --stop`.
- `core` не бачить Minecraft і завантажувач (`:core:checkNoMinecraft`).
- Критерій проходу тесту гри: рядок `all checks passed` у лозі, а не `BUILD SUCCESSFUL`.
