What is Uberifier?
It’s a tools to create standalone executable jar from Groovy script, which contains all dependencies within itself,
 
Why would anyone want it?
There are many reasons why one may want to run a Groovy script on a machine that has no internet connectivity.
 
Is it legal to package all the jar and distribute them?
Depends upon your circumstance and licenses associated with the libraries. I do not have any opinion about it.

How to run it?
java –jar Uberifier-1.0.0.jar /path_to_/MyGroovyScript.groovy and it will create the uber jar for your script in build/libs folder.
