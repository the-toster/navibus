<?php
namespace App\Message;

final class CreateUser
{
    public function __construct(public string $name) {}
}
