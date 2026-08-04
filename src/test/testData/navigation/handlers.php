<?php

namespace App\Handler;

use App\Attribute\Handler;
use App\Message\Envelope;
use App\Message\Bar;
use App\Message\Foo;
use App\Message\Loose;
use App\Message\Trec;

class MyHandlers
{
    // Тип-хинт задан импортированным КОРОТКИМ именем (Foo, не FQN) —
    // ключевой кейс: плагин обязан резолвить его в \App\Message\Foo.
    #[Handler]
    public function onFoo(Foo $foo): void {}

    // Второй обработчик того же класса -> N>1.
    #[Handler]
    public function onFooAgain(Foo $foo): void {}

    #[Handler]
    public function onBar(Bar $bar): void {}

    // Обработчик есть, но Loose не подтип Envelope — проверка фильтра по типу.
    #[Handler]
    public function onLoose(Loose $loose): void {}

    // Обработчик есть; Trec использует трейт Marker — проверка строгости фильтра
    // (трейт не считается implements/extends).
    #[Handler]
    public function onTrec(Trec $trec): void {}

    // Без атрибута -> игнорируется в атрибутном режиме, но в режиме
    // «игнорировать атрибут» это public-метод, принимающий Foo -> цель навигации.
    public function notAHandler(Foo $foo): void {}

    // Принимает Foo, но private -> в режиме «игнорировать атрибут» НЕ должен попасть
    // в цели (только public). Негативный контроль public-only.
    private function onFooPrivate(Foo $foo): void {}

    // Public-метод, принимающий базовый интерфейс Envelope напрямую (без атрибута).
    // Нужен для проверки, что в ignore-режиме ссылки в extends/implements НЕ получают
    // маркер: без этого метода у Envelope не было бы принимающего метода, и баг с
    // маркером на `implements Envelope` не воспроизвёлся бы.
    public function onEnvelope(Envelope $e): void {}
}
