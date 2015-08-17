package ohnosequences.nisperon

import java.io.InputStream
import java.util

import com.amazonaws.services.s3.model.{CompleteMultipartUploadRequest, InitiateMultipartUploadRequest, PartETag, UploadPartRequest}
import ohnosequences.awstools.s3.{ObjectAddress, S3}


trait Uploader {
  type Context
  val initialContext: Context

  def start(): Context

  def addPart(context: Context, s: InputStream, size: Long, partNumber: Int): Context

  def finish(context: Context)
}

case class MultiPartUploadingContext(uploadId: String, eTags: List[PartETag]) {
  //todo test repeats

  def addETag(eTag: String, partNumber: Int) = {
    MultiPartUploadingContext(uploadId, new PartETag(partNumber, eTag) :: eTags)
  }

  def getETags(): java.util.List[PartETag] = {
    val res = new util.ArrayList[PartETag]()
    eTags.foreach(res.add)
    res
  }
}

class MultiPartUploader(s3: S3, objectAddress: ObjectAddress) extends Uploader {

  type Context = MultiPartUploadingContext
  val initialContext = MultiPartUploadingContext("", List[PartETag]())


  override def start(): MultiPartUploadingContext = {
    //initiate multipart
    val resp = s3.s3.initiateMultipartUpload(new InitiateMultipartUploadRequest(objectAddress.bucket, objectAddress.key))
    val id = resp.getUploadId
    MultiPartUploadingContext(id, List[PartETag]())
  }

  //todo switch to inputStream
  override def addPart(context: MultiPartUploadingContext, part: InputStream, size: Long, partNumber: Int): MultiPartUploadingContext = {
    //  val array = part.getBytes
    //val stream = new ByteArrayInputStream(array)
    println("upload part " + partNumber)
    val eTag = s3.s3.uploadPart(new UploadPartRequest()
      .withUploadId(context.uploadId)
      .withPartNumber(partNumber)
      .withInputStream(part)
      .withBucketName(objectAddress.bucket)
      .withKey(objectAddress.key)
      .withPartSize(size)
    ).getETag
    context.addETag(eTag, partNumber)
  }

  override def finish(context: Context) {
    //println("tags: " + context.eTags.map(_.getETag))
    s3.s3.completeMultipartUpload(
      new CompleteMultipartUploadRequest(objectAddress.bucket, objectAddress.key, context.uploadId, context.getETags())
    )
  }
}


