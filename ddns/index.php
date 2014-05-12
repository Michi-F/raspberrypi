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

// Just print out the log file.

$logfilePath = "/home/tempwww/htdocs/ip_refresh.log";

$input = file($logfilePath);
$output = array_reverse($input);

foreach ($output as $line)
{
	echo nl2br($line);
}
?>