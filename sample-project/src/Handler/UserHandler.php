<?php
namespace App\Handler;

use App\Infrastructure\MessageBus\Autowire\Handler;
use App\Message\CreateUser;
use App\Message\DeleteUser;

final class UserHandler
{
    #[Handler]
    public function onCreate(CreateUser $message): void {}

    #[Handler]
    public function onDelete(DeleteUser $message): void {}

}
