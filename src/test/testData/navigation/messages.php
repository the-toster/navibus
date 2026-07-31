<?php

namespace App\Message;

interface Envelope {}

// Промежуточный интерфейс: Command -> Envelope. Нужен для проверки транзитивности
// фильтра (класс реализует Command, но Envelope — не напрямую).
interface Command extends Envelope {}

class Foo implements Command {}

class Bar implements Envelope
{
    // Явный конструктор: у new Bar() ClassReference.resolve() может вернуть __construct
    // (Method), а не PhpClass — регресс фильтра по типу.
    public function __construct(public string $id = "") {}
}

// Есть обработчик (onLoose), но НЕ подтип Envelope — негативный контроль фильтра.
class Loose {}

// Трейт + класс, который его использует. Контроль строгости фильтра: `use` — это
// не implements/extends, поэтому фильтр по \App\Message\Marker не должен ловить Trec.
trait Marker {}

class Trec
{
    use Marker;
}

class Plain {}
