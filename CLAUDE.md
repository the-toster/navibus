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

**Фильтр классов-сообщений по типу** (project-level, опционально): можно задать FQN
базового интерфейса/класса — тогда маркер ставится только у классов, которые его
`implements`/`extends`. Строгая семантика: транзитивно обходятся **только
родительские классы и интерфейсы**; трейты (`use`) и `@mixin` не считаются (это не
extends/implements). Пусто — фильтр выключен (маркер у любого класса с обработчиками).
Обработчики по-прежнему ищутся по атрибуту; фильтр — дополнительное условие на сам
класс-сообщение. Если базового типа нет в проекте — ни один класс не пройдёт
(аналогично «атрибута может не быть»).

## Стек и версии

- Язык: **Kotlin 2.4.10** (под платформенный Kotlin 2.4.0).
- **IntelliJ Platform Gradle Plugin 2.18.1** (`org.jetbrains.intellij.platform`).
- Собираемся против **PhpStorm 2026.2.0.1** (`phpstorm("2026.2.0.1")`); PHP встроен,
  подключается как `bundledPlugin("com.jetbrains.php")`.
- **JDK 21** (`kotlin { jvmToolchain(21) }`), Gradle Wrapper (`./gradlew`).
- Диапазон совместимости — **2026.2.x–2026.3.x** (`sinceBuild = "262"`,
  `untilBuild = "263.*"`). Верхнюю границу расширили вперёд на ветку 263; когда
  выйдет 2026.3 (EAP/релиз) — добавить её в `pluginVerification.ides` и прогнать
  `verifyPlugin`, чтобы убедиться, что внутренние API PHP-плагина не сломались.
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
  Если включён фильтр по типу (`isMessageFilterActive()`) — **после** того, как
  обработчики найдены, проверяет `isMessageClass(fqn)` по уже известному FQN
  (только под этим условием, чтобы не дёргать индекс на каждый leaf).
- `HandlerMethodSearch` (project `@Service`) — поиск обработчиков. Строит карту
  «тип параметра → методы» и кэширует её через `CachedValuesManager`
  (инвалидация по `PsiModificationTracker.MODIFICATION_COUNT` и по изменению
  настроек). Кандидаты берутся **прямым запросом к `PhpAttributeIndex`**
  PHP-плагина (использования атрибута по FQN) — без полного обхода классов
  проекта. Тип параметра резолвится через `PhpType.global(project)` (иначе
  импортированное короткое имя не совпадёт с FQN из `ClassReference`).
  `isMessageClass(fqn: String)` — фильтр по типу. Класс резолвится по FQN через
  `PhpIndex.getAnyByFQN` (**не** `ClassReference.resolve()`: у `new Foo()` резолв
  ссылки вернёт `__construct`, а не класс — из-за этого маркеры пропадали на
  упоминаниях классов с конструктором). Затем `PhpClassHierarchyUtils.processSuperClasses`
  + `processSuperInterfaces` (транзитивно) сравнивают FQN супертипов с базовым (по
  нормализованной строке, без резолва базового типа). Строго extends/implements —
  **без** трейтов и `@mixin` (два узких вызова, а не общий `processSupers`, который
  тянет трейты/миксины). Сам базовый FQN тоже проходит (аналог processSelf). Не
  кэшируется — читает настройку «вживую», вызывается после отсечения по обработчикам.
- `settings/NaviBusSettings` — project-level `PersistentStateComponent`
  (хранит FQN атрибута; дефолт `\App\Infrastructure\MessageBus\Autowire\Handler`;
  и `messageBaseFqn` — FQN базового типа сообщений, дефолт пусто = фильтр выкл.;
  нормализует FQN). Является `SimpleModificationTracker` для инвалидации кэша.
- `settings/NaviBusConfigurable` — страница **Settings | Tools | Navibus**
  (Kotlin UI DSL): поля «Handler attribute FQN» и «Message base type FQN».
  В `apply()` перезапускает анализатор, чтобы иконки пересчитались при смене FQN.

## Ключевые API PHP-плагина

- `ClassReference.fqn` — FQN упоминаемого класса; `PhpClass.fqn` /
  `nameIdentifier` — для определения класса.
- `PhpAttributeIndex` (`StubIndex<String, PhpAttribute>`) — использования атрибута
  по FQN; ключи в нижнем регистре с ведущим `\`. `PhpAttribute.getOwner()` →
  метод/класс/параметр. (NB: `PhpAttributesFQNsIndex` индексирует *объявление*
  атрибута, не использования — не то.)
- `Method.getAttributes(fqn)`, `Method.parameters`,
  `Parameter.declaredType` (`PhpType`), `PhpType.global(project)`.
- `PhpClassHierarchyUtils.processSuperClasses` / `processSuperInterfaces`
  `(clazz, processSelf, allowAmbiguity, Processor)` — транзитивный обход
  родительских классов / интерфейсов; используются фильтром классов-сообщений
  (строго extends/implements). `processSupers` — тот же обход, но с трейтами и
  `@mixin`; нам не нужен. `PhpClass.fqn` даёт FQN с ведущим `\`.

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
  определении класса, отсутствие атрибута (без падений), регистронезависимость,
  **фильтр по типу**: пара выкл→вкл (подтип сохраняет маркер, не-подтип с
  обработчиком его теряет), транзитивность (`Foo`→`Command`→`Envelope`), фильтр
  на упоминаниях. Смена настройки не меняет PSI — в тестах фильтра нужен
  `DaemonCodeAnalyzer.restart(reason)` перед `doHighlighting()`; `messageBaseFqn`
  сбрасывается в `setUp` (light-проект переиспользуется). Отдельный тест на строгость:
  `Trec use Marker` (трейт) не проходит фильтр по `Marker`. Фикстура `messages.php`
  содержит `Envelope`/`Command`/`Foo`/`Bar`/`Loose`/`Marker`/`Trec` (Loose — негативный
  контроль по подтипу; Trec+Marker — контроль исключения трейтов).
- `NaviBusSettingsTest` — дефолт (в т.ч. пустой `messageBaseFqn`), персист+trim
  обоих FQN, построение панели.

## Демо

`sample-project/` — маленький PHP-проект с дефолтным атрибутом для ручной
проверки в `runIde` (открыть как проект, смотреть иконки в `UserController`/на
определениях классов-сообщений). Для фильтра по типу: `CreateUser implements
MessageInterface`, `DeleteUser` — нет; задайте в Settings | Tools | Navibus поле
«Message base type FQN» = `\App\Message\MessageInterface` — маркер останется
только у `CreateUser`.
