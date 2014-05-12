<?php
/*******************************************************************************************
*    Copyright © 2012-2013 Michael Felger                                                  *
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

// e.g. use with crontab: # */10 * * * * /usr/bin/php -c /etc/php5/apache2/php.ini -f /home/tempwww/htdocs/ip_refresh.php >> /dev/null

/*  config  */
// path to your log file
$logfilePath = "/home/tempwww/htdocs/ip_refresh.log";
// URL to public IP check. Be sure to comply with terms and conditions of the service (e.g. limit of requests per hour).
$checkIpUrl = "http://checkip.dyndns.com";
// Your DNS
$myDNS = "localhost";
// Your update URL, %s will be replaced with IP from public IP check.
// Don´t include user:pass into the URL because it may be visible in the log file 
$myUpdateURL = "https://127.0.0.1/update.php?newip=%s";
// Your update URL username & password for HTTP Auth
$myHTTPAuth = "user:pass";

/*  program  */
error_reporting(E_ALL);
ini_set('display_errors', 1);
function mylog($mode, $step="", $inf1="", $inf2="")
{
	global $logfilePath;
	$date = date("Y.m.d-H:i:s");
	$logstring = $date.';'.$mode.';'.$step.';'.$inf1.';'.$inf2."\n";
	echo $logstring."<br>";
	
	$handle=fopen($logfilePath,'a') or die("can't open log file !");
	fwrite($handle,$logstring);
	fclose($handle);
}

class myrequest
{
	public $r = null;
	public $responsecode = 0;
	public $responsebody = null;
	
	public $ok = 0;
	public $exception = null;
	
	function myrequest($url, $auth = false)
	{
		global $myHTTPAuth;
		try
		{
			if($auth === false )
			{
				$this->r = new HttpRequest($url, HttpRequest::METH_GET);
			}
			else
			{
				$options = array('httpauth' => $myHTTPAuth, 'httpauthtype' => HTTP_AUTH_BASIC);
				$this->r = new HttpRequest($url, HttpRequest::METH_GET, $options);
			}
			$this->r->addHeaders(array('User-Agent' => 'BETA PHP DNS Upadter v0.1'));
			$this->r->send();
			$this->responsecode = $this->r->getResponseCode();
			if($this->responsecode != 200)
			{
				$this->exception = 'Bad Response Code: '.$this->responsecode;
				return;
			}
			
			$this->responsebody = $this->r->getResponseBody();
			$this->ok = 1;
		}
		catch(HttpException $ex)
		{
			$this->ok = 0;
			$this->exception = $ex;
		}
	}
}

mylog("Start");

$pubiprequest = new myrequest($checkIpUrl);
$pubipmatch = array();
preg_match("/[\d]{1,3}.[\d]{1,3}.[\d]{1,3}.[\d]{1,3}/",$pubiprequest->responsebody, $pubipmatch);

if($pubiprequest->ok != 1 || !isset($pubipmatch[0]) || empty($pubipmatch[0]))
{
	mylog("ERROR","Get Pub IP failed");
	die();
}
$publicip = $pubipmatch[0];


$dyndnsiprecord = dns_get_record($myDNS, DNS_ALL);
if(empty($dyndnsiprecord[0]["ip"]))
{
	mylog("ERROR","Get DNS record IP failed");
	die("Get DNS record IP failed");
}
$dnsip = $dyndnsiprecord[0]["ip"];

if($dnsip != $publicip)
{
	mylog("Process","IPs are not the same",$dnsip,$publicip);
	$updateURL = sprintf($myUpdateURL,$publicip);
	mylog("Process","Start update");
	$updaterequest = new myrequest($updateURL, true);	
	if($updaterequest->ok == 1)
	{
		mylog("Process","update ok",$updaterequest->responsebody);
	}
	else
	{
		mylog("Process","failed update",$updaterequest->exception);
	}
}
else
{
	mylog("Process","IPs ok",$dnsip,$publicip);
}
mylog("End");
?>