package com.eirahoutmoss.nexsense.ar

import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.eirahoutmoss.nexsense.core.model.PlaneType
import com.eirahoutmoss.nexsense.core.model.ScanAlignment
import com.eirahoutmoss.nexsense.core.model.ScanPlane
import com.eirahoutmoss.nexsense.core.model.ScanPoint
import com.eirahoutmoss.nexsense.core.model.TrackPoint
import com.eirahoutmoss.nexsense.core.scene.Vec3
import com.eirahoutmoss.nexsense.core.spatial.FrameMath
import com.eirahoutmoss.nexsense.core.spatial.TrackRecorder
import com.eirahoutmoss.nexsense.core.spatial.VoxelGrid
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.Session
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
import com.google.ar.core.exceptions.NotYetAvailableException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.atan2

/** AR durumu; metni arayüz seçer. */
enum class ArStatus { SEARCHING, TOO_DARK, FEW_FEATURES, TOO_FAST, CAMERA_UNAVAILABLE, GPU_UNSUPPORTED, ERROR }

/** Bitmiş yakalamanın sonucu (proje koordinatında). */
class CaptureResult(val points: List<ScanPoint>, val planes: List<ScanPlane>, val track: List<TrackPoint>, val trackLengthM: Double)

/**
 * ARCore yakalama çizicisi (GL iş parçacığı).
 *
 * - Kamera görüntüsünü arka plana çizer; görüntü kaydedilmez.
 * - İzleme başladığı ilk an telefonun konumu ve baktığı yatay yön tarama çerçevesinin orijini ve +y eksenidir.
 *   Sonra kullanıcının hizalaması ([alignment]: başlangıç noktası, dönüş, yükseklik) uygulanır.
 * - ~2 Hz özellik noktaları, ~1 Hz derinlik örnekleri (cihaz destekliyorsa) ve ~0,5 Hz düzlemler toplanır.
 * - İzleme kaybolduğunda hiçbir şey kaydedilmez ve durum bildirilir.
 * ARCore istisnaları yakalanır; çizici uygulamayı çökertmez.
 */
