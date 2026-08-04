<?php
namespace App\Message;

// Не реализует Message, но помечен атрибутом-маркером — проходит фильтр по правилу
// «атрибут на классе», а не по базовому типу.
#[AsMessage]
final class DeleteUser {}
