run: comp
	mvn exec:java

comp:
	mvn compile

mvn:
	export M2_HOME=/home/spl211/Desktop/SPL/Assignment2/SPL_second_assignment/apache-maven-3.9.6-bin/apache-maven-3.9.6
	export MAVEN_HOME=/home/spl211/Desktop/SPL/Assignment2/SPL_second_assignment/apache-maven-3.9.6-bin/apache-maven-3.9.6
	
j:
	export PATH=${M2_HOME}/bin:${PATH}

clean:
	mvn clean