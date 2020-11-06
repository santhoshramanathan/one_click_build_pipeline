package vars
import poc.lib.BuildHandler

def call (appName) {
    node() {
    
      try {
          BuildHandler bHandler = new BuildHandler()
          bHandler.setAcePipeline(acepipeline)
          bHandler.setAppName(appName)
          workflow(bHandler)
          }
      catch (Exception e) {
        echo e.message
        throw e
         }
         
      finally {
       step([$class: 'WsCleanup', notFailBuild: true, deleteDirs: true])
       }
    }
    
}

