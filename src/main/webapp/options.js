   
   // var serversJSON = '${descriptor.getServersJSONString()}';
//var jsonObj = JSON.parse(serversJSON); 
    
    //var serversStr = '${descriptor.getServersHTMLOptions()}';
    //document.getElementById('serverPerform').innerHTML = serversStr;
 


	function setOptions(serversJSONstr){

    var jsonObj = JSON.parse(serversJSONstr);
    var options = '';
    
    for(var i = 0; i<jsonObj.size; i++){
      options += '<option value="' + jsonObj.servers[i].server + '">' + jsonObj.servers[i].server + '</option>';
    }
    document.getElementById('serverPerform').innerHTML = options;
}