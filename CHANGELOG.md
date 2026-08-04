<!-- Keep a Changelog guide -> https://keepachangelog.com -->

# navibus Changelog

## [Unreleased]

### Added

- Message class filter by attribute: a new **Message attribute FQNs** field (one FQN
  per line) on the **Settings | Tools | Navibus** page. A class is treated as a message
  if it is annotated with any of these attributes on the class declaration itself.

### Changed

- The message class filter is now a **set of rules combined with OR**: a class gets the
  gutter icon if it matches the **Message base type FQN** (subtype rule) **or** the new
  **Message attribute FQNs** (attribute rule). Both fields empty disables the filter and
  keeps the previous behavior.

## [0.0.3] - 2026-07-31

### Added

- Bus icon
- Message class type filter: a new **Message base type FQN** field on the
  **Settings | Tools | Navibus** page. When an interface/class FQN is set, the gutter
  icon is shown only for classes that `implement`/`extend` it (transitively, strictly —
  parent classes and interfaces only; traits and `@mixin` are not considered). An empty
  field disables the filter and keeps the previous behavior.

## [0.0.2]

### Fixed

- The icon on a handler's own parameter type hint no longer navigates "to itself":
  such a target is filtered out, and if there are no other handlers, the icon is not
  shown at all.

## [0.0.1]

### Added

- Gutter navigation from a PHP class to its handler methods (marked with the target
  attribute and accepting an instance of the class as a parameter). The icon is placed
  both on a class reference and on its definition; navigation leads to 0..N handlers.
- Configurable target attribute FQN on the **Settings | Tools | Navibus** page.

### Changed

- Extended the IDE compatibility range to **2026.2.x–2026.3.x**
  (`untilBuild = "263.*"`).

[Unreleased]: https://github.com/the-toster/navibus/compare/v0.0.3...HEAD
[0.0.3]: https://github.com/the-toster/navibus/compare/v0.0.2...v0.0.3
[0.0.2]: https://github.com/the-toster/navibus/compare/v0.0.1...v0.0.2
[0.0.1]: https://github.com/the-toster/navibus/commits/v0.0.1
