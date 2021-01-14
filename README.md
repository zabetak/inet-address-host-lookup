# Concurrent InetAddress hostname resolution

The program simulates a real multi-threaded application that performs hostname resolution using the `InetAddress#getByName(String)` method. Its primary purpose is to help diagnose a native memory leak appearing in an old version of Hiveserver2 that seems to be related with address resolution.

## Build
`mvn clean install`

## Run

    java -jar target/inet-address-host-lookup-1.0-SNAPSHOT-jar-with-dependencies.jar -poolSize 10 -maxSize 100 -host localhost -host www.google.com

For more configuration options:

    java -jar target/inet-address-host-lookup-1.0-SNAPSHOT-jar-with-dependencies.jar
