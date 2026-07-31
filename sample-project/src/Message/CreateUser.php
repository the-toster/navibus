<?php

namespace App\Message;

final class CreateUser implements Message
{
    public function __construct(public string $name)
    {
    }
}
