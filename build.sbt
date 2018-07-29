val server = (project in file("./server"))

val client = (project in file("./client"))

scalaVersion := "2.12.6"

addCommandAlias("sr", "server/run")

addCommandAlias("cr", "client/run")