package spark

import java.io._
import scala.collection.mutable.ArrayBuffer

sealed trait EventLogEntry
case class ExceptionEvent(exception: Throwable) extends EventLogEntry
case class RDDCreation(rdd: RDD[_], location: StackTraceElement) extends EventLogEntry
case class RDDChecksum(rdd: RDD[_], split: Split, checksum: Int) extends EventLogEntry

class EventLogWriter extends Logging {
  val eventLog =
    if (System.getProperty("spark.logging.eventLog") != null) {
      try {
        val file = new File(System.getProperty("spark.logging.eventLog"))
        if (!file.exists) {
          Some(new EventLogOutputStream(new FileOutputStream(file)))
        } else {
          logWarning("Event log %s already exists".format(System.getProperty("spark.logging.eventLog")))
          None
        }
      } catch {
        case e: FileNotFoundException =>
          logWarning("Can't write to %s: %s".format(System.getProperty("spark.logging.eventLog"), e))
          None
      }
    } else {
      None
    }

  def log(entry: EventLogEntry) {
    for (l <- eventLog)
      l.writeObject(entry)
  }
}

class EventLogOutputStream(out: OutputStream) extends ObjectOutputStream(out)

class EventLogInputStream(in: InputStream, val sc: SparkContext) extends ObjectInputStream(in)

class EventLogReader(sc: SparkContext) {
  val ois = new EventLogInputStream(new FileInputStream(System.getProperty("spark.logging.eventLog")), sc)
  val events = new ArrayBuffer[EventLogEntry]
  try {
    while (true) {
      events += (ois.readObject.asInstanceOf[EventLogEntry] match {
        case ExceptionEvent(exception) =>
          ExceptionEvent(exception)
        case RDDCreation(rdd, location) =>
          RDDCreation(rdd, location)
        case RDDChecksum(rdd, split, checksum) =>
          RDDChecksum(rdd, split, checksum)
      })
    }
  } catch {
    case e: EOFException => {}
  }
  ois.close()
  System.out.println()

  def rdds = events.collect { case RDDCreation(rdd, location) => rdd }
}