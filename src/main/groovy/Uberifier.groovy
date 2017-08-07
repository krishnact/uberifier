/**
 @Grapes(
 [
 @GrabResolver(name='jitpack', root='https://jitpack.io'),
 @GrabResolver(name='gradle', root='https://repo.gradle.org/gradle/libs-releases-local'),
 @Grab('com.github.krishnact:commandlinetool-base:0.4.1'),
 @Grab('org.slf4j:slf4j-log4j12:1.7.7'),
 @Grab('org.himalay:gradle-wrapper:0.0.1')
 ]
 )
 */
import org.himalay.commandline.Option
import org.himalay.commandline.CLTBase
import org.himalay.commandline.CLTBaseQuiet
import org.himalay.commandline.RealMain


import groovy.util.OptionAccessor

/**
 * This tool creates uber jar for a groovy script
 * usage:
 * Uberifier MyScript.groovy
 * Caveats:
 * Grape components must be one one each line. They will be read even if they in / * * / comment block as long as there is no non-space character before them.
 * @author krishna
 *
 */
//@RealMain
class Uberifier extends CLTBaseQuiet{

	//@Option
	String srcFolder='src/main/groovy/'

	@Option(description="Target Java version")
	String javaVersion = "1.6"

	@Option(description="Source Java version")
	String javaSourceVersion   = "1.7"
	@Option(description="Target Java version")
	String javaTargetVersion= "1.6"

	@Option(description="Source Groovy version")
	String groovySourceVersion = "1.6"
	@Option(description="Target Groovy version")
	String groovyTargetVersion="1.6"

	@Option(numberOfOptions= 0,description="Clean previous build")
	boolean clean = false

	@Option(numberOfOptions= 0,description="Don't build uber jar")
	boolean nojar = false

	@Option(description="Version number of the created uberjar")
	String version ="1.0.0"
	
	String GRADLE_CONF_SIGNATURE = "e73476c2e4f9d03092c1d10b7e1a7f62120818adcaf2da8668d27b7eed985876"
	/**
	 * build.gradle template
	 */
	public static String template ='''apply plugin: 'groovy'
apply plugin: 'application'

mainClassName = "${mainclass}"
version = '${version}'
archivesBaseName = "${mainclass}"

repositories {
    mavenCentral()
    ${repos.collect{'maven { url '+ quote + it+ quote+ brace_e}.join( nl )}
    mavenLocal()
}


dependencies {

    // To use the Groovy library that ships with Gradle
    compile localGroovy()
    
    // To use a regular Groovy dependency
    //compile 'org.codehaus.groovy:groovy-all:2.3.6'
    
    // Add External libs here
    ${depend_compile.collect{'compile '+ quote + it+ quote}.join( nl )}
    compile 'commons-cli:commons-cli:1.3.1'
    compile 'org.apache.commons:commons-lang3:3.5'
    // Add local GROOVY_HOME libs here
    /*
    def GROOVY_HOME = new File(System.getenv('GROOVY_HOME'))
    if (!GROOVY_HOME.canRead()) {
        println "Missing environment variable GROOVY_HOME: '${DOLLAR}{GROOVY_HOME}'" 
    } else {
        compile fileTree( dir: GROOVY_HOME.getAbsolutePath(), include: ['lib/commons*.jar'] )
    }
    */
	/* Creator signature */
	/* ${GRADLE_CONF_SIGNATURE}*/
}

task wrapper(type: Wrapper) {
    gradleVersion = '2.0'
}
tasks.withType(JavaCompile) {
    sourceCompatibility = ${javaSourceVersion}
    targetCompatibility = ${javaTargetVersion}
}
tasks.withType(GroovyCompile) {
    sourceCompatibility = ${groovySourceVersion}
    targetCompatibility = '${groovyTargetVersion}'
}
// Use this task to generate the stand-alone excutable jar of groovy
task uberjar( type: Jar, dependsOn: [':compileJava',':compileGroovy'] ) {
    manifest {
        attributes 'Main-Class': mainClassName
    }
    
    // Our groovy classes
    from files(sourceSets.main.output.classesDir)
    // All groovy classes
    from configurations.runtime.asFileTree.files.collect { zipTree(it) }
}
'''

String  propsText='''
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
distributionUrl=https\\://services.gradle.org/distributions/gradle-3.4.1-all.zip
''' ;


