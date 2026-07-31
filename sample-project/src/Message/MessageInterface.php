<?php
namespace App\Message;

/**
 * Маркер-интерфейс сообщений. Для демонстрации фильтра «Message base type FQN»
 * (Settings | Tools | Navibus): задайте \App\Message\MessageInterface — маркеры
 * останутся только у сообщений-подтипов (CreateUser), но не у DeleteUser.
 */
interface MessageInterface {}
