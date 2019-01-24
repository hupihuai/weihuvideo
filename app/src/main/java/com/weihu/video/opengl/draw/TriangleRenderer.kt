package com.weihu.video.opengl.draw

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import com.weihu.video.MyApp
import com.weihu.video.opengl.DataUtil
import com.weihu.video.opengl.Shader
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * created by hupihuai on 2019/1/24
 */
class TriangleRenderer : GLSurfaceView.Renderer {
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private var esShader = Shader()

    private var vertices = floatArrayOf(
        -0.5f, -0.5f, 0.0f,
        0.5f, -0.5f, 0.0f,
        0.0f, 0.5f, 0.0f
    )

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES30.glViewport(0, 0, width, height)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES30.glClearColor(0.2f, 0.3f, 0.3f, 1.0f)
        //vao
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glBindVertexArray(vao[0])
        //vbo
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        val vertexBuffer = DataUtil.createByteBuffer(vertices)
        //复制数据到opengl
        GLES30.glBufferData(
            GLES30.GL_ARRAY_BUFFER,
            vertices.size * DataUtil.sizeof(GLES30.GL_FLOAT),
            vertexBuffer,
            GLES30.GL_STATIC_DRAW
        )
        //设置定点数组指针
        GLES30.glVertexAttribPointer(0, 3, GLES30.GL_FLOAT, false, 3 * DataUtil.sizeof(GLES30.GL_FLOAT), 0)
        GLES30.glEnableVertexAttribArray(0)
        //vbo end
        //vao end
        //shader
        esShader.loadProgramFromAsset(MyApp.context, "triangle_vert.glsl", "triangle_frag.glsl")
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
        esShader.use()
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
    }
}