package poc.lib

class BuildHandler implements Serializable {

def acepipeline
def appName

def getAcePipeline() {
 	return acepipeline
 }
 
void setAcePipeline(acepipeline) {
	this.acepipeline = acepipeline
}

def getAppName() {
 	return appName
 }
 
void setAppName(appName) {
	this.appName = appName
}

}
