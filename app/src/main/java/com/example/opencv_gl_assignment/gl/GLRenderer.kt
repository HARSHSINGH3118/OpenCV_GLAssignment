// app/src/main/java/com/example/opencv_gl_assignment/gl/GLRenderer.kt
package com.example.opencv_gl_assignment.gl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLRenderer : GLSurfaceView.Renderer {

    @Volatile var pendingFrameRGBA: ByteArray? = null
    @Volatile var frameWidth = 0
    @Volatile var frameHeight = 0

    private var program = 0
    private var aPos = 0
    private var aUV = 0
    private var uTex = 0
    private var texId = 0
    private var textureReady = false

    private val verts = floatArrayOf(
        -1f,-1f, 0f,1f,
        1f,-1f, 1f,1f,
        -1f, 1f, 0f,0f,
        1f, 1f, 1f,0f
    )
    private val vb: FloatBuffer =
        ByteBuffer.allocateDirect(verts.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer().put(verts).apply { position(0) }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        val vs = """
            attribute vec2 aPos; attribute vec2 aUV;
            varying vec2 vUV;
            void main(){ vUV=aUV; gl_Position=vec4(aPos,0.0,1.0); }
        """.trimIndent()
        val fs = """
            precision mediump float;
            varying vec2 vUV; uniform sampler2D uTex;
            void main(){ gl_FragColor = texture2D(uTex, vUV); }
        """.trimIndent()
        program = link(vs, fs)
        aPos = GLES20.glGetAttribLocation(program, "aPos")
        aUV  = GLES20.glGetAttribLocation(program, "aUV")
        uTex = GLES20.glGetUniformLocation(program, "uTex")

        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texId = ids[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        val frame = pendingFrameRGBA
        if (frame != null && frameWidth > 0 && frameHeight > 0) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texId)
            val buf = ByteBuffer.wrap(frame)
            if (!textureReady) {
                GLES20.glTexImage2D(
                    GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    frameWidth, frameHeight, 0,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
                )
                textureReady = true
            } else {
                GLES20.glTexSubImage2D(
                    GLES20.GL_TEXTURE_2D, 0, 0, 0,
                    frameWidth, frameHeight,
                    GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buf
                )
            }
        }

        GLES20.glUseProgram(program)
        vb.position(0)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 16, vb)
        vb.position(2)
        GLES20.glEnableVertexAttribArray(aUV)
        GLES20.glVertexAttribPointer(aUV, 2, GLES20.GL_FLOAT, false, 16, vb)
        GLES20.glUniform1i(uTex, 0)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private fun link(vsSrc: String, fsSrc: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            return id
        }
        val vs = compile(GLES20.GL_VERTEX_SHADER, vsSrc)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fsSrc)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, vs)
        GLES20.glAttachShader(p, fs)
        GLES20.glLinkProgram(p)
        return p
    }
}