class ArCapture(
    private val session: Session,
    private val displayRotation: () -> Int,
    private val alignment: ScanAlignment,
    private val useDepth: Boolean,
    private val onStatus: (ArStatus?) -> Unit,
    /** (proje konumu, saniye, izleniyor mu) — her karede değil, ~10 Hz. */
    private val onPose: (Vec3, Double, Boolean) -> Unit,
) : GLSurfaceView.Renderer {

    private val lock = Any()
    private val voxels = VoxelGrid(sizeM = 0.05, maxPoints = 30_000)
    private val track = TrackRecorder(minStepM = 0.1)
    private val planes = LinkedHashMap<Plane, ScanPlane>()
    private val drawPts = FloatArray(MAX_DRAW * 3)
    private var drawCount = 0
    private val marks = ArrayList<FloatArray>()

    @Volatile var capturing = true

    private var origin: Pose? = null
    private var yaw0Deg = 0.0
    private var t0Ns = 0L
    private var lastPoseEmitNs = 0L
    private var lastCloudNs = 0L
    private var lastDepthNs = 0L
    private var lastPlaneNs = 0L

    private var oesTexture = 0
    private var bgProgram = 0
    private var ptProgram = 0
    private var viewW = 1
    private var viewH = 1
    private var lastStatus: ArStatus? = ArStatus.SEARCHING
    private val quadNdc: FloatBuffer = floatBuffer(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f))
    private val quadTex: FloatBuffer = floatBuffer(FloatArray(8))
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val mvp = FloatArray(16)

    val pointCount: Int get() = synchronized(lock) { voxels.size }
    val planeCount: Int get() = synchronized(lock) { planes.size }
    val trackLength: Double get() = synchronized(lock) { track.length }

    fun result(): CaptureResult = synchronized(lock) {
        CaptureResult(voxels.points(), planes.values.toList(), track.points(), track.length)
    }

    /** Kaydedilen ölçüm noktasını sahnede işaretlemek için (AR dünya koordinatı). */
    fun markCurrent() {
        lastWorld?.let { w -> synchronized(lock) { if (marks.size < 2000) marks += w.copyOf() } }
    }

    @Volatile private var lastWorld: FloatArray? = null

    /** AR dünya noktasını proje çerçevesine çevirir (orijin belirlendikten sonra). */
    private fun toProject(x: Float, y: Float, z: Float): Vec3? {
        val o = origin ?: return null
        val rel = FrameMath.arToScan((x - o.tx()).toDouble(), (y - o.ty()).toDouble(), (z - o.tz()).toDouble())
        return FrameMath.align(FrameMath.rotateZ(rel, -yaw0Deg), alignment)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        val tex = IntArray(1)
        GLES20.glGenTextures(1, tex, 0)
        oesTexture = tex[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        session.setCameraTextureName(oesTexture)
        try {
            bgProgram = program(BG_VS, BG_FS)
            ptProgram = program(PT_VS, PT_FS)
        } catch (e: IllegalStateException) {
            bgProgram = 0; ptProgram = 0
            status(ArStatus.GPU_UNSUPPORTED)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        viewW = width; viewH = height
        GLES20.glViewport(0, 0, width, height)
        session.setDisplayGeometry(displayRotation(), width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        if (bgProgram == 0 || ptProgram == 0) return
        val frame = try {
            session.update()
        } catch (e: Exception) {
            status(ArStatus.CAMERA_UNAVAILABLE)
            return
        }
        if (frame.hasDisplayGeometryChanged()) {
            quadNdc.position(0); quadTex.position(0)
            frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES, quadNdc, Coordinates2d.TEXTURE_NORMALIZED, quadTex)
        }
        if (frame.timestamp == 0L) return
        drawBackground()

        val camera = frame.camera
        val now = frame.timestamp
        if (camera.trackingState != TrackingState.TRACKING) {
            status(
                when (camera.trackingFailureReason) {
                    TrackingFailureReason.INSUFFICIENT_LIGHT -> ArStatus.TOO_DARK
                    TrackingFailureReason.INSUFFICIENT_FEATURES -> ArStatus.FEW_FEATURES
                    TrackingFailureReason.EXCESSIVE_MOTION -> ArStatus.TOO_FAST
                    TrackingFailureReason.CAMERA_UNAVAILABLE -> ArStatus.CAMERA_UNAVAILABLE
                    else -> ArStatus.SEARCHING
                },
            )
            if (now - lastPoseEmitNs > POSE_EMIT_NS && origin != null) {
                lastPoseEmitNs = now
                onPose(Vec3.ZERO, (now - t0Ns) / 1e9, false)
            }
            return
        }
        status(null)
        val pose = camera.pose
        if (origin == null) {
            origin = pose
            t0Ns = now
            // Başlangıçta kameranın baktığı yatay yön (ARCore −z) → proje +y
            val z = pose.zAxis
            val f = FrameMath.arToScan(-z[0].toDouble(), -z[1].toDouble(), -z[2].toDouble())
            yaw0Deg = Math.toDegrees(atan2(-f.x, f.y))
        }
        lastWorld = floatArrayOf(pose.tx(), pose.ty(), pose.tz())
        val pProj = toProject(pose.tx(), pose.ty(), pose.tz())!!
        val tS = (now - t0Ns) / 1e9
        if (capturing) synchronized(lock) { track.offer(tS, pProj) }
        if (now - lastPoseEmitNs > POSE_EMIT_NS) {
            lastPoseEmitNs = now
            onPose(pProj, tS, true)
        }
        if (capturing) {
            if (now - lastCloudNs > CLOUD_NS) { lastCloudNs = now; collectCloud(frame) }
            if (useDepth && now - lastDepthNs > DEPTH_NS) { lastDepthNs = now; collectDepth(frame) }
            if (now - lastPlaneNs > PLANE_NS) { lastPlaneNs = now; collectPlanes() }
        }

        camera.getProjectionMatrix(proj, 0, 0.03f, 30f)
        camera.getViewMatrix(view, 0)
        Matrix.multiplyMM(mvp, 0, proj, 0, view, 0)
        drawPoints()
    }

    private fun collectCloud(frame: Frame) {
        try {
            frame.acquirePointCloud().use { cloud ->
                val b = cloud.points
                val n = b.remaining() / 4
                synchronized(lock) {
                    for (i in 0 until n) {
                        val x = b.get(i * 4); val y = b.get(i * 4 + 1); val z = b.get(i * 4 + 2); val c = b.get(i * 4 + 3)
                        if (c < 0.3f) continue
                        val p = toProject(x, y, z) ?: continue
                        if (voxels.add(ScanPoint(r3(p.x), r3(p.y), r3(p.z), r2(c.toDouble()), "f"))) addDraw(x, y, z)
                    }
                }
            }
        } catch (_: Exception) {
            // Nokta bulutu bu karede yok: sonraki denemede alınır.
        }
    }

    /** Derinlik görüntüsünden seyrek örnek (her 10. piksel), 0,3–5 m. */
    private fun collectDepth(frame: Frame) {
        val image = try {
            frame.acquireDepthImage16Bits()
        } catch (_: NotYetAvailableException) {
            return
        } catch (_: Exception) {
            return
        }
        image.use { img ->
            val intr = frame.camera.imageIntrinsics
            val dims = intr.imageDimensions
            val focal = intr.focalLength
            val pp = intr.principalPoint
            val w = img.width; val h = img.height
            if (dims[0] <= 0 || dims[1] <= 0) return
            val sx = w.toFloat() / dims[0]; val sy = h.toFloat() / dims[1]
            val fx = focal[0] * sx; val fy = focal[1] * sy
            val cx = pp[0] * sx; val cy = pp[1] * sy
            val plane = img.planes[0]
            val buf = plane.buffer.order(ByteOrder.nativeOrder())
            val rowStride = plane.rowStride
            val pixStride = plane.pixelStride
            val pose = frame.camera.pose
            val tmp = FloatArray(3)
            synchronized(lock) {
                var v = 0
                while (v < h) {
                    var u = 0
                    while (u < w) {
                        val idx = v * rowStride + u * pixStride
                        if (idx + 1 < buf.limit()) {
                            val mm = buf.getShort(idx).toInt() and 0xFFFF
                            val d = mm / 1000f
                            if (d in 0.3f..5f) {
                                val xc = (u - cx) * d / fx
                                val yc = (v - cy) * d / fy
                                tmp[0] = xc; tmp[1] = -yc; tmp[2] = -d
                                val wpt = pose.transformPoint(tmp)
                                val p = toProject(wpt[0], wpt[1], wpt[2])
                                if (p != null && voxels.add(ScanPoint(r3(p.x), r3(p.y), r3(p.z), 0.6, "d"))) addDraw(wpt[0], wpt[1], wpt[2])
                            }
                        }
                        u += 10
                    }
                    v += 10
                }
            }
        }
    }

    private fun collectPlanes() {
        try {
            val all = session.getAllTrackables(Plane::class.java)
            synchronized(lock) {
                for (pl in all) {
                    if (pl.trackingState != TrackingState.TRACKING || pl.subsumedBy != null) { planes.remove(pl); continue }
                    val poly = pl.polygon
                    val n = poly.remaining() / 2
                    if (n < 3) continue
                    val center = pl.centerPose
                    val verts = ArrayList<List<Double>>(n)
                    for (i in 0 until n) {
                        val w = center.transformPoint(floatArrayOf(poly.get(i * 2), 0f, poly.get(i * 2 + 1)))
                        val p = toProject(w[0], w[1], w[2]) ?: continue
                        verts += listOf(r3(p.x), r3(p.y), r3(p.z))
                    }
                    if (verts.size < 3) continue
                    val type = when (pl.type) {
                        Plane.Type.HORIZONTAL_UPWARD_FACING -> PlaneType.HORIZONTAL_UP
                        Plane.Type.HORIZONTAL_DOWNWARD_FACING -> PlaneType.HORIZONTAL_DOWN
                        else -> PlaneType.VERTICAL
                    }
                    planes[pl] = ScanPlane(type, verts, r2((pl.extentX * pl.extentZ).toDouble()))
                }
            }
        } catch (_: Exception) {
            // Düzlem listesi alınamadı; sonraki denemede.
        }
    }

    private fun addDraw(x: Float, y: Float, z: Float) {
        if (drawCount >= MAX_DRAW) return
        drawPts[drawCount * 3] = x; drawPts[drawCount * 3 + 1] = y; drawPts[drawCount * 3 + 2] = z
        drawCount++
    }

    private fun drawBackground() {
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)
        GLES20.glUseProgram(bgProgram)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture)
        GLES20.glUniform1i(GLES20.glGetUniformLocation(bgProgram, "u_Tex"), 0)
        val aPos = GLES20.glGetAttribLocation(bgProgram, "a_Position")
        val aTex = GLES20.glGetAttribLocation(bgProgram, "a_TexCoord")
        quadNdc.position(0); quadTex.position(0)
        GLES20.glVertexAttribPointer(aPos, 2, GLES20.GL_FLOAT, false, 0, quadNdc)
        GLES20.glVertexAttribPointer(aTex, 2, GLES20.GL_FLOAT, false, 0, quadTex)
        GLES20.glEnableVertexAttribArray(aPos)
        GLES20.glEnableVertexAttribArray(aTex)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisableVertexAttribArray(aTex)
        GLES20.glDepthMask(true)
    }

    private fun drawPoints() {
        val (cloud, cloudN, markArr) = synchronized(lock) { Triple(drawPts.copyOf(drawCount * 3), drawCount, marks.toList()) }
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glUseProgram(ptProgram)
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(ptProgram, "u_Mvp"), 1, false, mvp, 0)
        val aPos = GLES20.glGetAttribLocation(ptProgram, "a_Position")
        val uColor = GLES20.glGetUniformLocation(ptProgram, "u_Color")
        val uSize = GLES20.glGetUniformLocation(ptProgram, "u_Size")
        GLES20.glEnableVertexAttribArray(aPos)
        if (cloudN > 0) {
            val buf = floatBuffer(cloud)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glUniform4f(uColor, 0.20f, 0.82f, 0.96f, 0.75f)
            GLES20.glUniform1f(uSize, viewW / 160f)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, cloudN)
        }
        if (markArr.isNotEmpty()) {
            val m = FloatArray(markArr.size * 3)
            markArr.forEachIndexed { i, p -> m[i * 3] = p[0]; m[i * 3 + 1] = p[1] - 0.15f; m[i * 3 + 2] = p[2] }
            val buf = floatBuffer(m)
            GLES20.glVertexAttribPointer(aPos, 3, GLES20.GL_FLOAT, false, 0, buf)
            GLES20.glUniform4f(uColor, 1.0f, 0.69f, 0.13f, 0.95f)
            GLES20.glUniform1f(uSize, viewW / 30f)
            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, markArr.size)
        }
        GLES20.glDisableVertexAttribArray(aPos)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun status(s: ArStatus?) {
        if (s != lastStatus) { lastStatus = s; onStatus(s) }
    }

    private fun r3(v: Double) = Math.round(v * 1000.0) / 1000.0
    private fun r2(v: Double) = Math.round(v * 100.0) / 100.0

    private companion object {
        const val MAX_DRAW = 8000
        const val POSE_EMIT_NS = 100_000_000L
        const val CLOUD_NS = 500_000_000L
        const val DEPTH_NS = 1_000_000_000L
        const val PLANE_NS = 2_000_000_000L

        const val BG_VS = """
            attribute vec4 a_Position;
            attribute vec2 a_TexCoord;
            varying vec2 v_TexCoord;
            void main() { gl_Position = a_Position; v_TexCoord = a_TexCoord; }
        """
        const val BG_FS = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            uniform samplerExternalOES u_Tex;
            varying vec2 v_TexCoord;
            void main() { gl_FragColor = texture2D(u_Tex, v_TexCoord); }
        """
        const val PT_VS = """
            uniform mat4 u_Mvp;
            uniform float u_Size;
            attribute vec4 a_Position;
            void main() {
                gl_Position = u_Mvp * vec4(a_Position.xyz, 1.0);
                gl_PointSize = clamp(u_Size / max(gl_Position.w, 0.1), 2.0, 64.0);
            }
        """
        const val PT_FS = """
            precision mediump float;
            uniform vec4 u_Color;
            void main() {
                vec2 d = gl_PointCoord - vec2(0.5);
                if (dot(d, d) > 0.25) discard;
                gl_FragColor = u_Color;
            }
        """

        fun floatBuffer(a: FloatArray): FloatBuffer =
            ByteBuffer.allocateDirect(maxOf(4, a.size * 4)).order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(a); position(0) }

        fun shader(type: Int, src: String): Int {
            val s = GLES20.glCreateShader(type)
            GLES20.glShaderSource(s, src.trimIndent())
            GLES20.glCompileShader(s)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(s, GLES20.GL_COMPILE_STATUS, ok, 0)
            if (ok[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(s)
                GLES20.glDeleteShader(s)
                throw IllegalStateException("shader: $log")
            }
            return s
        }

        fun program(vs: String, fs: String): Int {
            val p = GLES20.glCreateProgram()
            GLES20.glAttachShader(p, shader(GLES20.GL_VERTEX_SHADER, vs))
            GLES20.glAttachShader(p, shader(GLES20.GL_FRAGMENT_SHADER, fs))
            GLES20.glLinkProgram(p)
            val ok = IntArray(1)
            GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
            if (ok[0] == 0) throw IllegalStateException("link: ${GLES20.glGetProgramInfoLog(p)}")
            return p
        }
    }
}
