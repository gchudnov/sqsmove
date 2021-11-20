package com.github.gchudnov.sqsmove.sqs

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider
import software.amazon.awssdk.http.async.SdkAsyncHttpClient
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient
import software.amazon.awssdk.services.sqs.SqsAsyncClient
import software.amazon.awssdk.services.sqs.model.*

import java.util.List as JList
import scala.collection.immutable.IndexedSeq
import scala.jdk.CollectionConverters.*

object AwsSqs:
  type ReceiptHandle = String

  private val receiveAllAttributeNames: JList[String] = List("All").asJava
  private[sqs] val maxBatchSize: Int                  = 10
  private[sqs] val waitBatchMillis: Long              = 1000
  private val receiveWaitTimeSeconds: Int             = 20

  def makeHttpClient(maxConcurrency: Int): SdkAsyncHttpClient =
    NettyNioAsyncHttpClient.builder().maxConcurrency(maxConcurrency).build()

  def makeSqsClient(httpClient: SdkAsyncHttpClient): SqsAsyncClient =
    SqsAsyncClient
      .builder()
      .credentialsProvider(DefaultCredentialsProvider.create())
      .httpClient(httpClient)
      .build()

  def makeReceiveRequest(queueUrl: String, visibilityTimeoutSec: Long, batchSize: Int): ReceiveMessageRequest =
    ReceiveMessageRequest
      .builder()
      .queueUrl(queueUrl)
      .attributeNamesWithStrings(receiveAllAttributeNames)
      .messageAttributeNames(receiveAllAttributeNames)
      .maxNumberOfMessages(batchSize)
      .waitTimeSeconds(receiveWaitTimeSeconds)
      .visibilityTimeout(visibilityTimeoutSec.toInt)
      .build()

  def toBatchRequestEntry(m: Message, id: Int): SendMessageBatchRequestEntry =
    SendMessageBatchRequestEntry
      .builder()
      .id(id.toString)
      .messageBody(m.body())
      .messageAttributes(m.messageAttributes())
      .messageGroupId(m.attributes().getOrDefault(MessageSystemAttributeName.MESSAGE_GROUP_ID, null))
      .messageDeduplicationId(m.attributes().getOrDefault(MessageSystemAttributeName.MESSAGE_DEDUPLICATION_ID, null))
      .build()

  def toBatchRequest(queueUrl: String, entries: IndexedSeq[SendMessageBatchRequestEntry]): SendMessageBatchRequest =
    SendMessageBatchRequest
      .builder()
      .queueUrl(queueUrl)
      .entries(entries.asJava)
      .build()

  def toDeleteRequestEntry(h: ReceiptHandle, id: Int): DeleteMessageBatchRequestEntry =
    DeleteMessageBatchRequestEntry
      .builder()
      .id(id.toString)
      .receiptHandle(h)
      .build()

  def toDeleteRequest(queueUrl: String, entries: IndexedSeq[DeleteMessageBatchRequestEntry]): DeleteMessageBatchRequest =
    DeleteMessageBatchRequest
      .builder()
      .queueUrl(queueUrl)
      .entries(entries.asJava)
      .build()
