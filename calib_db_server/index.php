<?php
// Connect
$dbconn = mysql_connect('localhost', 'birkler.se', 'vZst6JBB')
OR die(mysql_error());

$databasename = "birkler_se";
$tablename = "opencv_calibration_data";


mysql_select_db($databasename, $dbconn);

echo "Hellow world!!!!";

if (isset($_POST['data'])) {
		$jsondata=$_POST['data'];
		echo $_POST;
	
		$data = json_decode($jsondata);

		print_r($data);		
}

/*$sql="INSERT INTO $tablename (FirstName, LastName, Age)
VALUES
('$_POST[firstname]','$_POST[lastname]','$_POST[age]')";

if (!mysql_query($sql,$con))
{
	die('Error: ' . mysql_error());
}
echo "1 record added";
*/
// Query
//$query = sprintf("SELECT * FROM users WHERE user='%s' AND password='%s'",
//		mysql_real_escape_string($user),
//		mysql_real_escape_string($password));

mysql_close($dbconn);
?>

