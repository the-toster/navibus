# CLAUDE.md

Контекст проекта для Claude Code. Проект собран из шаблона
[intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template)
и пока пустой (только скелет).

## Задача

Плагин для **PhpStorm**, реализующий навигацию между:

- **упоминанием класса** в коде (`ClassReference` — например, тип-хинт,
  `new`, использование имени класса), и
- **методом-обработчиком**, который принимает объект этого класса параметром
  **и** помечен целевым атрибутом.

Целевой атрибут (замени на реальный из проекта пользователя):
`\App\Attribute\Handler`

Надо учесть:
- целевой атрибут должен быть настраиваемый
- этого атрибута может не быть в проекте, плагин не должен падать от этого
- обработчиков может быть от 0 до N,
- в одной строке мжет быть несколько классов



## Стек и требования

- Язык плагина: **Kotlin**
- **IntelliJ Platform Gradle Plugin 2.x** (`org.jetbrains.intellij.platform`)
- **JDK 21** (требование платформы 2024.2+ / 2026.x)
- **Gradle Wrapper** 8.5+ (использовать `./gradlew`, не системный gradle)
- Зависимость от PHP-плагина: `depends com.jetbrains.php`
- Для сборки нужен **IntelliJ IDEA Ultimate** SDK (PHP-плагина нет в Community)
- Версию PHP-плагина брать точно под версию IDE с JetBrains Marketplace

## Пример build.gradle.kts (зависимости)

```kotlin
plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.0.0"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        intellijIdeaUltimate("2024.2")
        plugin("com.jetbrains.php:242.20224.155") // подобрать под версию IDE
        instrumentationTools()
        testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
    }
    testImplementation("junit:junit:4.13.2")
}
```

## plugin.xml (ключевое)

```xml
<idea-plugin>
    <id>com.example.attr-nav</id>
    <name>Attribute Navigation</name>
    <depends>com.jetbrains.php</depends>

    <extensions defaultExtensionNs="com.intellij">
        <codeInsight.lineMarkerProvider
            language="PHP"
            implementationClass="com.example.AttributeLineMarker"/>
    </extensions>
</idea-plugin>
```

## Черновик реализации (LineMarkerProvider, gutter-иконки)

Идея: на leaf-элементе идентификатора внутри `ClassReference` берём FQN класса,
ищем методы с целевым атрибутом, у которых есть параметр этого типа, и вешаем
иконку-переход через `NavigationGutterIconBuilder`.

```kotlin
package com.example

import com.intellij.codeInsight.daemon.LineMarkerInfo
import com.intellij.codeInsight.daemon.LineMarkerProvider
import com.intellij.codeInsight.navigation.NavigationGutterIconBuilder
import com.intellij.icons.AllIcons
import com.intellij.psi.PsiElement
import com.jetbrains.php.PhpIndex
import com.jetbrains.php.lang.psi.elements.ClassReference
import com.jetbrains.php.lang.psi.elements.Method
import com.jetbrains.php.lang.psi.elements.PhpClass

private const val TARGET_ATTRIBUTE = "\\App\\Attribute\\Handler"

class AttributeLineMarker : LineMarkerProvider {

    override fun getLineMarkerInfo(element: PsiElement): LineMarkerInfo<*>? {
        if (element.firstChild != null) return null // только leaf

        val classRef = element.parent as? ClassReference ?: return null
        val fqn = classRef.fqn ?: return null

        val methods = findHandlerMethods(element.project, fqn)
        if (methods.isEmpty()) return null

        return NavigationGutterIconBuilder.create(AllIcons.Gutter.ImplementedMethod)
            .setTargets(methods)
            .setTooltipText("Перейти к обработчику")
            .createLineMarkerInfo(element)
    }

    private fun findHandlerMethods(
        project: com.intellij.openapi.project.Project,
        classFqn: String
    ): List<Method> {
        val phpIndex = PhpIndex.getInstance(project)
        val result = mutableListOf<Method>()
        for (name in phpIndex.getAllClassFqns(null)) {
            for (cls in phpIndex.getClassesByFQN(name)) {
                collectMatchingMethods(cls, classFqn, result)
            }
        }
        return result
    }

    private fun collectMatchingMethods(
        cls: PhpClass, classFqn: String, out: MutableList<Method>
    ) {
        for (method in cls.methods) {
            val hasAttr = method.attributes.any { it.fqn == TARGET_ATTRIBUTE }
            if (!hasAttr) continue
            val accepts = method.parameters.any { p ->
                p.declaredType.types.any { it == classFqn }
            }
            if (accepts) out.add(method)
        }
    }
}
```

## Ключевые API PHP-плагина

- `ClassReference.fqn` — FQN упоминаемого класса
- `PhpClass.methods`, `Method.parameters`, `Method.attributes`
- `PhpAttribute.fqn` — FQN атрибута
- `Parameter.declaredType` (`PhpType`) — объявленный тип параметра
- `PhpIndex` — индекс классов/методов; для обратного поиска — `ReferencesSearch`

## ВАЖНО: производительность

Наивный обход `getAllClassFqns` на каждый gutter-элемент недопустимо медленный.
В рабочей версии нужно:

- кэшировать карту «класс-параметр → методы» через `CachedValuesManager` +
  `PsiModificationTracker`;
- сузить поиск через собственный `FileBasedIndex` / `StubIndex`, индексирующий
  только классы с целевым атрибутом;
- проверять `attributes` до дорогих операций.

## Основные Gradle-задачи

```bash
./gradlew build         # компиляция + сборка
./gradlew runIde        # запуск IDE-песочницы с плагином
./gradlew test          # юнит-тесты (BasePlatformTestCase)
./gradlew verifyPlugin  # Plugin Verifier — проверка совместимости
./gradlew buildPlugin   # .zip в build/distributions/
```

## Тестирование

- База: `BasePlatformTestCase` из test-фреймворка платформы
- Фикстуры `.php` кладём в `src/test/testData/`
- Проверка gutter: `myFixture.findGuttersAtCaret()`
- Проверка перехода: `myFixture.performEditorAction(...)`

## Что нужно сделать дальше (roadmap)

1. Прописать зависимости в `build.gradle.kts` под нужную версию IDE.
2. Уточнить реальный FQN целевого атрибута.
3. Реализовать `AttributeLineMarker` (прямая навигация) + кэш.
4. Добавить обратный `LineMarkerProvider` от метода к использованиям.
5. Заменить полный обход индекса на `FileBasedIndex`/`StubIndex`.
6. Написать тесты навигации в обе стороны.
