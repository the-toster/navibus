# CLAUDE.md

Контекст проекта для Claude Code.

## Что это

Плагин **navibus** для **PhpStorm** — gutter-навигация от PHP-класса к его
**методам-обработчикам**: методам, которые помечены целевым атрибутом **и**
принимают объект этого класса параметром. Задумано для разных реализаций
message bus.

Иконка в gutter ставится:
- на **упоминании** класса (`ClassReference` — тип-хинт, `new`, и т.п.);
- на **определении** класса (`PhpClass`, на имени класса).

Переход ведёт к одному или нескольким обработчикам.

Учтено: целевой атрибут **настраиваемый** (project-level); атрибута может не быть
в проекте — плагин не падает; обработчиков от 0 до N; в одной строке может быть
несколько классов (у каждого свой маркер).

## Стек и версии

- Язык: **Kotlin 2.4.10** (под платформенный Kotlin 2.4.0).
- **IntelliJ Platform Gradle Plugin 2.18.1** (`org.jetbrains.intellij.platform`).
- Собираемся против **PhpStorm 2026.2.0.1** (`phpstorm("2026.2.0.1")`); PHP встроен,
  подключается как `bundledPlugin("com.jetbrains.php")`.
- **JDK 21** (`kotlin { jvmToolchain(21) }`), Gradle Wrapper (`./gradlew`).
- Диапазон совместимости — только **2026.2.x** (`sinceBuild = "262"`,
  `untilBuild = "262.*"`).
- `instrumentCode = false` и `buildSearchableOptions = false` (плагин чисто на
  Kotlin, форм/Java нет; одно поле настроек не стоит headless-индексации).

Все нетривиальные причины этих версий/флагов — в памяти проекта (см.
`memory/`), в частности почему нужен IPGP ≥ 2.18.1.

## Структура кода

Пакет `com.github.thetoster.navibus`.

- `HandlerLineMarkerProvider` (`RelatedItemLineMarkerProvider`) — вешает
  gutter-иконку. Регистрируется в `plugin.xml` как
  `codeInsight.lineMarkerProvider` для языка PHP. Работает на leaf-элементе:
  распознаёт последний идентификатор `ClassReference` и имя `PhpClass`, берёт FQN,
  спрашивает обработчиков, строит маркер через `NavigationGutterIconBuilder`.
- `HandlerMethodSearch` (project `@Service`) — поиск обработчиков. Строит карту
  «тип параметра → методы» и кэширует её через `CachedValuesManager`
  (инвалидация по `PsiModificationTracker.MODIFICATION_COUNT` и по изменению
  настроек). Кандидаты берутся **прямым запросом к `PhpAttributeIndex`**
  PHP-плагина (использования атрибута по FQN) — без полного обхода классов
  проекта. Тип параметра резолвится через `PhpType.global(project)` (иначе
  импортированное короткое имя не совпадёт с FQN из `ClassReference`).
- `settings/NaviBusSettings` — project-level `PersistentStateComponent`
  (хранит FQN атрибута; дефолт `\App\Infrastructure\MessageBus\Autowire\Handler`;
  нормализует FQN). Является `SimpleModificationTracker` для инвалидации кэша.
- `settings/NaviBusConfigurable` — страница **Settings | Tools | Navibus**
  (Kotlin UI DSL). В `apply()` перезапускает анализатор, чтобы иконки
  пересчитались при смене FQN.

## Ключевые API PHP-плагина

- `ClassReference.fqn` — FQN упоминаемого класса; `PhpClass.fqn` /
  `nameIdentifier` — для определения класса.
- `PhpAttributeIndex` (`StubIndex<String, PhpAttribute>`) — использования атрибута
  по FQN; ключи в нижнем регистре с ведущим `\`. `PhpAttribute.getOwner()` →
  метод/класс/параметр. (NB: `PhpAttributesFQNsIndex` индексирует *объявление*
  атрибута, не использования — не то.)
- `Method.getAttributes(fqn)`, `Method.parameters`,
  `Parameter.declaredType` (`PhpType`), `PhpType.global(project)`.

## Gradle-задачи

```bash
./gradlew build         # компиляция + тесты
./gradlew test          # юнит-тесты (BasePlatformTestCase)
./gradlew runIde        # запуск PhpStorm-песочницы с плагином
./gradlew verifyPlugin  # Plugin Verifier против PhpStorm 2026.2.0.1
./gradlew buildPlugin   # .zip в build/distributions/
```

`verifyPlugin` настроен на `PhpStorm 2026.2.0.1` (блок `pluginVerification`).

## Тестирование

- База: `BasePlatformTestCase`; фикстуры `.php` в `src/test/testData/navigation/`.
- `HandlerLineMarkerTest` покрывает: резолв импортированного короткого имени
  (дискриминатор), 0/1/N обработчиков, несколько классов в строке, иконку на
  определении класса, отсутствие атрибута (без падений), регистронезависимость.
- `NaviBusSettingsTest` — дефолт, персист+trim, построение панели.

## Демо

`sample-project/` — маленький PHP-проект с дефолтным атрибутом для ручной
проверки в `runIde` (открыть как проект, смотреть иконки в `UserController`/на
определениях классов-сообщений).