	@Override
	protected void realMain(OptionAccessor options) {
		def temp = new groovy.text.SimpleTemplateEngine().createTemplate(template)
		String groovyFilePath= options.arguments()[0]
		String gradleConfig = "build.gradle"
		if (groovyFilePath == null){
			warn( "No args specified.")
			this.cliBuilder.usage()
			System.exit(-10)
		}
		File groovyFile = new File(groovyFilePath)
		if (! groovyFile.exists()){
			warn( "File ${groovyFile.absolutePath} does not exit.")
			System.exit(-11)
		}
		def repos = []
		def depends=[]
		def package_=""
		def mainClass=groovyFile.name.split('\\.')[0]
		boolean classFileStarted = false
		groovyFile.eachLine {String aLine->
			if ( classFileStarted == false){
				switch (aLine){
					case ~$/.*@GrabResolver.*/$:
						def match =  aLine =~/.*'(.*)'.*/
						repos << match[0][1]
						break;
					case ~$/.*@Grab.*/$:
						def match =  aLine =~/.*'(.*)'.*/
						depends << match[0][1]
						break;
					case ~$/[\s]*package .*/$:
						def match =  aLine =~/[\s]*package[\s]+(.*)[\s]*/
						package_=  match[0][1]
						mainClass= package_ +'.'+mainClass
						break;
					case ~$/.*class\s+.*/$:
						classFileStarted = true
						break;
				}
			}
		}


		String destFile =""
		if ( package_ != null) {
			destFile = "${srcFolder}/${package_.replace('.','/')}/${groovyFile.name}"
		}else{
			destFile = "${srcFolder}/${groovyFile.name}"
		}
		copySrcFile(groovyFile, new File(destFile))

		String text = temp.make([
			javaSourceVersion   : javaSourceVersion   ,
			javaTargetVersion   : javaTargetVersion   ,
			groovySourceVersion : groovySourceVersion ,
			groovyTargetVersion : groovyTargetVersion ,
			GRADLE_CONF_SIGNATURE:GRADLE_CONF_SIGNATURE,
			version             : version             ,
			DOLLAR:'$',
			mainclass:mainClass,
			package_:package_,
			repos:repos,
			brace_e: '}',
			depend_compile:depends,
			quote :"'",
			nl:"\n"]
		).toString()

		File gradleConfFile = new File(gradleConfig)
		if ( gradleConfFile.exists() && gradleConfFile.text.contains(GRADLE_CONF_SIGNATURE))
		{
			warn ("The gradle conf file ${} was not created by this tool. Please save it because we want to overwrite it.")
			System.exit(-14)
		}else{
			gradleConfFile.text = text
		}

		//println "execute gradlew.bat"
		String actions = ""
		 
		if (nojar == false) actions = 'uberjar'
		if (clean == true) actions = 'clean ' + actions
		String[] gradleOptions =  actions.trim().split("\\s+") as String[]
		try{
			File wrapperJar = wrapperJar()
			File props = new File(
				wrapperJar.getParent(), 
				wrapperJar.getName().replaceFirst("\\.(jar|class|groovy)\$", ".properties")
				)		
		props.text = propsText
		info "After build, check the jar file in build/libs folder."
		org.gradle.wrapper.GradleWrapperMain.main(gradleOptions)
				
		}catch(FileNotFoundException ex){
			
		}
		
	}

	private static File wrapperJar() {
		URI location;
		try {
			location = Uberifier.class.getProtectionDomain().getCodeSource().getLocation().toURI();
		} catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		if (!(location.getScheme().equals("file"))) {
			throw new RuntimeException( "Cannot determine classpath for wrapper Jar from codebase ${location}.")
		}
		return new File(location.getPath());
	}
	
	
	void clean(String srcFolder) {
		try{

			info ("Cleaning src folder")
			new File(srcFolder).listFiles().each{ it.delete(); }
		}catch (Exception ex)
		{
			ex.printStackTrace()
		}
	}

	void copySrcFile(File srcFile, File destFile) {
		info "Creating uberjar for ${destFile.absolutePath}"
		if ( destFile != srcFile){
			clean(srcFolder);
			destFile.parentFile.mkdirs();
			destFile.text = srcFile.text
		}
	}
	//D:\clients\cox\tools\groovy-executable-jar-with-gradle-example-master\src\main\groovy\org\himalay\general\tools\MassSNMP.groovy
	public static void main(String [] args)
	{
		CLTBase._main(new Uberifier(), args);
	}

	@Override
	protected int verifyOptions(OptionAccessor options)
	{

		if ( options.arguments().size() == 0){
			info ("No arguments specified")
			this.cliBuilder_.usage()
			return -12
		}
		return 0;

	}
}

// You need to install gradle-wrapper file as maven dependency to create a uber jar of this groovy file.
// 1.
// Use following command to install gradle-wrapper.jar to your local maven repository.
// mvn install:install-file -Dfile=lib/gradle-wrapper.jar -DgroupId=org.himalay -DartifactId=gradle-wrapper -Dversion=0.0.1 -Dpackaging=jar -DgeneratePom=true
// 2.
// After installling copy gradle-wrapper.properties to the same folder where 
// copy lib\gradle-wrapper.properties C:\Users\krishna\.m2\repository\org\himalay\gradle-wrapper\0.0.1\gradle-wrapper-0.0.1.properties 
// 3. Now you can run it like this:
// groovy src\main\groovy\Uberifier.groovy src\main\groovy\Uberifier.groovy 
// It will create build\libs\Uberifier-1.0.0.jar which can be executed like:
// java -jar build\libs\Uberifier-1.0.0.jar

