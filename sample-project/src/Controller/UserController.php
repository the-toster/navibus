<?php
namespace App\Controller;

use App\Message\CreateUser;
use App\Message\DeleteUser;

final class UserController
{
    public function run(): void
    {
        // на CreateUser слева должна быть gutter-иконка -> onCreate
        $a = new CreateUser('john');
        // несколько классов в одной строке: обе с обработчиками
        $this->dispatch(new CreateUser('x'), new DeleteUser());
    }

    private function dispatch(CreateUser $c, DeleteUser $d): void {}
}
