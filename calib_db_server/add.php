<?php
error_reporting(E_ALL);
ini_set('display_errors','On');

// Connect
$connect = true;
if ($connect) {
	$dbconn = mysql_connect('localhost', 'birkler_se', 'vZst6JBB') OR die(mysql_error());
	$databasename = "birkler_se";
	$tablename = "opencv_calibration_data";
	mysql_select_db($databasename, $dbconn);
}


var_dump($_SERVER);

if ($_SERVER['REQUEST_METHOD'] == "POST") {
	$data = file_get_contents("php://input");
	
	if ($_SERVER['CONTENT_TYPE'] == "text/xml") {
		$xmldata = simplexml_load_string($data);
		var_dump($xmldata);
		$programname = str_replace('"', "", $xmldata->program);
		$cameraname = str_replace('"', "", $xmldata->camera_name);
		$datetimeCapture = date ("Y-m-d H:i:s",strtotime(str_replace('"', "", $xmldata->capture_time)));
		$devideid = $cameraname;
		$focallength = str_replace('"', "", $xmldata->reported_focal_length);
		$resolution = $xmldata->image_width . "x" . $xmldata->image_height;
		$Kmat = $xmldata->camera_matrix->data;
		$K = join(" ", preg_split('/\s+/',$Kmat ));
		$Distmat = $xmldata->distortion_coefficients->data;
		$dist = join(" ", preg_split('/\s+/',$Distmat ));
	} 
	else if ($_SERVER['CONTENT_TYPE'] == "application/json") {
		$jsondata = json_decode($data,true);
		
		//print_r($jsondata);
		$resolution = $jsondata["data"]["width"] . "x" . $jsondata["data"]["height"];
		$focallength = $jsondata["focallength"];
		$cameraname = $jsondata["name"];
		$devideid = $jsondata["deviceId"];
		$datetimeCapture = date ("Y-m-d H:i:s",strtotime($jsondata["captureTime"]));
		$resolution = $jsondata["data"]["width"] . "x" . $jsondata["data"]["height"];
		$Kmat = $jsondata["data"]["K"];
		$K = join(" ", preg_split('/\s+/',$Kmat ));
		$Distmat = $jsondata["data"]["kdist"];
		$dist = join(" ", preg_split('/\s+/',$Distmat ));
	}
	if (empty($cameraname)) { echo("No camera name"); }
	if (empty($resolution)) {echo("No resolution"); }
	$nowdatestring = date ("Y-m-d H:i:s",time());
	$query = sprintf("INSERT INTO %s (name, resolution, focallength, dev_serial, timeadded, capturedate,K,dist, data, program) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s')",
			$tablename,
			mysql_real_escape_string($cameraname),
			mysql_real_escape_string($resolution),
			mysql_real_escape_string($focallength),
			mysql_real_escape_string($devideid),
			mysql_real_escape_string($nowdatestring),
			mysql_real_escape_string($datetimeCapture),
			mysql_real_escape_string($K),
			mysql_real_escape_string($dist),
			mysql_real_escape_string($data),
			mysql_real_escape_string($programname));
	
	htmlentities(print_r($query));
		 
	
	if (!mysql_query($query,$dbconn))
	{
		die('Error: ' . mysql_error());
	}
	echo "1 record added";
} else {
	echo "Nothing here to GET.";
}


mysql_close($dbconn);
?>

