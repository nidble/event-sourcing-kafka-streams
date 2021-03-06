package org.amitayh.invoices.common

import org.apache.kafka.clients.admin.NewTopic
import org.apache.kafka.common.config.TopicConfig

import scala.collection.JavaConverters._
import scala.concurrent.duration._

object Config {
  val BootstrapServers = sys.env("BOOTSTRAP_SERVERS")

  object Stores {
    val Snapshots = "invoices.store.snapshots"
  }

  object Topics {
    sealed trait CleanupPolicy
    object CleanupPolicy {
      case object Compact extends CleanupPolicy
    }

    case class Topic(name: String,
                     numPartitions: Int = 4,
                     replicationFactor: Short = 1,
                     retention: Option[Duration] = None,
                     cleanupPolicy: Option[CleanupPolicy] = None) {

      def toNewTopic: NewTopic = {
        val emptyConfigs = Map.empty[String, String]
        val withRetention = retentionConfig.foldLeft(emptyConfigs)(_ + _)
        val withCleanupPolicy = cleanupPolicyConfig.foldLeft(withRetention)(_ + _)
        new NewTopic(name, numPartitions, replicationFactor)
          .configs(withCleanupPolicy.asJava)
      }

      private def retentionConfig: Option[(String, String)] = retention.map { retention =>
        val millis = if (retention.isFinite) retention.toMillis else -1
        TopicConfig.RETENTION_MS_CONFIG -> millis.toString
      }

      private def cleanupPolicyConfig: Option[(String, String)] = cleanupPolicy.map {
        case CleanupPolicy.Compact =>
          TopicConfig.CLEANUP_POLICY_CONFIG ->
            TopicConfig.CLEANUP_POLICY_COMPACT
      }

    }

    val Events = Topic("invoices.topic.events", retention = Some(Duration.Inf))
    val Commands = Topic("invoices.topic.commands", retention = Some(5.minutes))
    val CommandResults = Topic("invoices.topic.command-results", retention = Some(5.minutes))
    val Snapshots = Topic("invoices.topic.snapshots", cleanupPolicy = Some(CleanupPolicy.Compact))
    val All = Set(Events, Commands, CommandResults, Snapshots)
  }
}
