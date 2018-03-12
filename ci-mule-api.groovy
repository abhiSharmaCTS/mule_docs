def gitlab_Url = "http://hsyplsvns001.amwater.net:81/mulesoft-integrations"
def gitlab_credentialsId = "6e426155-40c6-4c99-97fe-247f84ebf361"
def gitlab_apiUrl = "${gitlab_Url}" + "/aw-template-api.git"
def gitlab_branch = "master"

def artifactoryServerUrl = "http://10.1.68.239:8081/artifactory"
def artifactoryCredentialId = "mulesvc Artifactory Credential"

def localJenkinM2Repo = "/home/jenkins-slave/.m2/repository/com/amwater/mule/api"
def cdTargetEnvironment = "Development"

timestamps {
    node("qa"){
        stage "Checkout from Gitlab"
        cleanWs()
        sh "rm -r ${localJenkinM2Repo}"
        git branch: "${gitlab_branch}", 
            credentialsId: "${gitlab_credentialsId}", 
            url: "${gitlab_apiUrl}"
    
        stage "Build"

        def artifactoryDetails = [url: "${artifactoryServerUrl}", 
                          credentialsId: "${artifactoryCredentialId}"]

        def artifactoryServer = Artifactory.newServer(artifactoryDetails)

        def rtMaven = Artifactory.newMavenBuild()
        rtMaven.deployer server:artifactoryServer, 
            releaseRepo:'libs-release-local', snapshotRepo:'libs-snapshot-local'
        rtMaven.deployer.deployArtifacts = false
        
        def buildInfo = Artifactory.newBuildInfo()
        rtMaven.run pom: 'pom.xml', goals: 'clean install', buildInfo: buildInfo
        
        stage "Publish Artifacts"
        rtMaven.deployer.deployArtifacts buildInfo
        artifactoryServer.publishBuildInfo buildInfo
        
        archiveArtifacts 'target/*.zip'
        
        stage "Continuous Deployment"
        def pomFileContent = readFile('pom.xml')

        build job: 'aw-mule-cd-pipeline', parameters: [string(name: 'muleApiName', value: getArtifactId(pomFileContent)), string(name: 'muleApiArtifactsNameLoc', value: prepareArtifactsLocation(pomFileContent)), string(name: 'targetEnvironment', value: "${cdTargetEnvironment}")]
    }
}

@NonCPS
def prepareArtifactsLocation(pomFileContent){

    def groupId = getGroupId(pomFileContent)
    def groupIdLoc = groupId.replaceAll('\\.' , '/')
    def artifactId = getArtifactId(pomFileContent)
    def artifactsVersion = getArtifactsVersion(pomFileContent)
    artifactsVersion = artifactsVersion.replace('${BUILD_NUMBER}', '')
    artifactsVersion = artifactsVersion + "${BUILD_NUMBER}"
    
    println 'Artifacts= ' +groupId+ "::" +artifactId+ "::" +artifactsVersion

    def awArtifactLocation = groupIdLoc+ "/" +artifactId+ "/" +artifactsVersion+ "/" +artifactId+ "-" +artifactsVersion+ ".zip"
    println("awArtifactLocation = " + awArtifactLocation)
    awArtifactLocation
}

@NonCPS
def getGroupId(pomFileContent) {
  def matcher = pomFileContent =~ '<groupId>(.+)</groupId>'
  matcher ? matcher[0][1] : null
}

@NonCPS
def getArtifactId(pomFileContent) {
  def matcher = pomFileContent =~ '<artifactId>(.+)</artifactId>'
  matcher ? matcher[0][1] : null
}

@NonCPS
def getArtifactsVersion(pomFileContent) {
  def matcher = pomFileContent =~ '<version>(.+)</version>'
  matcher ? matcher[0][1] : null
}
