package com.weihu.video.h264video

import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaFormat
import android.os.Environment
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ArrayBlockingQueue

/**
 * created by hupihuai on 2019/1/9
 */
class MediaCodeUtil {
    private lateinit var mediaCodec: MediaCodec
    private val VCODEC_MIME = "video/avc" // H264的MIME
    private var height: Int = 0
    private var width: Int = 0
    private var isRuning = false
    private lateinit var configByte: ByteArray
    private lateinit var outputStream: BufferedOutputStream

    private var yuv420Queue = ArrayBlockingQueue<ByteArray>(10)

    fun init(width: Int, height: Int, frameRate: Int) {

        if (!::mediaCodec.isInitialized) {
            val bitrate = 2 * width * height
            this.width = width
            this.height = height

            val mediaCodecInfo = selectCodec(VCODEC_MIME)
            mediaCodec = MediaCodec.createByCodecName(mediaCodecInfo!!.name)

            var videoFormat = MediaFormat.createVideoFormat(VCODEC_MIME, width, height)
            videoFormat.setInteger(MediaFormat.KEY_BIT_RATE, bitrate)
            videoFormat.setInteger(MediaFormat.KEY_FRAME_RATE, frameRate)
            videoFormat.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            videoFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            mediaCodec.configure(videoFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            mediaCodec.start()
            createFile()
        }

    }

    private fun createFile() {
        val path = Environment.getExternalStorageDirectory().absolutePath + "/test.yuv"
        val file = File(path)
        if (file.exists()) {
            file.delete()
        }
        try {
            outputStream = BufferedOutputStream(FileOutputStream(file))
        } catch (e: Exception) {
            e.printStackTrace()
        }

    }

    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val numCodecs = MediaCodecList.getCodecCount()
        for (i in 0 until numCodecs) {
            val codecInfo = MediaCodecList.getCodecInfoAt(i)
            //是否是编码器
            if (!codecInfo.isEncoder) {
                continue
            }
            val types = codecInfo.supportedTypes
            for (type in types) {
                if (mimeType.equals(type, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }


    fun putData(buffer: ByteArray) {
        if (yuv420Queue.size >= 10) {
            yuv420Queue.poll()
        }
        yuv420Queue.add(buffer)
    }


    fun startEncoder() {
        Thread {
            isRuning = true
            var input: ByteArray? = null
            var pts: Long = 0
            var generateIndex: Long = 0

            while (isRuning) {
                if (yuv420Queue.size > 0) {
                    input = yuv420Queue.poll()
                    val yuv420sp = ByteArray(width * height * 3 / 2)
                    nv212nv12(input, yuv420sp, width, height)
                    input = yuv420sp
                }
                if (input != null) {
                    try {
                        val inputBuffers = mediaCodec.inputBuffers
                        val outputBuffers = mediaCodec.outputBuffers

                        val inputBufferIndex = mediaCodec.dequeueInputBuffer(-1)
                        if (inputBufferIndex > 0) {
                            //数据放入到inputBuffer中
                            val inputBuffer = inputBuffers[inputBufferIndex]
                            inputBuffer.clear()
                            inputBuffer.put(input, 0, input.size)
                            //把数据传给编码器并进行编码
                            mediaCodec.queueInputBuffer(
                                inputBufferIndex, 0,
                                input.size,
                                System.nanoTime() / 1000, 0
                            )
                            generateIndex++

                            val bufferInfo = MediaCodec.BufferInfo()
                            //输出buffer出队，返回成功的buffer索引。
                            var outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 11000)
                            while (outputBufferIndex >= 0) {
                                val outputBuffer = outputBuffers[outputBufferIndex]
                                val outData = ByteArray(bufferInfo.size)
                                outputBuffer.get(outData)
                                when {
                                    bufferInfo.flags == MediaCodec.BUFFER_FLAG_CODEC_CONFIG -> {//sps  pps
                                        configByte = outData
                                        println("BUFFER_FLAG_CODEC_CONFIG = ")
                                    }
                                    bufferInfo.flags == MediaCodec.BUFFER_FLAG_KEY_FRAME -> {
                                        println("BUFFER_FLAG_KEY_FRAME = ")
                                        val keyframe = ByteArray(bufferInfo.size + configByte.size)
                                        System.arraycopy(configByte, 0, keyframe, 0, configByte.size)
                                        System.arraycopy(outData, 0, keyframe, configByte.size, outData.size)
                                        outputStream.write(keyframe, 0, keyframe.size)
                                    }
                                    else -> {//视频数据
                                        outputStream.write(outData, 0, outData.size)
                                    }
                                }
                                mediaCodec.releaseOutputBuffer(outputBufferIndex, false)
                                outputBufferIndex = mediaCodec.dequeueOutputBuffer(bufferInfo, 0)
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                } else {
                    Thread.sleep(500)
                }
            }
            // 停止编解码器并释放资源
            try {
                mediaCodec.stop()
                mediaCodec.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }


            // 关闭数据流
            try {
                outputStream.flush()
                outputStream.close()
            } catch (e: IOException) {
                e.printStackTrace()
            }


        }.start()

    }

    fun stop() {
        isRuning = false
    }


    private fun nv212nv12(nv21: ByteArray?, nv12: ByteArray?, width: Int, height: Int) {
        if (nv21 == null || nv12 == null) return
        val framesize = width * height
        var i = 0
        var j = 0
        System.arraycopy(nv21, 0, nv12, 0, framesize)
        i = 0
        while (i < framesize) {
            nv12[i] = nv21[i]
            i++
        }
        j = 0
        while (j < framesize / 2) {
            nv12[framesize + j - 1] = nv21[j + framesize]
            j += 2
        }
        j = 0
        while (j < framesize / 2) {
            nv12[framesize + j] = nv21[j + framesize - 1]
            j += 2
        }
    }


}