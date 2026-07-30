<?php

namespace App\Client;

use App\Message\Bar;

class SingleClient
{
    public function run(): void
    {
        new <caret>Bar();
    }
}
