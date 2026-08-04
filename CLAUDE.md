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

**Фильтр классов-сообщений** (project-level, опционально) — **набор правил** с
семантикой **OR**: класс-сообщение получает маркер, если выполнено **любое** из
заданных правил.

- **Правило по типу**: FQN базового интерфейса/класса — класс проходит, если его
  `implements`/`extends`. Строгая семантика: транзитивно обходятся **только
  родительские классы и интерфейсы**; трейты (`use`) и `@mixin` не считаются (это не
  extends/implements). Сам базовый тип тоже проходит.
- **Правило по атрибутам**: список FQN атрибутов — класс проходит, если помечен любым
  из них **на самом объявлении класса** (атрибуты в PHP не наследуются — иерархию для
  этого правила не обходим).

Оба правила пустые — фильтр выключен (маркер у любого класса с обработчиками).
Обработчики по-прежнему ищутся по атрибуту обработчика; фильтр — дополнительное
условие на сам класс-сообщение. Если базового типа/атрибута нет в проекте — по этому
правилу ни один класс не пройдёт (аналогично «атрибута может не быть»).

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
  Если включён фильтр класса-сообщения (`isMessageFilterActive()`) — **после** того,
  как обработчики найдены, проверяет `isMessageClass(fqn)` по уже известному FQN
  (только под этим условием, чтобы не дёргать индекс на каждый leaf).
- `HandlerMethodSearch` (project `@Service`) — поиск обработчиков. Строит карту
  «тип параметра → методы» и кэширует её через `CachedValuesManager`
  (инвалидация по `PsiModificationTracker.MODIFICATION_COUNT` и по изменению
  настроек). Кандидаты берутся **прямым запросом к `PhpAttributeIndex`**
  PHP-плагина (использования атрибута по FQN) — без полного обхода классов
  проекта. Тип параметра резолвится через `PhpType.global(project)` (иначе
  импортированное короткое имя не совпадёт с FQN из `ClassReference`).
  `isMessageClass(fqn: String)` — фильтр класса-сообщения (набор правил, OR). Класс
  резолвится по FQN через `PhpIndex.getAnyByFQN` (**не** `ClassReference.resolve()`:
  у `new Foo()` резолв ссылки вернёт `__construct`, а не класс — из-за этого маркеры
  пропадали на упоминаниях классов с конструктором). Для каждого резолвнутого
  `PhpClass`: **правило по атрибутам** — `phpClass.attributes` сравниваются (по
  нормализованному FQN) с настроенным множеством, без обхода иерархии (атрибуты не
  наследуются); **правило по типу** — `PhpClassHierarchyUtils.processSuperClasses` +
  `processSuperInterfaces` (транзитивно) сравнивают FQN супертипов с базовым (по
  нормализованной строке, без резолва базового типа). Строго extends/implements —
  **без** трейтов и `@mixin` (два узких вызова, а не общий `processSupers`, который
  тянет трейты/миксины). Сам базовый FQN тоже проходит (аналог processSelf). Первое
  сработавшее правило → `true`. Оба правила пустые → `true` (фильтр выключен, ранний
  возврат — база-пусто больше **не** короткозамыкает в `true`, когда заданы атрибуты).
  Не кэшируется — читает настройку «вживую», вызывается после отсечения по обработчикам.
- `settings/NaviBusSettings` — project-level `PersistentStateComponent`
  (хранит FQN атрибута обработчика; дефолт `\App\Infrastructure\MessageBus\Autowire\Handler`;
  `messageBaseFqn` — FQN базового типа сообщений, дефолт пусто; и
  `messageAttributeFqns` — список FQN атрибутов-маркеров класса (`@XCollection`),
  дефолт пусто; сеттер списка trim-ит, отбрасывает пустые и дубли; оба пустых = фильтр
  выкл.; нормализует FQN). Является `SimpleModificationTracker` для инвалидации кэша.
- `settings/NaviBusConfigurable` — страница **Settings | Tools | Navibus**
  (Kotlin UI DSL): поля «Handler attribute FQN», «Message base type FQN» и text area
  «Message attribute FQNs» (по одному FQN на строку; адаптер text↔list идемпотентен —
  склейка через `\n`, пустые строки отбрасываются). В `apply()` перезапускает
  анализатор, чтобы иконки пересчитались при смене настроек.

## Ключевые API PHP-плагина

- `ClassReference.fqn` — FQN упоминаемого класса; `PhpClass.fqn` /
  `nameIdentifier` — для определения класса.
- `PhpAttributeIndex` (`StubIndex<String, PhpAttribute>`) — использования атрибута
  по FQN; ключи в нижнем регистре с ведущим `\`. `PhpAttribute.getOwner()` →
  метод/класс/параметр. (NB: `PhpAttributesFQNsIndex` индексирует *объявление*
  атрибута, не использования — не то.)
- `Method.getAttributes(fqn)`, `Method.parameters`,
  `Parameter.declaredType` (`PhpType`), `PhpType.global(project)`.
- `PhpClass.attributes` (`PhpAttributesOwner.getAttributes()`) — атрибуты **на самом
  классе**; `PhpAttribute.getFQN()` резолвит короткое импортированное имя (`use`) в
  полный FQN. Используется правилом фильтра «атрибут на классе-сообщении».
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
  на упоминаниях; **фильтр по атрибуту**: правило по `#[AsMessage]` в одиночку
  (проходит только помеченный `Loose`, короткое имя из `use` резолвится в FQN) и
  **OR-комбинация** (база `Envelope` + атрибут `AsMessage`: `Foo`/`Bar` по подтипу,
  `Loose` по атрибуту, `Trec` выпадает). Смена настройки не меняет PSI — в тестах
  фильтра нужен `DaemonCodeAnalyzer.restart(reason)` перед `doHighlighting()`;
  `messageBaseFqn` и `messageAttributeFqns` сбрасываются в `setUp` (light-проект
  переиспользуется). Отдельный тест на строгость: `Trec use Marker` (трейт) не
  проходит фильтр по `Marker`. Фикстура `messages.php` содержит
  `Envelope`/`Command`/`Foo`/`Bar`/`Loose`/`Marker`/`Trec` (Loose — негативный
  контроль по подтипу, но помечен `#[AsMessage]` — позитивный по атрибуту;
  Trec+Marker — контроль исключения трейтов). Атрибут `AsMessage` объявлен в
  `attribute.php`.
- `NaviBusSettingsTest` — дефолт (пустые `messageBaseFqn` и `messageAttributeFqns`),
  персист+trim FQN, персист списка атрибутов (trim/дедуп/отбрасывание пустых),
  round-trip адаптера text↔list в `NaviBusConfigurable` (`apply()` идемпотентен —
  `isModified == false` после), построение панели.

## Демо

`sample-project/` — маленький PHP-проект с дефолтным атрибутом для ручной
проверки в `runIde` (открыть как проект, смотреть иконки в `UserController`/на
определениях классов-сообщений). Фильтр — набор правил (Settings | Tools | Navibus):
- **По типу**: `CreateUser implements Message`, `DeleteUser` — нет; задайте
  «Message base type FQN» = `\App\Message\Message` — маркер останется только у
  `CreateUser`.
- **По атрибуту**: `DeleteUser` помечен `#[AsMessage]` (`\App\Message\AsMessage`).
  Добавьте этот FQN в «Message attribute FQNs» — `DeleteUser` вернёт маркер по
  правилу-атрибуту (семантика OR с правилом по типу).
