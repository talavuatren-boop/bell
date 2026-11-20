package com.example.processing

import android.graphics.Bitmap
import android.graphics.ColorMatrix
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Hardware EGL14 + OpenGL ES 2.0 Surface renderer for MediaCodec input surfaces.
 * Avoids Surface.lockHardwareCanvas() GPU/HWUI issues and explicitly binds
 * zero-based presentation timestamps via EGLExt.eglPresentationTimeANDROID
 * so exported videos and Live Photo clips never inherit device uptime (e.g. 7 hours).
 */
internal class EglVideoColorSurface private constructor(
    private val eglDisplay: EGLDisplay,
    private val eglContext: EGLContext,
    private val eglSurface: EGLSurface,
    private val surfaceWidth: Int,
    private val surfaceHeight: Int
) {
    private var prog2d = 0
    private var progOes = 0
    private var tex2dId = 0
    private var texOesId = 0

    private val vertexData = floatArrayOf(
        // X, Y, Z, U, V
        -1f, -1f, 0f, 0f, 0f,
         1f, -1f, 0f, 1f, 0f,
        -1f,  1f, 0f, 0f, 1f,
         1f,  1f, 0f, 1f, 1f
    )
    private val vertexBuf: FloatBuffer = ByteBuffer
        .allocateDirect(vertexData.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .put(vertexData)
        .apply { position(0) }

    private val identityTexMatrix = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }
    private val bmpTexMatrix = FloatArray(16).apply {
        // Flip Y for Bitmap -> OpenGL texture coordinates
        Matrix.setIdentityM(this, 0)
        Matrix.scaleM(this, 0, 1f, -1f, 1f)
        Matrix.translateM(this, 0, 0f, -1f, 0f)
    }

    fun makeCurrent() {
        EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
    }

    fun createOesInputTexture(): Int {
        if (texOesId != 0) return texOesId
        makeCurrent()
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        texOesId = ids[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, texOesId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        return texOesId
    }

    fun drawOesFrame(
        oesTexId: Int,
        stMatrix: FloatArray,
        colorMatrix: ColorMatrix?,
        ptsNs: Long
    ) {
        makeCurrent()
        if (progOes == 0) {
            progOes = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_OES)
        }
        drawQuad(
            program = progOes,
            textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
            textureId = oesTexId,
            texMatrix = stMatrix,
            vertexTransformMatrix = identityTexMatrix,
            colorMatrix = colorMatrix,
            ptsNs = ptsNs
        )
    }

    fun drawBitmapFrame(
        bitmap: Bitmap,
        colorMatrix: ColorMatrix?,
        shiftX: Float,
        shiftY: Float,
        cropScale: Float,
        ptsNs: Long
    ) {
        makeCurrent()
        if (prog2d == 0) {
            prog2d = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER_2D)
        }
        if (tex2dId == 0) {
            val ids = IntArray(1)
            GLES20.glGenTextures(1, ids, 0)
            tex2dId = ids[0]
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex2dId)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        } else {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, tex2dId)
        }
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        val vMat = FloatArray(16)
        Matrix.setIdentityM(vMat, 0)
        val normShiftX = if (surfaceWidth > 0) (2f * shiftX / surfaceWidth.toFloat()) else 0f
        val normShiftY = if (surfaceHeight > 0) (-2f * shiftY / surfaceHeight.toFloat()) else 0f
        Matrix.translateM(vMat, 0, normShiftX, normShiftY, 0f)
        Matrix.scaleM(vMat, 0, cropScale, cropScale, 1f)

        drawQuad(
            program = prog2d,
            textureTarget = GLES20.GL_TEXTURE_2D,
            textureId = tex2dId,
            texMatrix = bmpTexMatrix,
            vertexTransformMatrix = vMat,
            colorMatrix = colorMatrix,
            ptsNs = ptsNs
        )
    }

    private fun drawQuad(
        program: Int,
        textureTarget: Int,
        textureId: Int,
        texMatrix: FloatArray,
        vertexTransformMatrix: FloatArray,
        colorMatrix: ColorMatrix?,
        ptsNs: Long
    ) {
        GLES20.glViewport(0, 0, surfaceWidth, surfaceHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        GLES20.glUseProgram(program)
        val aPos = GLES20.glGetAttribLocation(program, "aPosition")
        val aTex = GLES20.glGetAttribLocation(program, "aTextureCoord")
        val uMvp = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val uTex = GLES20.glGetUniformLocation(program, "uSTMatrix")
        val uRowR = GLES20.glGetUniformLocation(program, "uRowR")
        val uRowG = GLES20.glGetUniformLocation(program, "uRowG")
        val uRowB = GLES20.glGetUniformLocation(program, "uRowB")
        val uRowA = GLES20.glGetUniformLocation(program, "uRowA")
        val uOffset = GLES20.glGetUniformLocation(program, "uOffset")

        vertexBuf.position(0)
        GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 20, vertexBuf)
        GLES20.glEnableVertexAttribArray(aPos)

        vertexBuf.position(3)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 20, vertexBuf)
        GLES20.glEnableVertexAttribArray(aTex)

        GLES20.glUniformMatrix4fv(uMvp, 1, false, vertexTransformMatrix, 0)
        GLES20.glUniformMatrix4fv(uTex, 1, false, texMatrix, 0)

        val m = colorMatrix?.array ?: IDENTITY_COLOR_MATRIX
        GLES20.glUniform4f(uRowR, m[0], m[1], m[2], m[3])
        GLES20.glUniform4f(uRowG, m[5], m[6], m[7], m[8])
        GLES20.glUniform4f(uRowB, m[10], m[11], m[12], m[13])
        GLES20.glUniform4f(uRowA, m[15], m[16], m[17], m[18])
        GLES20.glUniform4f(uOffset, m[4] / 255f, m[9] / 255f, m[14] / 255f, m[19] / 255f)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(textureTarget, textureId)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, ptsNs.coerceAtLeast(0L))
        EGL14.eglSwapBuffers(eglDisplay, eglSurface)
    }

    fun release() {
        runCatching { makeCurrent() }
        if (tex2dId != 0) GLES20.glDeleteTextures(1, intArrayOf(tex2dId), 0)
        if (texOesId != 0) GLES20.glDeleteTextures(1, intArrayOf(texOesId), 0)
        if (prog2d != 0) GLES20.glDeleteProgram(prog2d)
        if (progOes != 0) GLES20.glDeleteProgram(progOes)
        runCatching {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        return shader
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
        private val IDENTITY_COLOR_MATRIX = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uMVPMatrix;
            uniform mat4 uSTMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = uMVPMatrix * aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER_2D = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            uniform vec4 uRowR;
            uniform vec4 uRowG;
            uniform vec4 uRowB;
            uniform vec4 uRowA;
            uniform vec4 uOffset;
            void main() {
                vec4 c = texture2D(sTexture, vTextureCoord);
                vec4 outC = vec4(
                    dot(uRowR, c) + uOffset.r,
                    dot(uRowG, c) + uOffset.g,
                    dot(uRowB, c) + uOffset.b,
                    dot(uRowA, c) + uOffset.a
                );
                gl_FragColor = clamp(outC, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER_OES = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform vec4 uRowR;
            uniform vec4 uRowG;
            uniform vec4 uRowB;
            uniform vec4 uRowA;
            uniform vec4 uOffset;
            void main() {
                vec4 c = texture2D(sTexture, vTextureCoord);
                vec4 outC = vec4(
                    dot(uRowR, c) + uOffset.r,
                    dot(uRowG, c) + uOffset.g,
                    dot(uRowB, c) + uOffset.b,
                    dot(uRowA, c) + uOffset.a
                );
                gl_FragColor = clamp(outC, 0.0, 1.0);
            }
        """

        fun create(surface: Surface, width: Int, height: Int): EglVideoColorSurface? {
            return runCatching {
                val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
                if (display == EGL14.EGL_NO_DISPLAY) return null
                val version = IntArray(2)
                if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null

                val attribList = intArrayOf(
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_ALPHA_SIZE, 8,
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL_RECORDABLE_ANDROID, 1,
                    EGL14.EGL_NONE
                )
                val configs = arrayOfNulls<EGLConfig>(1)
                val numConfigs = IntArray(1)
                if (!EGL14.eglChooseConfig(display, attribList, 0, configs, 0, 1, numConfigs, 0) || numConfigs[0] == 0) {
                    EGL14.eglTerminate(display)
                    return null
                }
                val config = configs[0] ?: run {
                    EGL14.eglTerminate(display)
                    return null
                }

                val ctxAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
                val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, ctxAttribs, 0)
                if (context == EGL14.EGL_NO_CONTEXT) {
                    EGL14.eglTerminate(display)
                    return null
                }

                val surfAttribs = intArrayOf(EGL14.EGL_NONE)
                val eglSurface = EGL14.eglCreateWindowSurface(display, config, surface, surfAttribs, 0)
                if (eglSurface == EGL14.EGL_NO_SURFACE) {
                    EGL14.eglDestroyContext(display, context)
                    EGL14.eglTerminate(display)
                    return null
                }

                EGL14.eglMakeCurrent(display, eglSurface, eglSurface, context)
                EglVideoColorSurface(display, context, eglSurface, width, height)
            }.getOrNull()
        }
    }
}
