<?php

namespace App\Client;

use App\Message\Plain;

class NoneClient
{
    public function run(): void
    {
        new <caret>Plain();
    }
}
