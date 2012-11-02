<?php
ob_start();
error_reporting(E_STRICT);
ini_set('display_errors','On');

// Connect
$connect = true;
if ($connect) {
	$dbconn = mysql_connect('localhost', 'birkler_se', 'vZst6JBB') OR die(mysql_error());
	$databasename = "birkler_se";
	$tablename = "opencv_calibration_data";
	mysql_select_db($databasename, $dbconn);
}


function mysql_fetch_all($result) 
{
	while($row=mysql_fetch_array($result,MYSQL_ASSOC)) {
		$return[] = $row;
	}
	return $return;
}

function addStringChild($xmlResult,$calib,$name) {
	$xmlResult->addChild($name,'"'.$calib[$name].'"');
}



//var_dump($_SERVER);
//var_dump($_REQUEST);

if ($_SERVER['REQUEST_METHOD'] == "GET" && isset($_REQUEST['get'])) {
	$cameraname = $_REQUEST['get'];
	$query = sprintf("SELECT * FROM %s WHERE name = '%s'",$tablename,mysql_real_escape_string($cameraname));
	$queryresult = mysql_query($query);
	if ($queryresult) {
		$result = mysql_fetch_all($queryresult);
		if (count($result) > 0) {
			$xmlDoc = new SimpleXMLElement("<opencv_storage></opencv_storage>");
			$xmlCalibrations=$xmlDoc->addChild("calibrations");
			foreach ($result as $calib) {
				$xmlResult=$xmlCalibrations->addChild("_");
				addStringChild($xmlResult,$calib,"id");
				addStringChild($xmlResult,$calib,"name");
				addStringChild($xmlResult,$calib,"capturedate");
				addStringChild($xmlResult,$calib,"dev_serial");
				addStringChild($xmlResult,$calib,"programname");
				addStringChild($xmlResult,$calib,"user");
				$widthheight = preg_split('/x/',$calib["resolution"]);
				$xmlResult->addChild("width",$widthheight[0]);
				$xmlResult->addChild("height",$widthheight[1]);
				$xmlResult->addChild("focallength",$calib["focallength"]);
				$child = $xmlResult->addChild("camera_matrix");
				$child->addAttribute("type_id","opencv-matrix");
				$child->rows=3;
				$child->cols=3;
				$child->dt='d';
				$child->addChild("data",trim($calib["K"]));
				
				$child = $xmlResult->addChild("distortion_coefficients");
				$child->addAttribute("type_id","opencv-matrix");
				$distarray = preg_split('/\s+/',trim($calib["dist"]));
				$child->rows=count($distarray);
				$child->cols=1;
				$child->dt='d';
				$child->addChild("data",$calib["dist"]);
				
			}
			header('Content-Type: text/xml');
			echo $xmlDoc->asXML();
		}
		else {
			header(':', true, 404);
		}
	}
	else {
		http_response_code(404);
		echo "Not found";
	}
}
else if (($_SERVER['REQUEST_METHOD'] == "POST" || $_SERVER['REQUEST_METHOD'] == "PUT")&& isset($_REQUEST['add'])) {
	$data = file_get_contents("php://input");
	
	if ($_SERVER['CONTENT_TYPE'] == "text/xml") {
		$xmldata = simplexml_load_string($data);
		var_dump($xmldata);
		$programname = str_replace('"', "", $xmldata->program);
		$user = str_replace('"', "", $xmldata->user);
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
		$programname = $jsondata["program"];
		$user = $jsondata["user"];
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
	if (empty($cameraname)) { 	http_response_code(403); echo("No camera name");  }
	if (empty($resolution)) {http_response_code(403); echo("No resolution"); }
	$nowdatestring = date ("Y-m-d H:i:s",time());
	
	echo 'INSERTING!!!';
	$from_ip = $_SERVER['REMOTE_ADDR'];
	
	$query = sprintf("INSERT INTO %s (name, resolution, focallength, dev_serial, timeadded, capturedate,K,dist, data, program, user, from_ip) VALUES ('%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s','%s')",
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
			mysql_real_escape_string($programname),
			mysql_real_escape_string($user),
			mysql_real_escape_string($from_ip));
	
	htmlentities(print_r($query));
		 
	
	if (!mysql_query($query,$dbconn))
	{
		http_response_code(403);
		echo('Error: ' . mysql_error());
	}
	echo "1 record added";
} else {
	http_response_code(403);
	echo "Nothing here to GET here.";
}


mysql_close($dbconn);
?>

