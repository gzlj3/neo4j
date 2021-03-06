/*
 * Copyright (c) 2002-2015 "Neo Technology,"
 * Network Engine for Objects in Lund AB [http://neotechnology.com]
 *
 * This file is part of Neo4j.
 *
 * Neo4j is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.neo4j.cypher.cucumber.db

import java.io.{File => JFile}

import org.neo4j.graphdb.factory.GraphDatabaseFactory

import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.reflect.io.File

case class ImportQuery(script: String, params: java.util.Map[String, Object])

object AvailableDatabase {
  final val archive = Map("cineast" -> importInto("src/test/resources/org/neo4j/cypher/db/cineast/"))
  final val dbPaths : mutable.Map[String, JFile] = new mutable.HashMap
  private final val SCRIPT_FILENAME = "import.cyp"
  private final val PARAMS_FILENAME = "params.json"

  private def importInto(path: String): ImportQuery = {
    val scriptFile = new JFile(path, SCRIPT_FILENAME)
    val paramsFile = new JFile(path, PARAMS_FILENAME)
    assert(scriptFile.exists())
    assert(paramsFile.exists())

    val script = File.apply(scriptFile).slurp()

    val contents = File.apply(paramsFile).slurp()
    val json = scala.util.parsing.json.JSON.parseFull(contents)
    val params = json match {
      case Some(map: Map[_,_]) => map.asInstanceOf[Map[String,AnyRef]].asJava
      case _ => throw new IllegalStateException(s"Unable to parse json file containing params at $paramsFile")
    }

    ImportQuery(script, params)
  }
}

case class DatabaseFactory(dbDir: String) extends ((String) => Unit) {
  override def apply(dbName: String): Unit = {
    val ImportQuery(script, params) = AvailableDatabase.archive(dbName)
    val dbPath = new JFile(dbDir, dbName)
    if (!dbPath.exists()) {
      val graph = new GraphDatabaseFactory().newEmbeddedDatabase(dbPath.getAbsolutePath)
      script.split(';').filter(_.trim.nonEmpty) foreach { q =>
        graph.execute(q.trim, params)
      }
      graph.shutdown()
    }
    AvailableDatabase.dbPaths += dbName -> dbPath
  }
}

object DatabaseLoader extends ((String) => JFile) {
  override def apply(dbName: String): JFile = AvailableDatabase.dbPaths(dbName)
}
