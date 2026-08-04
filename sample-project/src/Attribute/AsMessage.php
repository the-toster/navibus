<?php
namespace App\Message;

// Атрибут-маркер класса-сообщения: альтернатива базовому интерфейсу Message.
// Демонстрирует правило фильтра по атрибуту (Settings | Tools | Navibus).
#[\Attribute(\Attribute::TARGET_CLASS)]
final class AsMessage {}
