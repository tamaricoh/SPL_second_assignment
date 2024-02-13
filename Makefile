run: comp
	mvn exec:java

comp:
	mvn compile

mvn:
	export M2_HOME=/home/spl211/Desktop/Set_Card_Game/apache-maven-3.9.6-bin/apache-maven-3.9.6
	export MAVEN_HOME=/home/spl211/Desktop/Set_Card_Game/apache-maven-3.9.6-bin/apache-maven-3.9.6
	export bin:${PATH}/PATH=${M2_HOME}

clean:
	mvn clean