<?php

namespace App\Handler;

use App\Attribute\Handler;
use App\Message\Bar;
use App\Message\Foo;

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

    // Без атрибута -> должен игнорироваться.
    public function notAHandler(Foo $foo): void {}
}
