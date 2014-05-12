<?php
/*******************************************************************************************
*    Copyright © 2012-2014 Michael Felger                                                  *
*                                                                                          *
*    This program is free software: you can redistribute it and/or modify                  *
*    it under the terms of the GNU General Public License Version 3 as published by        *
*    the Free Software Foundation.                                                         *
*                                                                                          *
*    This program is distributed in the hope that it will be useful,                       *
*    but WITHOUT ANY WARRANTY; without even the implied warranty of                        *
*    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the                         *
*    GNU General Public License Version 3 for more details.                                *
*                                                                                          *
*    You should have received a copy of the GNU General Public License Version 3           *
*    along with this program.  If not, see <http://www.gnu.org/licenses/gpl-3.0/>.         *
********************************************************************************************/

$mymac = "AA-BB-CC-DD-EE-FF";
$myIpToWakeUp = "127.0.0.1";

if(isset($_GET["host"]) && $_GET["host"] == '1')
{
	wakeup($mymac, $myIpToWakeUp);
	echo "Paket zum aufwecken gesendet<br><br>\n\n";
}

function wakeup ($mac, $ip) {

    if (!$fp = fsockopen('udp://' . $ip, 2304, $errno, $errstr, 2))
	{
		return false;
	}

    $mac_hex = preg_replace('=[^a-f0-9]=i', '', $mac);

    $macbin = pack('H12', $mac_hex);

    $res = str_repeat("\xFF", 6) . str_repeat($macbin, 16);

    fputs($fp, $res);
    fclose($fp);
    return true;
}
?>

<a href="http://felger.dyndns.org/wol/index.php?host=1">Home-PC aufwecken</a>