<?php
namespace App\Message;

final class CreateUser implements MessageInterface
{
    public function __construct(public string $name) {}
}
