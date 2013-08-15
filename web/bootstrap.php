<?php

require 'vendor/autoload.php';
require_once("rdb/rdb.php");

$db = array(
    'host' => 'localhost',
    'db' => ''
);

$_conn;
// Connect to localhost
/**
 * @return \r\Connection
 */
function rql_connect()
{

    global $db, $_conn;
    if (isset($_conn)) return $_conn;
    $conn = r\connect($db['host']);
    $conn->useDb($db['db']);
    return $_conn = $conn;
}

function current_leaders($amount)
{

    $conn = rql_connect();
    r\table("profiles")->eqJoin("id", r\table("stats"))->zip()->limit($amount)->run($conn);

}
