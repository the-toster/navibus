<?php

namespace App\Client;

use App\Message\Bar;
use App\Message\Foo;

class MultiClient
{
    public function handle(<caret>Foo $foo, Bar $bar): void {}
}
